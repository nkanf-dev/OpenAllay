#!/usr/bin/env python3
"""Capture existing CommonMark provider/nested bytes on success or failure; no build/JVM/guard change."""
import argparse
import hashlib
import json
from pathlib import Path
from io import BytesIO
import zipfile
EXPECTED = "dff5404332182c794aec52538a9a620b61032041a3b08ddbb972ddf246021a02"
MAX_NESTED_BYTES = 4 * 1024 * 1024

def digest(blob): return hashlib.sha256(blob).hexdigest()
def inventory(blob):
    with zipfile.ZipFile(BytesIO(blob)) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)) or jar.testzip() is not None: raise ValueError("Invalid CommonMark archive")
        entries = {}; texts = {}; majors = {}; count = 0
        for name in names:
            if name.endswith("/"): continue
            data = jar.read(name); row = {"bytes":len(data),"sha256":digest(data)}
            if name.endswith(".class"):
                if data[:4] != b"\xca\xfe\xba\xbe": raise ValueError("Invalid class")
                row["major"] = int.from_bytes(data[6:8], "big"); majors[str(row["major"])] = majors.get(str(row["major"]),0)+1; count += 1
            if name in ("META-INF/MANIFEST.MF", "fabric.mod.json"): texts[name] = data.decode(errors="replace")
            entries[name] = row
        return {"entries":entries,"classCount":count,"classMajors":majors,"metadataText":texts,
            "canonicalClassClosure":count==213 and set(majors)=={"52"} and all(name.startswith("org/commonmark/") for name in entries if name.endswith(".class"))}

def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument("--project",type=Path,required=True)
    p.add_argument("--target",required=True); p.add_argument("--families",required=True); p.add_argument("--output",type=Path,required=True)
    a = p.parse_args(); a.output.mkdir(parents=True,exist_ok=False)
    project=a.project.resolve(); properties={}
    for line in (project/"gradle.properties").read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key,value=line.split("=",1);properties[key.strip()]=value.strip()
    catalog=json.loads((project/"gradle/minecraft-artifacts.json").read_text())
    requested=a.families.split(",")
    families=[family for family in catalog["acceptedFamilies"] if family["id"] in requested]
    if len(families)!=len(requested) or any(family["buildTarget"]!=a.target for family in families):raise ValueError("Unknown capture family selection")
    products={family["loader"]:project/family["loader"]/"build/libs"/family["filenameTemplate"].replace("{version}",properties["version"]) for family in families}
    if set(products)!={"fabric","neoforge"}:raise ValueError("Capture requires exact Fabric+NeoForge selected family")
    provider_path=project/"runtime-commonmark/build/libs"/("openallay-commonmark-"+properties["commonmark_version"]+".jar")
    report = {"scope":"capture-only actualCommonMark provider/nestedbytecustody beforeconsumerdecision","acceptedRawProviderSha256":EXPECTED,"guardChangeAdmitted":False,"inputs":[],"errors":[]}
    provider = None
    if provider_path.is_file():
        blob = provider_path.read_bytes()
        if len(blob)>MAX_NESTED_BYTES: raise ValueError("Provider bound exceeded")
        (a.output / "provider.jar").write_bytes(blob); provider = inventory(blob)
        report["provider"] = {"path":str(provider_path),"bytes":len(blob),"sha256":digest(blob),"matchesAccepted":digest(blob)==EXPECTED,**provider}
    else: report["errors"].append("Missing actual raw provider: " + str(provider_path))
    for loader,path,directory in [("fabric",products["fabric"],"META-INF/jars/"),("neoforge",products["neoforge"],"META-INF/jarjar/")]:
        row = {"loader":loader,"productPath":str(path)}
        if not path.is_file(): row["error"]="Missing actual compiled product"; report["inputs"].append(row); continue
        # Product bytes remain in normal output; only tiny nested library evidence is copied.
        row["productBytes"] = path.stat().st_size
        with path.open("rb") as source:
            state=hashlib.sha256()
            while True:
                chunk=source.read(1024*1024)
                if not chunk:break
                state.update(chunk)
            row["productSha256"]=state.hexdigest()
        with zipfile.ZipFile(path) as outer:
            registration="fabric.mod.json" if loader=="fabric" else "META-INF/jarjar/metadata.json"
            metadata=json.loads(outer.read(registration)) if registration in outer.namelist() else {}
            registered=[item["file"] for item in metadata.get("jars",[])] if loader=="fabric" else [item["path"] for item in metadata.get("jars",[])]
            matches=[]
            for name in registered:
                if not name.startswith(directory) or not name.endswith(".jar") or name not in outer.namelist():continue
                if outer.getinfo(name).file_size>MAX_NESTED_BYTES:continue
                candidate=outer.read(name)
                with zipfile.ZipFile(BytesIO(candidate)) as child:
                    if "org/commonmark/node/Node.class" in child.namelist():matches.append(name)
            row["nestedPaths"]=matches
            if len(matches)!=1: row["error"]="Not exactly one CommonMark nested owner"; report["inputs"].append(row); continue
            if outer.getinfo(matches[0]).file_size>MAX_NESTED_BYTES: raise ValueError("Nested bound exceeded")
            blob=outer.read(matches[0]); (a.output/(loader+"-nested.jar")).write_bytes(blob)
            nested=inventory(blob); row.update({"nestedBytes":len(blob),"nestedSha256":digest(blob),**nested})
            registration="fabric.mod.json" if loader=="fabric" else "META-INF/jarjar/metadata.json"
            row["outerRegistration"] = outer.read(registration).decode() if registration in outer.namelist() else None
            if provider is not None:
                expected=provider["entries"]; actual=nested["entries"]
                row["missingProviderEntries"]=sorted(set(expected)-set(actual));row["addedEntries"]=sorted(set(actual)-set(expected))
                row["changedProviderEntries"]=sorted(name for name in expected if name in actual and expected[name]!=actual[name])
                row["allRuntimeClassBytesEqual"]=not any(name.endswith(".class") for name in row["missingProviderEntries"]+row["changedProviderEntries"])
                row["allProviderResourceBytesEqual"]=not any(not name.endswith(".class") for name in row["missingProviderEntries"]+row["changedProviderEntries"])
        report["inputs"].append(row)
    (a.output/"commonmark-provider-nested-capture.json").write_text(json.dumps(report,indent=2)+"\n")
    print("CommonMark capture saved; this does not admit any guard change")
if __name__=="__main__":main()
