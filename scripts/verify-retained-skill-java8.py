#!/usr/bin/env python3
"""Finite authentic-source retained Skill oracle; candidate bytes are bound to the converter manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess


def digest(blob): return hashlib.sha256(blob).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--project",type=Path,required=True)
    p.add_argument("--source-packet",type=Path,required=True,help="Actual materializer source-packet/ with raw pre/post and manifest")
    p.add_argument("--source-contract",type=Path,required=True,help="Repository-owned finite verification contract")
    p.add_argument("--verify-source-custody",action="store_true",help="Verify replay/hash/current-canonical equality without rerunning successful runtime vectors")
    p.add_argument("--javac",type=Path)
    p.add_argument("--java",type=Path)
    p.add_argument("--javac8",type=Path)
    p.add_argument("--java8",type=Path)
    p.add_argument("--gson",type=Path,action="append")
    p.add_argument("--output",type=Path,required=True)
    a=p.parse_args(); project=a.project.resolve(); packet=a.source_packet.resolve(); out=a.output.resolve()
    if out==project or project in out.parents: raise ValueError("External fresh output required")
    out.mkdir(parents=True,exist_ok=False)
    contract_path=project/"engine-core/src/retainedSkillJava8Test/source-contract.json"
    if a.source_contract.resolve()!=contract_path: raise ValueError("Use the repository-owned source contract")
    contract=json.loads(contract_path.read_text()); commands=[]; receipts=[]
    expected_pin_paths=set(contract["artifactPins"])
    if expected_pin_paths!={"fixture","runner","materializer","recipe","sourceManifest"}: raise ValueError("Incomplete finite build artifact pins")
    for label,pin in contract["artifactPins"].items():
        if digest((project/pin["path"]).read_bytes())!=pin["sha256"]: raise ValueError("Pinned artifact changed: "+label)
    recipe_path=project/contract["artifactPins"]["recipe"]["path"]
    recipe=json.loads(recipe_path.read_text())
    sources=json.loads((project/contract["artifactPins"]["sourceManifest"]["path"]).read_text())
    if sources["productionSourcePaths"]!=contract["productionSourcePaths"] or len(set(contract["productionSourcePaths"]))!=contract["productionSourceCount"]:
        raise ValueError("Exact production source closure changed")
    changed={"engine-core/src/main/java/"+n for n in recipe["rawPreimageSha256"]}
    if changed!=set(contract["changedSkillOwners"]) or len(changed)!=13: raise ValueError("Exact13 Skill owner boundary changed")
    manifest_path=packet/"manifest.json"; materialized=json.loads(manifest_path.read_text())
    if materialized["recipeSha256"]!=digest(recipe_path.read_bytes()) or materialized["acceptedConverterSha256"]!=recipe["converterSha256"]:
        raise ValueError("Materialized output has wrong recipe/converter custody")
    if digest((project/recipe["converter"]).read_bytes())!=recipe["converterSha256"]: raise ValueError("Accepted converter changed")
    rows=materialized["files"]
    if len(rows)!=13 or {r["path"] for r in rows}!=changed: raise ValueError("Candidate has unexpected owner")
    patch=(packet/"source.patch").read_bytes()
    if digest(patch)!=materialized["patch_sha256"] or len(patch)!=materialized["patch_bytes"]: raise ValueError("Materialized patch changed")
    for side in ["pre","post"]:
        actual={str(f.relative_to(packet/side)) for f in (packet/side).rglob("*") if f.is_file()}
        if actual!=changed: raise ValueError("Forbidden unchanged/model overlay in "+side)
    for row in rows:
        for side in ["pre","post"]:
            blob=(packet/side/row["path"]).read_bytes()
            if digest(blob)!=row[side+"_sha256"] or len(blob)!=row[side+"_bytes"]: raise ValueError("Materializer bound candidate hash changed: "+row["path"])
        name=row["path"].removeprefix("engine-core/src/main/java/")
        if row["pre_sha256"]!=recipe["rawPreimageSha256"][name]: raise ValueError("Wrong original canonical Skill source")
        target=contract["normalizedCanonicalTargets"][row["path"]]
        current=(project/row["path"]).read_bytes()
        if digest(current)!=target["sha256"] or len(current)!=target["bytes"] or row["post_sha256"]!=target["sha256"] or row["post_bytes"]!=target["bytes"]:
            raise ValueError("Candidate/current normalized production equality failed: "+row["path"])
    history=json.loads((packet/"historical-source-custody.json").read_text())
    if len(history)!=13 or {row["path"] for row in history}!=changed: raise ValueError("Historical source receipt boundary differs")
    for row in history:
        name=row["path"].removeprefix("engine-core/src/main/java/")
        if row["sourceCommit"]!=recipe["acceptedSourceCommit"] or row["gitBlob"]!=recipe["rawPreimageGitBlob"][name] or row["rawSha256"]!=recipe["rawPreimageSha256"][name]:
            raise ValueError("Historical source receipt pins differ")
    settled_model=project/contract["modelSourceContract"]
    model_contract=json.loads(settled_model.read_text())
    # Each model postimage must be the actual repository owner, never an external candidate overlay.
    model_rows={row["path"]:row for row in model_contract["files"]}
    for name in contract["model12Paths"]:
        row=model_rows[name]
        wanted=row["post_sha256"]
        if digest((project/name).read_bytes())!=wanted: raise ValueError("Model owner not at accepted contract postimage: "+name)
    for name,wanted in sources["sharedCurrentSourceHashes"].items():
        if digest((project/name).read_bytes())!=wanted: raise ValueError("Accepted shared dependency changed: "+name)
    if a.verify_source_custody:
        (out/"source-custody-receipt.json").write_text(json.dumps({"sourceContractSha256":digest(contract_path.read_bytes()),"materializerManifestSha256":digest(manifest_path.read_bytes()),
            "historicalSourceReceiptSha256":digest((packet/"historical-source-custody.json").read_bytes()),"normalizedCanonicalOwners":contract["normalizedCanonicalTargets"],
            "sourceEqualityVerified":True,"runtimeReexecuted":False,"preservedRuntimeOracle":contract["preservedRuntimeOracle"]},indent=2)+"\n")
        print("PASS historical replay/current normalized canonical source custody; preserved runtime proof, no runtime replay")
        return
    if not all([a.javac,a.java,a.javac8,a.java8,a.gson]): raise ValueError("Runtime mode requires both actual compilers/runtimes and pinned Gson hosts")
    libraries=[]
    if len(a.gson)!=len(contract["gsonPins"]): raise ValueError("Require exactly the approved genuine Gson hosts")
    seen=set()
    for gson in a.gson:
        blob=gson.read_bytes(); matches=[]
        for pin in contract["gsonPins"]:
            if len(blob)==pin["bytes"] and hashlib.new(pin["digest_algorithm"],blob).hexdigest()==pin["digest"]: matches.append(pin)
        if len(matches)!=1 or matches[0]["name"] in seen: raise ValueError("Gson is not a unique authenticated host: "+str(gson))
        seen.add(matches[0]["name"]); libraries.append((gson,matches[0]))
    def run(command,label):
        commands.append([str(x) for x in command]); (out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with (out/(label+".log")).open("wb") as log: subprocess.run(commands[-1],stdout=log,stderr=subprocess.STDOUT,check=True,cwd=project)
    run([a.javac8,"-version"],"javac8-version"); run([a.java8,"-version"],"java8-version")
    if not (out/"javac8-version.log").read_text().strip().startswith("javac 1.8."): raise ValueError("Require genuine javac8")
    if 'version "1.8.' not in (out/"java8-version.log").read_text(): raise ValueError("Require genuine Java8 runtime")
    fixture=project/contract["artifactPins"]["fixture"]["path"]
    for gson,pin in libraries:
        reports=[]; tag=pin["name"].removesuffix(".jar")
        for mode,compiler,release,runtime in [("original-modern",a.javac,"17",a.java),("candidate-release8",a.javac,"8",a.java8),("candidate-javac8",a.javac8,None,a.java8)]:
            selected=[]; paths=[]
            for name in contract["productionSourcePaths"]:
                path=packet/("pre" if mode=="original-modern" else "post")/name if name in changed else project/name
                blob=path.read_bytes(); paths.append(path); selected.append({"path":name,"actualSource":str(path),"sha256":digest(blob),"bytes":len(blob)})
            classes=out/(tag+"-"+mode+"-classes"); classes.mkdir()
            flags=["--release",release] if release is not None else ["-source","8","-target","8"]
            run([compiler,*flags,"-proc:none","-encoding","UTF-8","-cp",gson,"-d",classes,*paths,fixture],tag+"-"+mode+"-compile")
            majors={}
            for path in classes.rglob("*.class"):
                blob=path.read_bytes()
                if blob[:4]!=b"\xca\xfe\xba\xbe": raise ValueError("Bad classfile")
                major=struct.unpack(">H",blob[6:8])[0]; majors[major]=majors.get(major,0)+1
            expected_major=61 if mode=="original-modern" else 52
            if not majors or set(majors)!={expected_major}: raise ValueError("Wrong actual emitted class majors: "+str(majors))
            run([runtime,"-cp",str(classes)+os.pathsep+str(gson),"dev.openallay.skill.RetainedSkillJava8Fixture"],tag+"-"+mode+"-runtime")
            report=(out/(tag+"-"+mode+"-runtime.log")).read_bytes()
            if ("checks="+str(contract["expectedFixtureChecks"])+"\n").encode() not in report or not report.endswith(b"PASS actual retained skill context oracle\n"):
                raise ValueError("Finite fixture count/status missing")
            reports.append(report)
            receipts.append({"host":pin,"mode":mode,"sources":selected,"classMajors":majors,"reportSha256":digest(report),"fixtureChecks":contract["expectedFixtureChecks"]})
        if reports[0]!=reports[1] or reports[0]!=reports[2]: raise ValueError("Actual original17/release8/javac8 reports differ")
    (out/"receipt.json").write_text(json.dumps({"materializerManifestSha256":digest(manifest_path.read_bytes()),"sourceContractSha256":digest(contract_path.read_bytes()),
        "modelSourceContractSha256":digest(settled_model.read_bytes()),"productionSourceCount":contract["productionSourceCount"],"receipts":receipts,"wholeRuntimeJava8Acceptance":False},indent=2)+"\n")
    print("PASS bound original17=release8=realjavac8 actual fullsource retained Skill oracle")


if __name__=="__main__": main()
