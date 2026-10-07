#!/usr/bin/env python3
"""Exact public compiler text literal lowering into one review-only canonical source packet."""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import subprocess


def sha(blob):return hashlib.sha256(blob).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--project",type=Path,required=True);p.add_argument("--javac",type=Path,required=True);p.add_argument("--java",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();out=a.output.resolve()
    if out==project or project in out.parents:raise ValueError("Fresh externaloutput required")
    out.mkdir(parents=True,exist_ok=False);contract_path=project/"engine-core/src/guideTextBlockLanguageTest/source-contract.json";contract=json.loads(contract_path.read_text());root=project/"engine-core/src/main/java";selected=project/"engine-core/src/guideTextBlockLanguageTest/selected-paths.txt"
    if selected.read_text().splitlines()!=contract["selectedOwners"]:raise ValueError("Selected exactowner listchanged")
    before={name:(root/name).read_bytes()for name in contract["selectedOwners"]}
    if {name:sha(blob)for name,blob in before.items()}!=contract["selectedPreimageSha256"]:raise ValueError("Wholeowner rawsourcechanged")
    for path,pin in contract["toolPins"].items():
        if sha((project/path).read_bytes())!=pin:raise ValueError("Accepted tool/fixturepinchanged")
    classes=out/"tool-classes";classes.mkdir();commands=[]
    def run(command):
        commands.append([str(x)for x in command]);(out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with(out/("command-%02d.log"%len(commands))).open("wb")as log:subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True)
    run([a.javac,"--release","17","-encoding","UTF-8","-d",classes,project/"build-logic/src/main/java/dev/openallay/build/CanonicalTextBlockPort.java"])
    run([a.java,"-cp",classes,"dev.openallay.build.CanonicalTextBlockPort",root,selected,out/"materialized"])
    counts={name:int(count)for name,count in(line.split("\t")for line in(out/"materialized/literal-counts.tsv").read_text().splitlines())}
    if set(counts)!=set(before):raise ValueError("Actual literalownerclosurechanged")
    packet=out/"source-packet";packet.mkdir();patch="";rows=[]
    for name in sorted(before):
        old=(out/"materialized/pre"/name).read_bytes();new=(out/"materialized/post"/name).read_bytes()
        if old!=before[name]:raise ValueError("Raw materializer sourcechanged")
        path="engine-core/src/main/java/"+name
        for side,blob in[("pre",old),("post",new)]:
            target=packet/side/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(blob)
        patch+="".join(difflib.unified_diff(old.decode().splitlines(True),new.decode().splitlines(True),fromfile="a/"+path,tofile="b/"+path))
        rows.append({"path":path,"pre_sha256":sha(old),"pre_bytes":len(old),"post_sha256":sha(new),"post_bytes":len(new),"actualTextBlockCount":counts[name]})
    if before!={name:(root/name).read_bytes()for name in before}:raise ValueError("Source drift")
    (packet/"source.patch").write_bytes(patch.encode());(packet/"manifest.json").write_text(json.dumps({"scope":contract["scope"],"files":rows,"patch_sha256":sha(patch.encode()),"patch_bytes":len(patch.encode()),"sourceContractSha256":sha(contract_path.read_bytes()),"acceptedToolOracleRun":contract["acceptedToolOracleRun"],"fixtureReexecuted":False,"wholeRuntimeJava8Acceptance":False},indent=2)+"\n")
    print("Review-only exact publicliteral Guidepacket: "+str(packet))


if __name__=="__main__":main()
