#!/usr/bin/env python3
"""Self-contained custody regressions; optional real-capture probe is an explicit CLI mode."""
import importlib.util
import json
from pathlib import Path
from io import BytesIO
import tempfile
import unittest
import zipfile
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("cm_custody",ROOT/"scripts/verify-commonmark-provider-custody.py")
helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)

def archive(entries):
    out=BytesIO()
    with zipfile.ZipFile(out,"w") as dest:
        for name,blob in entries.items():dest.writestr(name,blob)
    return out.getvalue()

class CustodyTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.original_policy_path=helper.POLICY_PATH;self.original_digest=helper.PROVIDER_SHA256
        self.addCleanup(setattr,helper,"POLICY_PATH",self.original_policy_path)
        self.addCleanup(setattr,helper,"PROVIDER_SHA256",self.original_digest)
        self.provider={"org/commonmark/node/Node.class":b"class-payload",
            "org/commonmark/internal/util/entities.txt":b"entity-payload",
            "META-INF/MANIFEST.MF":b"Manifest-Version: 1.0\r\n\r\n",
            "META-INF/openallay/commonmark-source-changes/commonmark-java8.patch":b"source-provenance"}
        self.metadata={"schemaVersion":1,"id":"dev_openallay_runtime-commonmark","version":"0.28.0",
            "name":"runtime-commonmark","custom":{"fabric-loom:generated":True}}
        self.raw=archive(self.provider)
        self.fabric=dict(self.provider);self.fabric["fabric.mod.json"]=json.dumps(self.metadata).encode()
        # Inject only the checked artifact policy, not an alternate verification implementation.
        # This finite payload is not claimed to be CommonMark bytecode or the 222-entry provider.
        helper.PROVIDER_SHA256=helper.sha256(self.raw).hexdigest()
        policy={"providerSha256":helper.PROVIDER_SHA256,"providerVersion":"0.28.0",
            "providerEntryCount":len(self.provider),"providerEntries":{name:{"bytes":len(blob),"sha256":helper.sha256(blob).hexdigest()}
                for name,blob in self.provider.items()},"fabricWrapper":self.metadata}
        helper.POLICY_PATH=Path(self.temporary.name)/"policy.json";helper.POLICY_PATH.write_text(json.dumps(policy))
    def test_raw_and_exact_fabric_metadata_role(self):
        helper.verify_commonmark_payload(self.raw,"neoforge","0.28.0")
        helper.verify_commonmark_payload(archive(self.fabric),"fabric","0.28.0")
    def test_class_and_resource_mutations_fail(self):
        for name in self.provider:
            changed=dict(self.fabric);changed[name]=b"changed"
            with self.assertRaises(ValueError):helper.verify_commonmark_payload(archive(changed),"fabric","0.28.0")
    def test_unknown_entry_and_wrapper_behavior_fail(self):
        changed=dict(self.fabric);changed["other.json"]=b"{}"
        with self.assertRaises(ValueError):helper.verify_commonmark_payload(archive(changed),"fabric","0.28.0")
        for key,value in [("entrypoints",{"main":["evil"]}),("depends",{"minecraft":"*"}),("mixins",["evil.json"])]:
            metadata=dict(self.metadata);metadata[key]=value;changed=dict(self.fabric);changed["fabric.mod.json"]=json.dumps(metadata).encode()
            with self.assertRaises(ValueError):helper.verify_commonmark_payload(archive(changed),"fabric","0.28.0")
        changed=dict(self.fabric);changed["fabric.mod.json"]=b'{"schemaVersion":1,"schemaVersion":1}'
        with self.assertRaises(ValueError):helper.verify_commonmark_payload(archive(changed),"fabric","0.28.0")
    def test_fml_requires_entire_original_jar_bytes(self):
        with self.assertRaises(ValueError):helper.verify_commonmark_payload(archive(self.fabric),"neoforge","0.28.0")

def real_capture_probe(directory):
    # No mocked policy/digest in this mode: use normal checked-in 222-entry artifact custody.
    directory=Path(directory)
    helper.verify_commonmark_payload((directory/"neoforge-nested.jar").read_bytes(),"neoforge","0.28.0")
    original=(directory/"fabric-nested.jar").read_bytes()
    helper.verify_commonmark_payload(original,"fabric","0.28.0")
    with zipfile.ZipFile(BytesIO(original)) as source:entries={name:source.read(name) for name in source.namelist() if not name.endswith("/")}
    for name in ["org/commonmark/node/Node.class","org/commonmark/internal/util/entities.txt","META-INF/MANIFEST.MF",
                 "META-INF/openallay/commonmark-source-changes/commonmark-java8.patch"]:
        changed=dict(entries);changed[name]=b"changed"
        try:helper.verify_commonmark_payload(archive(changed),"fabric","0.28.0")
        except ValueError:pass
        else:raise AssertionError("Actual provider mutation accepted: "+name)
    print("PASS actual CommonMark captured provider/Fabric/FML custody probe")

if __name__=="__main__":
    import sys
    if len(sys.argv)==3 and sys.argv[1]=="--capture":real_capture_probe(sys.argv[2])
    else:unittest.main()
