#!/usr/bin/env python3
"""Build-only public compiler accepted record converter for the complete current client and settings domains."""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import subprocess


def sha(blob):return hashlib.sha256(blob).hexdigest()


def parse_format_edits(encoded):
    import base64
    text=base64.b64decode(encoded,validate=True).decode()
    edits=[]
    for line in text.splitlines():
        number,before,after=line.split("\t")
        edits.append({"line":int(number),"before":base64.b64decode(before,validate=True).decode(),"after":base64.b64decode(after,validate=True).decode()})
    return edits


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--project",type=Path,required=True);p.add_argument("--javac",type=Path,required=True);p.add_argument("--java",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();out=a.output.resolve()
    if out==project or project in out.parents:raise ValueError("Fresh externaloutput only")
    out.mkdir(parents=True,exist_ok=False);contract_path=project/"engine-core/src/clientSettingsRecordLanguageTest/source-contract.json";contract=json.loads(contract_path.read_text())
    for label in ["converter","tool","request"]:
        pin=contract[label]
        if sha((project/pin["path"]).read_bytes())!=pin["sha256"]:raise ValueError("Pinned buildartifact changed: "+label)
    request=project/contract["request"]["path"];root=project/"engine-core/src/main/java"
    if len(contract["owners"])!=contract["requestedOwnerCount"] or sum(len(r["recordPaths"]) for r in contract["owners"])!=contract["parsedRecordCount"]:raise ValueError("ActualpublicAST request shape changed")
    for row in contract["owners"]:
        if sha((root/row["path"]).read_bytes())!=row["raw_sha256"]:raise ValueError("Current complete client/settings owner changed: "+row["path"])
    classes=out/"tool-classes";classes.mkdir();commands=[]
    def run(command):
        commands.append([str(x) for x in command]);(out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with (out/("command-%02d.log"%len(commands))).open("wb") as log:subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True)
    run([a.javac,"--release","17","-encoding","UTF-8","-d",classes,project/contract["converter"]["path"],project/contract["tool"]["path"]])
    run([a.java,"-cp",classes,"dev.openallay.build.CanonicalRecordBatchMaterializer",root,request,out/"materialized"])
    status=[];patch="";rows=[];packet=out/"source-packet";packet.mkdir()
    for line in (out/"materialized/owner-status.tsv").read_text().splitlines():
        fields=line.split("\t");name=fields[0];state=fields[1]
        if state=="REJECTED":
            import base64
            status.append({"path":name,"state":state,"records":int(fields[2]),"reason":base64.b64decode(fields[4]).decode()});continue
        if state!="SUPPORTED" or len(fields)!=9:raise ValueError("Malformed converter classification")
        old=(out/"materialized/pre"/name).read_bytes();new=(out/"materialized/post"/name).read_bytes();logical="engine-core/src/main/java/"+name
        if sha(new)!=fields[6] or len(new)!=int(fields[7]):raise ValueError("Materializedpostimage custody differs")
        for side,b in [("pre",old),("post",new)]:
            dest=packet/side/logical;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
        patch+="".join(difflib.unified_diff(old.decode().splitlines(True),new.decode().splitlines(True),fromfile="a/"+logical,tofile="b/"+logical))
        row={"path":logical,"pre_sha256":sha(old),"pre_bytes":len(old),"post_sha256":sha(new),"post_bytes":len(new),"recordCount":int(fields[2]),"converterRawPostSha256":fields[4],"converterRawPostBytes":int(fields[5]),"exactNonblankFormatEdits":parse_format_edits(fields[8])}
        rows.append(row);status.append({"path":name,"state":state,"records":int(fields[2])})
    if len(status)!=contract["requestedOwnerCount"]:raise ValueError("Missing complete client/settings owner classification")
    by_name={row["path"]:row for row in contract["owners"]}
    for classification in status:
        requested=by_name[classification["path"]]
        if classification["records"]!=len(requested["recordPaths"]):raise ValueError("Whole-owner record count differs")
    rejected={row["path"] for row in status if row["state"]=="REJECTED"}
    if rejected!=set(contract["expectedUnsupportedOwners"]):raise ValueError("Actual unsupported owner set differs: "+str(rejected))
    if sum(row["recordCount"] for row in rows)!=contract["expectedSupportedRecordCount"]:raise ValueError("Actual complete supported record count differs")
    (packet/"source.patch").write_bytes(patch.encode());(packet/"manifest.json").write_text(json.dumps({"scope":contract["scope"],"files":rows,"patch_sha256":sha(patch.encode()),"patch_bytes":len(patch.encode()),"sourceContractSha256":sha(contract_path.read_bytes()),"convertedRecordCount":sum(r["recordCount"] for r in rows),"requestedRecordCount":contract["parsedRecordCount"],"owners":status,"unchangedRejectedOwners":[r for r in contract["owners"] if r["path"] in rejected],"wholeRuntimeJava8Acceptance":False,"requiredAcceptance":"Actual completeengine modern compilation/tests after canonicalpatch review; no runtime8claim"},indent=2)+"\n")
    print("Review-only acceptedconverter Client/settings packet: "+str(packet))


if __name__=="__main__":main()
