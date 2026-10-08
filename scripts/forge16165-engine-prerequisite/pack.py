#!/usr/bin/env python3
"""Deterministic Forge36 fat-mod closure scanner/packer. Python 3.11+, stdlib only."""
import argparse
import hashlib
import json
import os
import re
import stat
import struct
import sys
import tempfile
import tomllib
import unicodedata
import zipfile
from pathlib import Path

PROBE = "dev/openallay/forge36probe/"
FORBIDDEN = ("com/google/gson/", "com/google/common/", "com/google/thirdparty/",
    "org/slf4j/", "org/apache/logging/", "net/minecraft/", "net/minecraftforge/",
    "com/mojang/", "net/neoforged/", "net/fabricmc/", "org/lwjgl/",
    "org/spongepowered/", "org/apache/maven/", "org/eclipse/aether/",
    "cpw/mods/", "com/llamalad7/", "dev/architectury/", "mezz/jei/", "me/shedaniel/")
MAX_ARCHIVE = 256 * 1024 * 1024
MAX_ENTRY = 64 * 1024 * 1024
MAX_EXPANDED = 512 * 1024 * 1024
MAX_ENTRIES = 100000
MR = re.compile(r"META-INF/versions/([1-9][0-9]*)/(.+)")
SIGNATURE = re.compile(r"META-INF/(?:[^/]+\.(?:SF|RSA|DSA|EC)|SIG-[^/]+)", re.I)
LEGAL = re.compile(r"(?:LICENSE|LICENCE|NOTICE|COPYING|COPYRIGHT|DEPENDENCIES)(?:[._-].*)?", re.I)
IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*")

class GateError(ValueError):
    pass

def require(ok, message):
    if not ok:
        raise GateError(message)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def file_sha(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()

def exact(value, fields, label):
    require(isinstance(value, dict) and set(value) == set(fields), "Exact shape: " + label)

def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, "Duplicate JSON key: " + key)
        result[key] = value
    return result

def json_load(path):
    require(Path(path).stat().st_size <= 32 * 1024 * 1024, "JSON size bound: " + str(path))
    return json.loads(Path(path).read_text(encoding="utf-8"), object_pairs_hook=pairs,
        parse_constant=lambda value: (_ for _ in ()).throw(GateError("JSON constant: " + value)))

def encoded(value):
    return (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8")

def new_output(path):
    require(not Path(path).exists() and not Path(path).is_symlink(), "Output already exists: " + str(path))
    require(Path(path).parent.is_dir(), "Output parent must exist: " + str(path))

def write_new(path, data):
    with Path(path).open("xb") as target:
        target.write(data)

def safe_name(name, directory=False):
    require(isinstance(name, str) and name and len(name.encode("utf-8")) <= 1024, "Path length")
    require(name == unicodedata.normalize("NFC", name), "Noncanonical Unicode path: " + name)
    require(not any(ord(c) < 32 or ord(c) == 127 for c in name), "Control byte in path")
    require(not any(c in name for c in "\\:"), "Nonportable path: " + name)
    base = name[:-1] if directory else name
    require(base and not base.startswith("/") and all(p not in ("", ".", "..") for p in base.split("/")), "Unsafe path: " + name)
    require(not name.endswith("/") or directory, "Directory/file mismatch: " + name)
    return name

def logical(name):
    m = MR.fullmatch(name)
    if name.startswith("META-INF/versions/"):
        require(m is not None and int(m[1]) >= 9, "Invalid MR path: " + name)
    return m[2] if m else name

def class_info(data, name):
    require(len(data) >= 10 and data[:4] == b"\xca\xfe\xba\xbe", "Invalid class header: " + name)
    minor, major, count = struct.unpack_from(">HHH", data, 4)
    require(minor == 0 and 45 <= major <= 61 and count > 1, "Unsupported class version: " + name)
    pool = {}; i = 10; index = 1
    try:
        while index < count:
            tag = data[i]; i += 1
            if tag == 1:
                length = struct.unpack_from(">H", data, i)[0]; i += 2
                require(i + length <= len(data), "Truncated UTF constant: " + name)
                pool[index] = data[i:i+length]; i += length
            elif tag in (7,):
                pool[index] = struct.unpack_from(">H", data, i)[0]; i += 2
            elif tag in (8, 16, 19, 20): i += 2
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18): i += 4
            elif tag in (5, 6): i += 8; index += 1
            elif tag == 15: i += 3
            else: raise GateError("Unknown constant tag: " + name)
            require(i <= len(data), "Truncated class constant pool: " + name)
            index += 1
        this_class = struct.unpack_from(">H", data, i + 2)[0]
        binary = pool[pool[this_class]].decode("ascii")
        require(binary + ".class" == logical(name), "Class path/internal name mismatch: " + name)
    except (IndexError, KeyError, struct.error, UnicodeError) as error:
        raise GateError("Invalid class structure: " + name) from error
    return major

def manifest(data):
    text = data.decode("utf-8").replace("\r\n", "\n")
    lines = []
    for line in text.split("\n"):
        if line.startswith(" "):
            require(bool(lines), "Invalid manifest continuation")
            lines[-1] += line[1:]
        else: lines.append(line)
    attrs = {}
    for line in lines:
        if not line: break
        require(": " in line, "Invalid manifest main attribute")
        key, value = line.split(": ", 1); key = key.lower()
        require(key not in attrs, "Duplicate manifest attribute: " + key)
        attrs[key] = value
    return attrs

def archive(artifact):
    exact(artifact, ("role", "coordinate", "path", "sha256"), "artifact")
    path = Path(artifact["path"])
    require(isinstance(artifact["sha256"], str) and re.fullmatch(r"[0-9a-f]{64}", artifact["sha256"]), "Invalid archive hash")
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= MAX_ARCHIVE, "Archive path/size: " + str(path))
    require(file_sha(path) == artifact["sha256"], "Archive SHA mismatch: " + str(path))
    entries = {}; total = 0
    with zipfile.ZipFile(path) as z:
        infos = z.infolist(); require(len(infos) <= MAX_ENTRIES, "Entry count bound")
        for item in infos:
            name = safe_name(item.orig_filename, item.is_dir())
            require(name == item.filename and name not in entries, "Duplicate/truncated ZIP entry: " + name)
            mode = item.external_attr >> 16
            entry_type = stat.S_IFMT(mode)
            # Java ZIP producers can mark an empty slash directory as a regular file.
            # The canonical path and empty payload establish directory semantics.
            # Older Maven JARs encode an unset Unix mode as 0xffff plus DOS directory.
            legacy_empty_directory = (item.external_attr == 0xffff0010 and item.is_dir()
                and item.file_size == 0 and item.compress_size == 0)
            require(entry_type in (0, stat.S_IFREG, stat.S_IFDIR) or legacy_empty_directory, "ZIP symlink/special entry: " + name)
            require(item.is_dir() or (entry_type != stat.S_IFDIR and not item.external_attr & 0x10), "ZIP directory attributes on file: " + name)
            require(not item.flag_bits & 1, "Encrypted ZIP entry: " + name)
            require(item.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), "ZIP compression type: " + name)
            require(item.file_size <= MAX_ENTRY and (item.file_size <= max(item.compress_size, 1) * 1000), "ZIP entry bound: " + name)
            total += item.file_size; require(total <= MAX_EXPANDED, "ZIP expanded size bound")
            data = z.read(item); require(len(data) == item.file_size, "ZIP size mismatch: " + name)
            require(not item.is_dir() or not data, "Nonempty ZIP directory: " + name)
            entries[name] = data
    for name in entries:
        parts = name.rstrip("/").split("/")
        require(not any("/".join(parts[:i]) in entries for i in range(1, len(parts))), "File/directory path conflict: " + name)
        require(not (name.endswith("/") and name[:-1] in entries), "File/directory duplicate: " + name)
    require(file_sha(path) == artifact["sha256"], "Archive changed while reading: " + str(path))
    return entries

def service(data, name):
    require(IDENTIFIER.fullmatch(name.removeprefix("META-INF/services/")), "Invalid service name: " + name)
    result = []
    for line in data.decode("utf-8").splitlines():
        value = line.split("#", 1)[0].strip()
        if value:
            require(IDENTIFIER.fullmatch(value), "Invalid service provider: " + name)
            if value not in result: result.append(value)
    return result

def builder_descriptor(data):
    d = json.loads(data.decode("utf-8"), object_pairs_hook=pairs)
    require(set(d) in ({"schemaVersion","id","name","version","provider","summary","source","entrypoint","support"},
        {"schemaVersion","id","name","version","provider","summary","source","entrypoint","support","requirements"}), "Builder descriptor fields")
    require(type(d["schemaVersion"]) is int and d["schemaVersion"] == 2, "Universal external Builder descriptor")
    require(d["id"] == "openallay:builder" and d["version"] == "0.4.0" and d["entrypoint"] == "dev.openallay.builder.BuilderExtension", "Builder descriptor identity")
    for field in ("name", "provider", "summary", "source"):
        require(isinstance(d[field], str) and d[field].strip(), "Builder descriptor: " + field)
    exact(d["support"], ("targets","minimumJavaVersion","requiredHostFeatures","validatedTargetIds"), "Builder support")
    require(type(d["support"]["minimumJavaVersion"]) is int and 8 <= d["support"]["minimumJavaVersion"] <= 17, "Builder Java requirement")
    require(isinstance(d["support"]["targets"], list) and d["support"]["targets"], "Builder targets")
    for target in d["support"]["targets"]:
        exact(target, ("loader","minecraftVersionRange","openAllayVersionRange","openAllayApiVersionRange"), "Builder target")
        require(all(isinstance(v,str) and v.strip() for v in target.values()), "Builder target values")
    for key in ("requiredHostFeatures", "validatedTargetIds"):
        v=d["support"][key]; require(isinstance(v,list) and all(isinstance(s,str) and s.strip() for s in v) and len(v)==len(set(v)), "Builder support list")
    return {"entrypoint":d["entrypoint"], "id":d["id"], "version":d["version"], "sha256":sha(data)}

def disposition(role, coordinate, name, data, artifact_hash, policy, attrs):
    if name.endswith("/"): return "omit-directory", None, None
    path = logical(name)
    major = class_info(data, name) if name.endswith(".class") else None
    if role in policy["hostRoles"]:
        require(coordinate == policy["hostRoles"][role], "Host coordinate mismatch: " + role)
        return "host-supplied-not-packed", None, major
    if path == "module-info.class": return "omit-classpath-module-descriptor", None, major
    if name.startswith("META-INF/versions/"):
        mr = policy["sqliteMrPolicy"]
        require(role == "sqlite" and artifact_hash == mr["archiveSha256"] and
            mr["entries"].get(name) == sha(data) and major == mr["major"] and attrs.get("multi-release", "").lower() == "true",
            "STOP functional/unreviewed MR entry: " + role + ":" + name + " sha256=" + sha(data))
    if major is not None:
        require(not path.startswith(FORBIDDEN), "Host/native/raw Maven replacement class: " + name)
        info = (policy["roles"] | policy["annotationRoles"])[role]
        require(path.startswith(tuple(info["classPrefixes"])), "Unowned class namespace: " + role + ":" + name)
        if role in ("sdk","builder"): require(major == 52, "SDK/Builder class must be major52: " + name)
        if role in ("engine","rhino","json-proof") and path not in policy["exactEngineMavenClasses"]: require(major == 61, "Engine/Rhino/JSON class must be major61: " + name)
        if role == "engine":
            allowed = policy["primaryClasses"]["engine"]
            primary = path[:-6].split("$",1)[0]
            require(primary in allowed or path in policy["exactEngineMavenClasses"], "Unknown engine class: " + name)
        if role in ("sdk","rhino"):
            require(path[:-6].split("$",1)[0] in policy["primaryClasses"][role], "Unknown SDK/Rhino primary: " + name)
        if role == "maven-proof": require(path in policy["exactEngineMavenClasses"], "Unknown private Maven class")
    if role.endswith("-proof"):
        if name == "META-INF/MANIFEST.MF": return "omit-proof-manifest", None, major
        if SIGNATURE.fullmatch(name): return "omit-invalidated-signature", None, major
        return "embedded-engine-proof", name, major
    if LEGAL.fullmatch(name.split("/")[-1]):
        return "copy-legal-unique", "META-INF/licenses/closure/" + role + "/" + name, major
    if name == "META-INF/MANIFEST.MF": return "omit-dependency-manifest", None, major
    if SIGNATURE.fullmatch(name): return "omit-invalidated-signature", None, major
    if name == "META-INF/INDEX.LIST": return "omit-stale-jar-index", None, major
    if re.fullmatch(r"META-INF/maven/.+/pom\.(?:xml|properties)", name): return "omit-build-maven-metadata", None, major
    require(not name.startswith(("META-INF/jarjar/", "META-INF/coremods/")) and name not in
        ("META-INF/mods.toml", "fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/accesstransformer.cfg", "META-INF/coremods.json"), "Unexpected loader metadata: " + name)
    require(not name.endswith((".jar", ".zip")), "Nested archive: " + name)
    if name.startswith("META-INF/services/"):
        service(data,name)
        return "union-service", name, major
    return "copy-unchanged", name, major

def scan(spec_path, policy_path):
    policy = json_load(policy_path); spec = json_load(spec_path)
    exact(spec, ("sourceRevision","artifacts","resolution"), "closure input")
    source_revision = spec["sourceRevision"]
    require(isinstance(source_revision, str) and re.fullmatch(r"[0-9a-f]{40}", source_revision),
            "Exact current producer source revision required")
    require(isinstance(policy["sourceRevision"], str) and re.fullmatch(r"[0-9a-f]{40}", policy["sourceRevision"]),
            "Exact retained ownership source baseline required")
    exact(spec["resolution"], ("path","sha256"), "resolution")
    require(file_sha(spec["resolution"]["path"]) == spec["resolution"]["sha256"], "Resolution evidence hash")
    resolution = json_load(spec["resolution"]["path"])
    exact(resolution, ("sourceRevision","runtimeCoordinates"), "resolution evidence")
    require(resolution["sourceRevision"] == source_revision, "Resolution source mismatch")
    require(isinstance(resolution["runtimeCoordinates"],list) and all(isinstance(c,str) for c in resolution["runtimeCoordinates"]), "Resolution coordinates")
    require(len(resolution["runtimeCoordinates"])==len(set(resolution["runtimeCoordinates"])), "Duplicate resolution coordinate")
    artifacts=spec["artifacts"]; require(isinstance(artifacts,list), "Artifacts list")
    roles=[a.get("role") for a in artifacts]
    require(len(roles)==len(set(roles)), "Duplicate artifact role")
    require(set(policy["roles"]) <= set(roles), "Missing required artifacts/proofs: " + str(set(policy["roles"])-set(roles)))
    approved=policy["roles"] | policy["annotationRoles"]
    require(set(roles) <= set(approved) | set(policy["hostRoles"]), "Unknown artifact role")
    require(set(resolution["runtimeCoordinates"]) == {a["coordinate"] for a in artifacts if not a["role"].endswith("-proof")}, "Actual resolved closure differs from provided archives")
    require(set(policy["hostRoles"]) <= set(roles), "Missing host-supplied resolution artifacts")
    inventory=[]; errors=[]; outputs={}; services={}; contents={}; expanded=0; mr=False; builder=None
    for artifact in sorted(artifacts,key=lambda a:a["role"]):
        role=artifact["role"]
        expected=approved[role]["coordinate"] if role in approved else policy["hostRoles"][role]
        require(artifact["coordinate"]==expected, "Coordinate mismatch: " + role)
        entries=archive(artifact); contents[role]=entries
        expanded += sum(map(len,entries.values())); require(expanded <= MAX_EXPANDED, "Whole closure expanded bound")
        attrs=manifest(entries["META-INF/MANIFEST.MF"]) if "META-INF/MANIFEST.MF" in entries else {}
        for name,data in sorted(entries.items()):
            record={"role":role,"archiveSha256":artifact["sha256"],"name":name,"bytes":len(data),"sha256":sha(data),"major":None,"disposition":None,"target":None}
            if name.endswith(".class") and len(data)>=8 and data[:4]==b"\xca\xfe\xba\xbe":
                record["major"]=int.from_bytes(data[6:8],"big")
            try:
                action,target,major=disposition(role,artifact["coordinate"],name,data,artifact["sha256"],policy,attrs)
                record.update(disposition=action,target=target,major=major)
                if action=="union-service":
                    services.setdefault(target,[]).append((record,data))
                elif action.startswith("copy-"):
                    require(target not in outputs and target not in services, "Competing sole ownership: " + target)
                    outputs[target]=(data,[record])
                    mr |= target.startswith("META-INF/versions/")
            except (GateError, UnicodeError) as error:
                record["disposition"]="STOP"; record["error"]=str(error);errors.append(str(error))
            inventory.append(record)
        for name in policy["requiredEntries"].get(role,[]):
            if name not in entries: errors.append("Missing required entry: " + role + ":" + name)
        for primary in policy["primaryClasses"].get(role,[]):
            if primary+".class" not in entries: errors.append("Missing primary: " + role + ":" + primary)
        if role=="engine" and set(n for n in entries if n.startswith("dev/openallay/internal/maven/") and n.endswith(".class")) != set(policy["exactEngineMavenClasses"]):
            errors.append("Private Maven exact class closure differs")
        if role=="json-proof" and set(n for n in entries if n.endswith(".class")) != set(policy["runtimeJsonClasses"]):
            errors.append("Runtime JSON proof exact class closure differs")
        if role=="maven-proof" and set(n for n in entries if n.endswith(".class")) != set(policy["exactEngineMavenClasses"]):
            errors.append("Private Maven proof exact class closure differs")
        if role=="builder":
            try: builder=builder_descriptor(entries["META-INF/openallay-extension.json"])
            except (GateError, KeyError, UnicodeError, json.JSONDecodeError) as error: errors.append("Builder descriptor: " + str(error))
    for role in ("json-proof","maven-proof"):
        for record in inventory:
            if record["role"]==role and record["disposition"]=="embedded-engine-proof":
                name=record["name"]
                if contents["engine"].get(name)!=contents[role][name]:errors.append("Embedded proof byte mismatch: " + role + ":" + name)
    for name, members in sorted(services.items()):
        if name in outputs: errors.append("Service/resource collision: " + name);continue
        providers=[]
        for record,data in members:
            for provider in service(data,name):
                if provider not in providers: providers.append(provider)
        # Owner order is deterministic (role, entry); provider order within each owner is retained.
        data=("\n".join(providers)+("\n" if providers else "")).encode("utf-8")
        outputs[name]=(data,[record for record,_ in members])
        for provider in providers:
            provider_path=provider.replace(".","/")+".class"
            if provider_path not in outputs:
                errors.append("Service provider has no packed sole owner: " + name + ":" + provider)
    report={"status":"STOP" if errors else "READY", "sourceRevision":source_revision,
        "ownershipSourceBaseline":policy["sourceRevision"], "specSha256":file_sha(spec_path),"policySha256":file_sha(policy_path),"packerSha256":file_sha(__file__),
        "resolutionSha256":spec["resolution"]["sha256"],"artifacts":sorted(artifacts,key=lambda a:a["role"]),
        "inventory":inventory,"errors":errors,"multiRelease":mr,"builderDescriptor":builder,
        "plannedEntries":[{"name":name,"sha256":sha(data),"bytes":len(data),"owners":owners}
            for name,(data,owners) in sorted(outputs.items())]}
    return report,outputs

def probe_check(artifact):
    entries=archive(artifact)
    require(artifact["role"] in ("probe-input","probe-reobf"), "Probe role")
    require(artifact["coordinate"]=="dev.openallay:forge36-engine-probe:0.4.3", "Probe coordinate")
    for name,data in entries.items():
        if name.endswith("/"):
            require(PROBE.startswith(name) or name.startswith(PROBE) or name=="META-INF/", "Probe foreign directory: " + name)
            continue
        require(name.startswith(PROBE) or name in ("META-INF/MANIFEST.MF","META-INF/mods.toml"), "Probe foreign entry: " + name)
        if name.endswith(".class"):
            require(name.startswith(PROBE) and class_info(data,name)==61, "Probe class ownership/major")
        else: require(name.startswith("META-INF/"), "Unexpected probe resource: " + name)
    require(any(n.endswith(".class") for n in entries), "Empty probe")
    attrs=manifest(entries["META-INF/MANIFEST.MF"])
    require(attrs.get("fmlmodtype")=="MOD" and attrs.get("multi-release","false").lower()=="false", "Probe manifest")
    require(not (set(attrs) & {"class-path","main-class","premain-class","agent-class","fmlcoreplugin","fmlat"}), "Probe special loading metadata")
    toml=tomllib.loads(entries["META-INF/mods.toml"].decode("utf-8"))
    require(toml.get("modLoader")=="javafml" and toml.get("loaderVersion")=="[36,)" and len(toml.get("mods",[]))==1, "Probe normal mods.toml")
    require(toml["mods"][0].get("modId")=="openallay_engine_probe" and toml["mods"][0].get("version")=="0.4.3", "Probe mod identity")
    require(not set(toml)-{"modLoader","loaderVersion","license","mods","dependencies"}, "Probe extra TOML features")
    dependencies=toml.get("dependencies",{})
    require(set(dependencies)=={"openallay_engine_probe"}, "Probe dependency owner")
    deps=dependencies["openallay_engine_probe"]
    require(isinstance(deps,list) and len(deps)==2, "Probe exact host dependencies")
    require({d.get("modId") for d in deps}=={"forge","minecraft"}, "Probe dependency IDs")
    for dependency in deps:
        exact(dependency,("modId","mandatory","versionRange","ordering","side"),"probe host dependency")
        require(dependency["mandatory"] is True and dependency["ordering"]=="NONE" and dependency["side"]=="CLIENT", "Probe dependency shape")
        require(dependency["versionRange"]=={"forge":"[36.2.42]","minecraft":"[1.16.5]"}[dependency["modId"]], "Probe exact host version")
    return {n:b for n,b in entries.items() if not n.endswith("/")}

def pack(spec, policy, gate, gate_hash, probes_path, output, receipt):
    new_output(output); new_output(receipt)
    require(file_sha(gate)==gate_hash, "Scan gate hash mismatch")
    accepted=json_load(gate); actual,entries=scan(spec,policy)
    require(actual==accepted and actual["status"]=="READY", "Scan gate changed or STOP")
    probes=json_load(probes_path);exact(probes,("input","reobf"),"probe pair")
    before=probe_check(probes["input"]); after=probe_check(probes["reobf"])
    require(probes["input"]["role"]=="probe-input" and probes["reobf"]["role"]=="probe-reobf", "Probe pair roles")
    require(set(before)==set(after), "Reobfuscator changed probe entry set")
    for name in before:
        if not name.endswith(".class"):require(before[name]==after[name], "Reobfuscator changed probe metadata: " + name)
    for name,data in after.items():
        if name=="META-INF/MANIFEST.MF":continue
        require(name not in entries, "Probe/closure competing ownership: " + name)
        entries[name]=(data,[{"role":"probe-reobf","archiveSha256":probes["reobf"]["sha256"],"name":name,"sha256":sha(data),"disposition":"copy-unchanged"}])
    data=("Manifest-Version: 1.0\r\nFMLModType: MOD\r\nImplementation-Version: 0.4.3\r\n"+
        ("Multi-Release: true\r\n" if actual["multiRelease"] else "")+"\r\n").encode("ascii")
    entries["META-INF/MANIFEST.MF"]=(data,[{"role":"packer","disposition":"generate-fat-mod-manifest","sha256":sha(data),"inputManifestSha256":sha(after["META-INF/MANIFEST.MF"])}])
    fd,tmp=tempfile.mkstemp(prefix=".fat-mod-",suffix=".jar",dir=Path(output).parent);os.close(fd)
    try:
        with zipfile.ZipFile(tmp,"w",compression=zipfile.ZIP_STORED,allowZip64=False) as target:
            for name,(data,_) in sorted(entries.items()):
                safe_name(name);item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.create_system=3;item.external_attr=0o100644<<16
                target.writestr(item,data)
        final=archive({"role":"final","coordinate":"final","path":tmp,"sha256":file_sha(tmp)})
        require(set(final)==set(entries), "Final entry coverage")
        for name,(data,_) in entries.items():require(final[name]==data, "Final byte mismatch: " + name)
        for artifact in actual["artifacts"]+[probes["input"],probes["reobf"]]:
            require(file_sha(artifact["path"])==artifact["sha256"], "Input changed during packing")
        require(file_sha(spec)==actual["specSha256"] and file_sha(policy)==actual["policySha256"] and file_sha(gate)==gate_hash, "Control input changed during packing")
        result={"status":"PACKED","sourceRevision":actual["sourceRevision"],"outputSha256":file_sha(tmp),
            "scanSha256":gate_hash,"probePairSha256":file_sha(probes_path),"probeInput":probes["input"],"probeReobf":probes["reobf"],
            "closure":actual,"entries":[{"name":n,"bytes":len(b),"sha256":sha(b),"major":class_info(b,n) if n.endswith(".class") else None,"owners":o} for n,(b,o) in sorted(entries.items())]}
        # A failure never leaves an apparently accepted final file or overwrites an old artifact.
        os.link(tmp,output)
        try: write_new(receipt,encoded(result))
        except BaseException:Path(output).unlink();raise
    finally:Path(tmp).unlink(missing_ok=True)
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest="command",required=True)
    scan_parser=sub.add_parser("scan",help="inventory exact closure and write READY/STOP gate; no probe/game required")
    pack_parser=sub.add_parser("pack",help="pack only a hash-bound READY gate and separate probe-only reobf pair")
    for command in (scan_parser,pack_parser):
        command.add_argument("--spec",required=True);command.add_argument("--policy",default=str(Path(__file__).with_name("ownership.json")))
    scan_parser.add_argument("--report",required=True)
    for option in ("gate","gate-sha256","probes","output","receipt"):pack_parser.add_argument("--"+option,required=True)
    args=parser.parse_args()
    try:
        if args.command=="scan":
            new_output(args.report);report,_=scan(args.spec,args.policy);write_new(args.report,encoded(report))
            print(report["status"]+" reportSha256="+file_sha(args.report));return 0 if report["status"]=="READY" else 2
        result=pack(args.spec,args.policy,args.gate,args.gate_sha256,args.probes,args.output,args.receipt)
        print("PACKED outputSha256="+result["outputSha256"]);return 0
    except (GateError,OSError,KeyError,TypeError,ValueError,zipfile.BadZipFile) as error:
        print("STOP: "+str(error),file=sys.stderr);return 2

if __name__=="__main__":sys.exit(main())
