#!/usr/bin/env python3
"""Compose review-only retained-skill canonical source packet. Run remotely with installed modern JDK."""
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import subprocess


def sha(blob): return hashlib.sha256(blob).hexdigest()


def normalize_format(name, blob):
    # Preserve every source line terminator and every nonblank byte except one
    # explicitly recorded converter-retained compact-constructor line suffix.
    exact_owner="dev/openallay/skill/SkillDocument.java"
    exact_line=" chunks = dev.openallay.util.Java8Collections.listCopyOf(chunks); "
    output=[]; edits=[]
    for index,line in enumerate(blob.decode("utf-8").splitlines(True),1):
        if line.endswith("\r\n"): body=line[:-2]; ending="\r\n"
        elif line.endswith("\n") or line.endswith("\r"): body=line[:-1]; ending=line[-1]
        else: body=line; ending=""
        after=body
        if body and all(character in " \t" for character in body):
            after=""
        elif name==exact_owner and body==exact_line:
            after=exact_line[:-1]
        if after!=body: edits.append({"line":index,"before":body,"after":after})
        output.append(after+ending)
    return "".join(output).encode("utf-8"),edits


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--project",type=Path,required=True)
    p.add_argument("--recipe",type=Path,required=True)
    p.add_argument("--java",type=Path,required=True)
    p.add_argument("--javac",type=Path,required=True)
    p.add_argument("--output",type=Path,required=True)
    p.add_argument("--prepare",action="store_true",required=True,help="Replay recorded historical raw Git owners; never read mutable original overlays")
    a=p.parse_args(); project=a.project.resolve(); output=a.output.resolve()
    if output==project or project in output.parents: raise ValueError("External fresh output required")
    output.mkdir(parents=True,exist_ok=False)
    recipe=json.loads(a.recipe.read_text())
    converter=project/recipe["converter"]
    if sha(converter.read_bytes())!=recipe["converterSha256"]: raise ValueError("Accepted converter source changed")
    contract=json.loads((project/"engine-core/src/retainedSkillJava8Test/source-contract.json").read_text())
    before={}; working={}; historical=[]
    for name,wanted in recipe["rawPreimageSha256"].items():
        logical="engine-core/src/main/java/"+name
        target=contract["normalizedCanonicalTargets"][logical]
        current=(project/logical).read_bytes()
        if sha(current)!=target["sha256"] or len(current)!=target["bytes"]: raise ValueError("Current normalized production owner changed: "+name)
        spec=recipe["acceptedSourceCommit"]+":"+logical
        resolve=subprocess.run(["git","rev-parse",spec],cwd=project,capture_output=True,check=True)
        git_blob=resolve.stdout.decode("ascii").strip()
        if git_blob!=recipe["rawPreimageGitBlob"][name]: raise ValueError("Historical Git owner blob differs: "+name)
        blob=subprocess.run(["git","cat-file","blob",git_blob],cwd=project,capture_output=True,check=True).stdout
        if sha(blob)!=wanted: raise ValueError("Historical raw owner SHA256 differs: "+name)
        historical.append({"path":logical,"sourceCommit":recipe["acceptedSourceCommit"],"gitBlob":git_blob,"rawSha256":sha(blob),"rawBytes":len(blob)})
        before[name]=blob; text=blob.decode("utf-8")
        for edit in sorted(recipe["apiEdits"].get(name,[]),key=lambda item:len(item["before"]),reverse=True):
            if text.count(edit["before"])!=edit["occurrences"]: raise ValueError("Exact API preimage changed: "+name)
            text=text.replace(edit["before"],edit["after"])
        blob=text.encode("utf-8")
        if sha(blob)!=recipe["apiPostimageSha256"][name]: raise ValueError("API lowered postimage mismatch: "+name)
        target=output/"api-post"/name; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(blob)
        working[name]=target
    tools=output/"tool-classes"; tools.mkdir()
    commands=[]
    def run(command):
        commands.append([str(x) for x in command])
        (output/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with (output/("command-%02d.log"%len(commands))).open("wb") as log:
            subprocess.run(commands[-1],stdout=log,stderr=subprocess.STDOUT,check=True,cwd=project)
    run([a.javac,"--release","17","-encoding","UTF-8","-d",tools,converter])
    for index in (1,2):
        request=output/("record-pass%d.tsv"%index)
        rows=[]
        for name,selected in recipe["recordPass%d"%index].items():
            source=working[name]
            rows.append(str(source)+"\tengine-core/src/main/java/"+name+"\t"+sha(source.read_bytes())+"\t"+",".join(selected))
        request.write_text("\n".join(rows)+"\n")
        converted=output/("record-pass%d"%index)
        run([a.java,"-cp",tools,"dev.openallay.build.RecordValueSourceConverter",request,converted])
        for name in recipe["recordPass%d"%index]:
            working[name]=converted/"candidate/engine-core/src/main/java"/name
    packet=output/"source-packet"; packet.mkdir(); patch=""; rows=[]
    for name in sorted(before):
        path="engine-core/src/main/java/"+name; old=before[name]; raw=working[name].read_bytes()
        new,format_edits=normalize_format(name,raw)
        for side,blob in [("pre",old),("post",new)]:
            target=packet/side/path; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(blob)
        patch+="".join(difflib.unified_diff(old.decode().splitlines(True),new.decode().splitlines(True),fromfile="a/"+path,tofile="b/"+path))
        rows.append({"path":path,"pre_sha256":sha(old),"post_sha256":sha(new),"pre_bytes":len(old),"post_bytes":len(new),
                     "converterRawPostSha256":sha(raw),"converterRawPostBytes":len(raw),"formatEdits":format_edits})
    for row in rows:
        target=contract["normalizedCanonicalTargets"][row["path"]]
        current=(project/row["path"]).read_bytes()
        if sha(current)!=target["sha256"] or len(current)!=target["bytes"] or row["post_sha256"]!=target["sha256"] or row["post_bytes"]!=target["bytes"]:
            raise ValueError("Replayed normalized candidate differs from current canonical source: "+row["path"])
    (packet/"historical-source-custody.json").write_text(json.dumps(historical,indent=2)+"\n")
    (packet/"source.patch").write_bytes(patch.encode())
    (packet/"manifest.json").write_text(json.dumps({"scope":recipe["scope"],"files":rows,"patch_sha256":sha(patch.encode()),
        "patch_bytes":len(patch.encode()),"recipeSha256":sha(a.recipe.read_bytes()),"acceptedConverterSha256":recipe["converterSha256"],
        "wholeRuntimeJava8Acceptance":False,"pendingAcceptance":"Original modern vs actual full dependency Java8 source compile/runtime fixture; full affected modern tests"},indent=2)+"\n")
    print("Review-only fullowner canonical packet: "+str(packet))


if __name__=="__main__": main()
