#!/usr/bin/env python3
"""Stage only exact recorded canonical Guide pre-Git/post-current owner bytes for semantic proof."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


def sha(blob):return hashlib.sha256(blob).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--project",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();out=a.output.resolve()
    if out==project or project in out.parents:raise ValueError("Fresh external stage required")
    out.mkdir(parents=True,exist_ok=False)
    contract_path=project/"engine-core/src/guideUnionAdmissionTest/source-contract.json";contract=json.loads(contract_path.read_text())
    if sha(Path(__file__).read_bytes())!=contract["stageTool"]["sha256"]:raise ValueError("Repository stage tool pin changed")
    files=[];historical=[]
    for owner in contract["owners"]:
        name=owner["path"];blob_id=subprocess.run(["git","rev-parse",contract["rawSourceCommit"]+":"+name],cwd=project,capture_output=True,check=True).stdout.decode("ascii").strip()
        if blob_id!=contract["rawGitBlobPins"][name]:raise ValueError("Historical exact owner Git blob changed: "+name)
        before=subprocess.run(["git","cat-file","blob",blob_id],cwd=project,capture_output=True,check=True).stdout;after=(project/name).read_bytes()
        if sha(before)!=owner["pre_sha256"] or len(before)!=owner["pre_bytes"]:raise ValueError("Historical canonical preimage changed: "+name)
        if sha(after)!=owner["post_sha256"] or len(after)!=owner["post_bytes"]:raise ValueError("Current actual candidate owner changed: "+name)
        for side,blob in[("pre",before),("post",after)]:
            target=out/side/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob)
        files.append(owner);historical.append({"path":name,"sourceCommit":contract["rawSourceCommit"],"gitBlob":blob_id,"preSha256":sha(before),"postSha256":sha(after)})
    for name,pin in contract["semanticSharedSourcePins"].items():
        if sha((project/name).read_bytes())!=pin:raise ValueError("Real unchanged semantic source pin changed: "+name)
    for name,pin in contract["fixturePins"].items():
        if sha((project/name).read_bytes())!=pin:raise ValueError("Real fixture pin changed: "+name)
    (out/"manifest.json").write_text(json.dumps({"scope":contract["scope"],"files":files,"sourceContractSha256":sha(contract_path.read_bytes()),"historicalSourceCustody":historical,"wholeGuideJava8Acceptance":False},indent=2)+"\n")
    print("PASS repository-owned semantic stage: exact original Git/current target owners="+str(len(files)))


if __name__=="__main__":main()
