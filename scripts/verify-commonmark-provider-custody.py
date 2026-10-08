#!/usr/bin/env python3
"""Exact accepted provider-entry custody with one known Fabric-generated library metadata role."""
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import zipfile
POLICY_PATH = Path(__file__).with_name("commonmark-provider-entry-policy.json")
PROVIDER_SHA256 = "dff5404332182c794aec52538a9a620b61032041a3b08ddbb972ddf246021a02"
def verify_commonmark_payload(content, loader, version):
    policy=json.loads(POLICY_PATH.read_text())
    if policy["providerSha256"] != PROVIDER_SHA256 or version != policy["providerVersion"]:raise ValueError("CommonMark provider policy differs")
    if loader != "fabric" and sha256(content).hexdigest() != PROVIDER_SHA256:raise ValueError("FML CommonMark is not the exact accepted raw provider")
    with zipfile.ZipFile(BytesIO(content)) as jar:
        names=jar.namelist()
        if len(names)!=len(set(names)) or jar.testzip() is not None:raise ValueError("Corrupt CommonMark archive")
        actual={name for name in names if not name.endswith("/")}; expected=set(policy["providerEntries"])
        allowed=expected | ({"fabric.mod.json"} if loader=="fabric" else set())
        if actual!=allowed:raise ValueError("CommonMark provider/runtime resource entry set differs")
        for name,identity in policy["providerEntries"].items():
            data=jar.read(name)
            if len(data)!=identity["bytes"] or sha256(data).hexdigest()!=identity["sha256"]:raise ValueError("CommonMark provider entry changed: "+name)
        if loader=="fabric":
            raw=jar.read("fabric.mod.json")
            if len(raw)>4096:raise ValueError("Oversized CommonMark Fabric wrapper metadata")
            def unique(pairs):
                result={}
                for key,value in pairs:
                    if key in result:raise ValueError("Duplicate Fabric wrapper metadata key")
                    result[key]=value
                return result
            metadata=json.loads(raw,object_pairs_hook=unique)
            if metadata!=policy["fabricWrapper"]:raise ValueError("CommonMark Fabric wrapper metadata differs")
            if type(metadata["schemaVersion"]) is not int or type(metadata["custom"]["fabric-loom:generated"]) is not bool:raise ValueError("CommonMark Fabric wrapper field types differ")
    return {"providerSha256":PROVIDER_SHA256,"runtimeEntries":policy["providerEntryCount"],"loaderMetadataRole":"fabric-generated-library" if loader=="fabric" else None}
