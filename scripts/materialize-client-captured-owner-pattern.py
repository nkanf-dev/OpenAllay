#!/usr/bin/env python3
"""Review-only actual complete client/settings pattern owners with public compiler flow proof."""
import argparse
import base64
import difflib
import hashlib
import json
from pathlib import Path
import subprocess


def sha(blob):return hashlib.sha256(blob).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--project",type=Path,required=True);p.add_argument("--classpath-metadata",type=Path,required=True);p.add_argument("--javac",type=Path,required=True);p.add_argument("--java",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();out=a.output.resolve()
    if out==project or project in out.parents:raise ValueError("Fresh externaloutput required")
    out.mkdir(parents=True,exist_ok=False);contract_path=project/"engine-core/src/clientCapturedOwnerPatternLanguageTest/source-contract.json";contract=json.loads(contract_path.read_text());root=project/"engine-core/src/main/java";selected_file=project/"engine-core/src/clientCapturedOwnerPatternLanguageTest/selected-paths.txt"
    if selected_file.read_text().splitlines()!=contract["selectedOwners"]:raise ValueError("Exactselectedowner request changed")
    for name,pin in contract["toolPins"].items():
        if sha((project/name).read_bytes())!=pin:raise ValueError("Acceptedpublicpattern tool/fixture changed")
    if len(contract["selectedOwners"]) != contract["selectedOwnerCount"] or len(set(contract["selectedOwners"])) != contract["selectedOwnerCount"]:raise ValueError("Completeclient/settings owner request shape changed")
    if any(not name.startswith(("dev/openallay/client/", "dev/openallay/settings/")) for name in contract["selectedOwners"]):raise ValueError("Foreignowner boundary")
    original={name:(root/name).read_bytes() for name in contract["selectedOwners"]}
    if {name:sha(blob) for name,blob in original.items()}!=contract["selectedPreimageSha256"]:raise ValueError("Actual completeclient/settings preimage changed")
    metadata=json.loads(a.classpath_metadata.read_text());paths=sorted(str(p.resolve()) for p in root.rglob("*.java"))
    if Path(metadata["sourceRoot"]).resolve()!=root or metadata["sources"]!=paths or metadata["release"]!=17 or metadata["producer"]!=":engine-core:compileJava":raise ValueError("Genuinecompleteproduction source/classpath metadata required")
    classes=out/"tool-classes";classes.mkdir();commands=[]
    def run(command):
        commands.append([str(x) for x in command]);(out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with (out/("command-%02d.log"%len(commands))).open("wb") as log:subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True)
    run([a.javac,"--release","17","-encoding","UTF-8","-d",classes,project/"build-logic/src/main/java/dev/openallay/build/AttributedVarTypes.java",project/"build-logic/src/main/java/dev/openallay/build/CanonicalPatternPort.java",project/"scripts/pattern-language-port/CanonicalPatternPortFixture.java"])
    # Exact tool and fixture hashes above bind the accepted compiler oracle recorded in the contract.
    run([a.java,"-cp",classes,"dev.openallay.build.CanonicalPatternPort",root,metadata["classpath"],selected_file,out/"materialized"])
    rows=[];statuses=[];patch="";packet=out/"source-packet";packet.mkdir()
    for line in (out/"materialized/owner-status.tsv").read_text().splitlines():
        name,status,count,encoded=line.split("\t");count=int(count)
        if name not in original:raise ValueError("Unexpectedowner")
        if status=="REJECTED":statuses.append({"path":name,"state":status,"parsedPatterns":count,"reason":base64.b64decode(encoded,validate=True).decode()});continue
        if status!="SUPPORTED":raise ValueError("Unrecognizedpattern status")
        a_blob=(out/"materialized/pre"/name).read_bytes();b_blob=(out/"materialized/post"/name).read_bytes()
        if a_blob!=original[name]:raise ValueError("Rawpreimage changed")
        statuses.append({"path":name,"state":status,"parsedPatterns":count})
        if a_blob==b_blob:continue
        logical="engine-core/src/main/java/"+name
        for side,blob in [("pre",a_blob),("post",b_blob)]:
            dest=packet/side/logical;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(blob)
        patch+="".join(difflib.unified_diff(a_blob.decode().splitlines(True),b_blob.decode().splitlines(True),fromfile="a/"+logical,tofile="b/"+logical))
        rows.append({"path":logical,"pre_sha256":sha(a_blob),"pre_bytes":len(a_blob),"post_sha256":sha(b_blob),"post_bytes":len(b_blob),"parsedPatterns":count})
    if len(statuses)!=len(original) or {r["path"] for r in statuses}!=set(original):raise ValueError("Incompletepattern ownerreport")
    if statuses != [{"path":contract["selectedOwners"][0],"state":"SUPPORTED","parsedPatterns":1}]:raise ValueError("Exact admitted single wildcard owner classification differs: "+str(statuses))
    if original!={name:(root/name).read_bytes() for name in original}:raise ValueError("Canonical sourcechanged during generation")
    (packet/"source.patch").write_bytes(patch.encode());(packet/"manifest.json").write_text(json.dumps({"scope":contract["scope"],"files":rows,"patch_sha256":sha(patch.encode()),"patch_bytes":len(patch.encode()),"owners":statuses,"selectedOwnerCount":len(original),"convertedPatternCount":sum(r["parsedPatterns"] for r in rows),"sourceContractSha256":sha(contract_path.read_bytes()),"acceptedToolOracleRun":contract["acceptedToolOracleRun"],"toolOracleReexecuted":False,"wholeRuntimeJava8Acceptance":False,"requiredAcceptance":"Root exactraw/generatedscope review then completecurrent moderncompile/tests; rejectedowners unchanged"},indent=2)+"\n")
    print("Review-only publicpattern Client/settings packet: "+str(packet))


if __name__=="__main__":main()
