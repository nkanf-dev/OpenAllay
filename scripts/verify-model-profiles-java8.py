#!/usr/bin/env python3
"""Authentic complete provider source frontier original17/release8/realjavac8 oracle."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess


def sha(blob): return hashlib.sha256(blob).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--project",type=Path,required=True);p.add_argument("--source-packet",type=Path,required=True)
    p.add_argument("--javac",type=Path,required=True);p.add_argument("--java",type=Path,required=True)
    p.add_argument("--javac8",type=Path,required=True);p.add_argument("--java8",type=Path,required=True)
    p.add_argument("--gson",type=Path,action="append",required=True);p.add_argument("--output",type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();packet=a.source_packet.resolve();out=a.output.resolve()
    if out==project or project in out.parents: raise ValueError("External fresh output required")
    out.mkdir(parents=True,exist_ok=False)
    contract_path=project/"engine-core/src/modelProfilesJava8Test/source-contract.json";contract=json.loads(contract_path.read_text())
    for pin in contract["artifactPins"].values():
        if sha((project/pin["path"]).read_bytes())!=pin["sha256"]: raise ValueError("Buildartifact pin differs: "+pin["path"])
    recipe_path=project/contract["artifactPins"]["recipe"]["path"];recipe=json.loads(recipe_path.read_text())
    manifest_path=packet/"manifest.json";manifest=json.loads(manifest_path.read_text())
    if manifest["recipeSha256"]!=sha(recipe_path.read_bytes()) or manifest["acceptedConverterSha256"]!=recipe["converterSha256"]: raise ValueError("Wrong materialized recipe")
    if sha((project/recipe["converter"]).read_bytes())!=recipe["converterSha256"]: raise ValueError("Accepted converter changed")
    changed={"engine-core/src/main/java/"+n for n in recipe["rawPreimageSha256"]}
    rows=manifest["files"]
    if len(rows)!=6 or {r["path"] for r in rows}!=changed: raise ValueError("Expected exact6 complete changed owners")
    for side in ["pre","post"]:
        if {str(p.relative_to(packet/side)) for p in (packet/side).rglob("*") if p.is_file()}!=changed: raise ValueError("Forbidden overlay owner")
    for row in rows:
        for side in ["pre","post"]:
            blob=(packet/side/row["path"]).read_bytes()
            if sha(blob)!=row[side+"_sha256"] or len(blob)!=row[side+"_bytes"]: raise ValueError("Candidate source changed")
        name=row["path"].removeprefix("engine-core/src/main/java/")
        if row["pre_sha256"]!=recipe["rawPreimageSha256"][name]: raise ValueError("Originalowner changed")
    patch=(packet/"source.patch").read_bytes()
    if sha(patch)!=manifest["patch_sha256"] or len(patch)!=manifest["patch_bytes"]: raise ValueError("Canonical patch changed")
    for row in contract["sharedSourcePins"]:
        blob=(project/row["path"]).read_bytes()
        if sha(blob)!=row["sha256"] or len(blob)!=row["bytes"]: raise ValueError("Shared actualdependency drift: "+row["path"])
    for row in contract["resourcePins"]:
        blob=(project/row["path"]).read_bytes()
        if sha(blob)!=row["sha256"] or len(blob)!=row["bytes"]:raise ValueError("Actualcatalog resource changed")
    if len(a.gson)!=2: raise ValueError("Require exactly two approved Gson hosts")
    libraries=[];seen=set()
    for gson in a.gson:
        blob=gson.read_bytes();matches=[pin for pin in contract["gsonPins"] if len(blob)==pin["bytes"] and hashlib.new(pin["digest_algorithm"],blob).hexdigest()==pin["digest"]]
        if len(matches)!=1 or matches[0]["name"] in seen: raise ValueError("Unauthenticated Gson host")
        seen.add(matches[0]["name"]);libraries.append((gson,matches[0]))
    commands=[];receipts=[]
    def run(command,label):
        commands.append([str(x) for x in command]);(out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with (out/(label+".log")).open("wb") as log:subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True)
    run([a.javac8,"-version"],"javac8-version");run([a.java8,"-version"],"java8-version")
    if not (out/"javac8-version.log").read_text().strip().startswith("javac 1.8.") or 'version "1.8.' not in (out/"java8-version.log").read_text(): raise ValueError("Genuine Java8 compiler/runtime required")
    for gson,pin in libraries:
        tag=pin["name"].removesuffix(".jar");reports=[]
        for mode,compiler,flags,runtime,major in [("original17",a.javac,["--release","17"],a.java,61),("release8",a.javac,["--release","8"],a.java8,52),("truejavac8",a.javac8,["-source","8","-target","8"],a.java8,52)]:
            sources=[];pins=[]
            for name in contract["productionSourcePaths"]:
                source=packet/("pre" if mode=="original17" else "post")/name if name in changed else project/name
                blob=source.read_bytes();sources.append(source);pins.append({"path":name,"source":str(source),"sha256":sha(blob),"bytes":len(blob)})
            fixtures=[project/n for n in contract["fixturePaths"]]
            classes=out/(tag+"-"+mode+"-classes");classes.mkdir()
            run([compiler,*flags,"-encoding","UTF-8","-proc:none","-cp",gson,"-d",classes,*sources,*fixtures],tag+"-"+mode+"-compile")
            majors={}
            for f in classes.rglob("*.class"):
                raw=f.read_bytes()
                if raw[:4]!=b"\xca\xfe\xba\xbe": raise ValueError("Malformed class")
                m=struct.unpack(">H",raw[6:8])[0];majors[m]=majors.get(m,0)+1
            if set(majors)!={major}: raise ValueError("Wrong emitted class majors")
            run([runtime,"-cp",str(classes)+os.pathsep+str(gson)+os.pathsep+str(project/"engine-core/src/main/resources"),"dev.openallay.model.config.ModelProfilesJava8Fixture"],tag+"-"+mode+"-runtime")
            report=(out/(tag+"-"+mode+"-runtime.log")).read_bytes()
            if b"checks=49\n" not in report or not report.endswith(b"PASS complete profile/config loader writer oracle\n"):raise ValueError("Finite fixture vectors missing")
            reports.append(report);receipts.append({"host":pin,"mode":mode,"sources":pins,"classMajors":majors,"reportSha256":sha(report),"checks":49})
        if reports[0]!=reports[1] or reports[0]!=reports[2]:raise ValueError("Originalmodern/release8/truejavac8 behavior differs")
    (out/"receipt.json").write_text(json.dumps({"sourceContractSha256":sha(contract_path.read_bytes()),"materializerManifestSha256":sha(manifest_path.read_bytes()),"actualSourceCount":len(contract["productionSourcePaths"]),"receipts":receipts,"wholeRuntimeJava8Acceptance":False},indent=2)+"\n")
    print("PASS complete canonical profile/config loader writer semantic oracle")


if __name__=="__main__":main()
