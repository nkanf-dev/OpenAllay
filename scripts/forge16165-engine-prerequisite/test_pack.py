#!/usr/bin/env python3
"""Small synthetic ZIP boundary checks. No Java compiler or game invocation."""
import copy
import importlib.util
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

spec=importlib.util.spec_from_file_location("pack",Path(__file__).with_name("pack.py"))
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)

def clazz(name,major=61):
    data=name.encode("ascii")
    return b"\xca\xfe\xba\xbe"+struct.pack(">HHH",0,major,3)+b"\x01"+struct.pack(">H",len(data))+data+b"\x07\x00\x01"+struct.pack(">HHHHHHH",0x21,2,0,0,0,0,0)

class Boundaries(unittest.TestCase):
    def setUp(self):
        # Tests own only this small disposable directory, beside their source packet.
        self.tmp=tempfile.TemporaryDirectory(prefix="mock-archives-",dir=Path(__file__).parent)
        self.root=Path(self.tmp.name)
        self.policy=json.loads(Path(__file__).with_name("ownership.json").read_text())
    def tearDown(self):self.tmp.cleanup()
    def jar(self,entries,role="engine"):
        path=self.root/(role+".jar")
        with zipfile.ZipFile(path,"w") as z:
            for name,data in entries:z.writestr(name,data)
        return {"role":role,"coordinate":"mock","path":str(path),"sha256":p.file_sha(path)}
    def test_zip_unsafe_duplicate_symlink_and_bounds(self):
        for name in ("../escape","/absolute","a\\b","a//b","x:device","./a"):
            with self.subTest(name=name):
                with self.assertRaises(p.GateError):p.archive(self.jar([(name,b"x")]))
        nul=self.jar([("aXevil",b"x")])
        raw=Path(nul["path"]).read_bytes().replace(b"aXevil",b"a\x00evil")
        Path(nul["path"]).write_bytes(raw);nul["sha256"]=p.file_sha(nul["path"])
        with self.assertRaises(p.GateError):p.archive(nul)
        with self.assertRaises(p.GateError):p.archive(self.jar([("x",b"a"),("x",b"b")]))
        artifact=self.jar([])
        with zipfile.ZipFile(artifact["path"],"w") as z:
            item=zipfile.ZipInfo("link");item.create_system=3;item.external_attr=0o120777<<16;z.writestr(item,b"target")
        artifact["sha256"]=p.file_sha(artifact["path"])
        with self.assertRaises(p.GateError):p.archive(artifact)
        with self.assertRaises(p.GateError):p.archive(self.jar([("x",b"x"),("x/y",b"y")]))
        saved=p.MAX_ENTRY;p.MAX_ENTRY=2
        try:
            with self.assertRaises(p.GateError):p.archive(self.jar([("big",b"123")]))
        finally:p.MAX_ENTRY=saved
    def test_hash_and_internal_class_name(self):
        artifact=self.jar([("x",b"a")]);artifact["sha256"]="0"*64
        with self.assertRaises(p.GateError):p.archive(artifact)
        self.assertEqual(p.class_info(clazz("dev/openallay/Test"),"dev/openallay/Test.class"),61)
        with self.assertRaises(p.GateError):p.class_info(clazz("dev/openallay/Test"),"dev/openallay/Other.class")
        with self.assertRaises(p.GateError):p.class_info(clazz("dev/openallay/Test",62),"dev/openallay/Test.class")
    def test_mr_hash_policy_and_host_guards(self):
        name="META-INF/versions/9/org/sqlite/nativeimage/Feature.class";blob=clazz("org/sqlite/nativeimage/Feature",53)
        policy=copy.deepcopy(self.policy)
        policy["sqliteMrPolicy"]={"archiveSha256":"a"*64,"entries":{name:p.sha(blob)},"major":53}
        action,target,major=p.disposition("sqlite","org.xerial:sqlite-jdbc:3.50.3.0",name,blob,"a"*64,policy,{"multi-release":"true"})
        self.assertEqual((action,target,major),("copy-unchanged",name,53))
        with self.assertRaises(p.GateError):p.disposition("sqlite","mock",name,blob,"b"*64,policy,{"multi-release":"true"})
        module="META-INF/versions/9/module-info.class"
        self.assertEqual(p.disposition("sqlite","mock",module,clazz("module-info",53),"b"*64,policy,{})[0],"omit-classpath-module-descriptor")
        forbidden="META-INF/versions/9/com/google/gson/Gson.class"
        with self.assertRaises(p.GateError):p.disposition("engine","mock",forbidden,clazz("com/google/gson/Gson",53),"x",policy,{})
    def test_probe_rejects_foreign_features(self):
        manifest=b"Manifest-Version: 1.0\r\nFMLModType: MOD\r\n\r\n"
        toml=b'modLoader="javafml"\nloaderVersion="[36,)"\nlicense="MIT"\n[[mods]]\nmodId="openallay_engine_probe"\nversion="0.4.3"\n[[dependencies.openallay_engine_probe]]\nmodId="forge"\nmandatory=true\nversionRange="[36.2.42]"\nordering="NONE"\nside="CLIENT"\n[[dependencies.openallay_engine_probe]]\nmodId="minecraft"\nmandatory=true\nversionRange="[1.16.5]"\nordering="NONE"\nside="CLIENT"\n'
        entries=[("dev/openallay/forge36probe/Probe.class",clazz("dev/openallay/forge36probe/Probe")),("META-INF/MANIFEST.MF",manifest),("META-INF/mods.toml",toml)]
        artifact=self.jar(entries,"probe-input");artifact["coordinate"]="dev.openallay:forge36-engine-probe:0.4.3"
        self.assertEqual(len(p.probe_check(artifact)),3)
        for name in ("META-INF/coremods.json","dev/openallay/FeatureServices.class","assets/unrelated.txt"):
            artifact=self.jar(entries+[(name,b"{}")] ,"probe-input");artifact["coordinate"]="dev.openallay:forge36-engine-probe:0.4.3"
            with self.assertRaises(p.GateError):p.probe_check(artifact)
    def test_full_scan_pack_services_legal_and_embedded_proof(self):
        policy=copy.deepcopy(self.policy)
        classes={"engine":"dev/openallay/Test","sdk":"dev/openallay/api/extension/Test","rhino":"dev/latvian/mods/rhino/Test","builder":"dev/openallay/builder/BuilderExtension","commonmark":"org/commonmark/Test","tables":"org/commonmark/ext/gfm/tables/Test","jtokkit":"com/knuddels/jtokkit/Test","sqlite":"org/sqlite/JDBC","json-proof":"dev/openallay/json/JsonTrees","maven-proof":"dev/openallay/internal/maven/Test"}
        policy["primaryClasses"]={"engine":[classes["engine"],classes["json-proof"]],"sdk":[classes["sdk"]],"rhino":[classes["rhino"]]}
        mr_name="META-INF/versions/9/org/sqlite/nativeimage/Feature.class";mr_blob=clazz("org/sqlite/nativeimage/Feature",53)
        policy["runtimeJsonClasses"]=[classes["json-proof"]+".class"]
        policy["requiredEntries"]={};policy["exactEngineMavenClasses"]=[classes["maven-proof"]+".class"]
        desc={"schemaVersion":2,"id":"openallay:builder","version":"0.4.0","entrypoint":"dev.openallay.builder.BuilderExtension","name":"Builder","provider":"OpenAllay","summary":"Build","source":"source","support":{"targets":[{"loader":"forge","minecraftVersionRange":"[1.16.5]","openAllayVersionRange":"[0.4.3]","openAllayApiVersionRange":"[0.4.0]"}],"minimumJavaVersion":8,"requiredHostFeatures":[],"validatedTargetIds":[]}}
        artifacts=[]
        for role,info in policy["roles"].items():
            major=52 if role in ("sdk","builder","maven-proof") else 61
            entries=[(classes[role]+".class",clazz(classes[role],major)),("META-INF/MANIFEST.MF",b"Manifest-Version: 1.0\r\n\r\n")]
            if role=="engine":entries += [(classes["json-proof"]+".class",clazz(classes["json-proof"])),(classes["maven-proof"]+".class",clazz(classes["maven-proof"],52))]
            if role=="builder":entries += [("META-INF/openallay-extension.json",p.encoded(desc))]
            if role in ("commonmark","tables"):entries += [("META-INF/LICENSE",b"legal-"+role.encode()),("META-INF/services/java.sql.Driver",b"org.sqlite.JDBC # retained provider\n")]
            if role=="sqlite":
                entries=[(n,b) for n,b in entries if n!="META-INF/MANIFEST.MF"]
                entries += [("org/sqlite/native/Linux/libsqlite.so",b"native-resource"),(mr_name,mr_blob),("META-INF/MANIFEST.MF",b"Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n")]
            artifact=self.jar(entries,role);artifact["coordinate"]=info["coordinate"];artifacts.append(artifact)
        for role,coordinate in policy["hostRoles"].items():
            artifact=self.jar([("host.txt",b"host-supplied")],role);artifact["coordinate"]=coordinate;artifacts.append(artifact)
        sqlite=next(a for a in artifacts if a["role"]=="sqlite")
        policy["sqliteMrPolicy"]={"archiveSha256":sqlite["sha256"],"entries":{mr_name:p.sha(mr_blob)},"major":53}
        policy_path=self.root/"policy.json";policy_path.write_bytes(p.encoded(policy))
        resolution=self.root/"resolution.json";resolution.write_bytes(p.encoded({"sourceRevision":p.REVISION,"runtimeCoordinates":[a["coordinate"] for a in artifacts if not a["role"].endswith("-proof")]}))
        spec_path=self.root/"spec.json";spec_path.write_bytes(p.encoded({"sourceRevision":p.REVISION,"artifacts":artifacts,"resolution":{"path":str(resolution),"sha256":p.file_sha(resolution)}}))
        gate,outputs=p.scan(spec_path,policy_path)
        self.assertEqual(gate["status"],"READY",gate["errors"])
        self.assertEqual(len(outputs["META-INF/services/java.sql.Driver"][1]),2)
        self.assertEqual(outputs["META-INF/services/java.sql.Driver"][0],b"org.sqlite.JDBC\n")
        self.assertIn("META-INF/licenses/closure/tables/META-INF/LICENSE",outputs)
        gate_path=self.root/"gate.json";gate_path.write_bytes(p.encoded(gate))
        pe=[("dev/openallay/forge36probe/Probe.class",clazz("dev/openallay/forge36probe/Probe")),("META-INF/MANIFEST.MF",b"Manifest-Version: 1.0\r\nFMLModType: MOD\r\n\r\n"),("META-INF/mods.toml",b'modLoader="javafml"\nloaderVersion="[36,)"\nlicense="MIT"\n[[mods]]\nmodId="openallay_engine_probe"\nversion="0.4.3"\n[[dependencies.openallay_engine_probe]]\nmodId="forge"\nmandatory=true\nversionRange="[36.2.42]"\nordering="NONE"\nside="CLIENT"\n[[dependencies.openallay_engine_probe]]\nmodId="minecraft"\nmandatory=true\nversionRange="[1.16.5]"\nordering="NONE"\nside="CLIENT"\n')]
        probes={key:self.jar(pe,role) for key,role in (("input","probe-input"),("reobf","probe-reobf"))}
        for a in probes.values():a["coordinate"]="dev.openallay:forge36-engine-probe:0.4.3"
        probes_path=self.root/"probes.json";probes_path.write_bytes(p.encoded(probes))
        first=p.pack(spec_path,policy_path,gate_path,p.file_sha(gate_path),probes_path,self.root/"first.jar",self.root/"first-receipt.json")
        second=p.pack(spec_path,policy_path,gate_path,p.file_sha(gate_path),probes_path,self.root/"second.jar",self.root/"second-receipt.json")
        self.assertEqual(first["outputSha256"],second["outputSha256"])
        with zipfile.ZipFile(self.root/"first.jar") as z:
            self.assertEqual(z.read(mr_name),mr_blob)
            self.assertIn(b"Multi-Release: true",z.read("META-INF/MANIFEST.MF"))
            self.assertEqual(z.read("org/sqlite/native/Linux/libsqlite.so"),b"native-resource")
        with self.assertRaises(p.GateError):p.pack(spec_path,policy_path,gate_path,"0"*64,probes_path,self.root/"third.jar",self.root/"third-receipt.json")
        proof=next(a for a in artifacts if a["role"]=="json-proof")
        original=Path(proof["path"]).read_bytes()
        with zipfile.ZipFile(proof["path"],"w") as z:z.writestr(classes["json-proof"]+".class",clazz(classes["json-proof"])+b"different-byte")
        proof["sha256"]=p.file_sha(proof["path"])
        spec_path.write_bytes(p.encoded({"sourceRevision":p.REVISION,"artifacts":artifacts,"resolution":{"path":str(resolution),"sha256":p.file_sha(resolution)}}))
        stopped,_=p.scan(spec_path,policy_path);self.assertTrue(any("Embedded proof byte mismatch" in s for s in stopped["errors"]))
        Path(proof["path"]).write_bytes(original);proof["sha256"]=p.file_sha(proof["path"])
        # Collisions cannot become READY even for byte-identical resources.
        artifact=next(a for a in artifacts if a["role"]=="tables")
        with zipfile.ZipFile(artifact["path"],"a") as z:z.writestr("org/sqlite/native/Linux/libsqlite.so",b"native-resource")
        artifact["sha256"]=p.file_sha(artifact["path"]);spec_path.write_bytes(p.encoded({"sourceRevision":p.REVISION,"artifacts":artifacts,"resolution":{"path":str(resolution),"sha256":p.file_sha(resolution)}}))
        stopped,_=p.scan(spec_path,policy_path);self.assertEqual(stopped["status"],"STOP")
        self.assertTrue(any("Competing sole ownership" in s for s in stopped["errors"]))

if __name__=="__main__":unittest.main(verbosity=2)
