#!/usr/bin/env python3
"""Genuine source-only retained Skill modern/Java8 oracle. Every admitted dependency is a real owner."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--project",type=Path,required=True)
    p.add_argument("--original-root",type=Path,required=True,help="Raw original Skill pre/ root; no copied model variants")
    p.add_argument("--candidate-root",type=Path,required=True,help="Real record-converter source-packet post/ root")
    p.add_argument("--source-manifest",type=Path,required=True)
    p.add_argument("--javac",type=Path,required=True)
    p.add_argument("--java",type=Path,required=True)
    p.add_argument("--java8",type=Path,required=True)
    p.add_argument("--gson",type=Path,action="append",required=True,help="Repeat for genuine Gson2.8.0 and supported modern host JARs")
    p.add_argument("--output",type=Path,required=True)
    a=p.parse_args(); project=a.project.resolve(); out=a.output.resolve()
    if out==project or project in out.parents: raise ValueError("External fresh output required")
    out.mkdir(parents=True,exist_ok=False); manifest=json.loads(a.source_manifest.read_text()); commands=[]; receipts=[]
    fixture=project/"engine-core/src/retainedSkillJava8Test/java/dev/openallay/skill/RetainedSkillJava8Fixture.java"
    def sha(blob): return hashlib.sha256(blob).hexdigest()
    def run(command,label):
        commands.append([str(x) for x in command]); (out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with (out/(label+".log")).open("wb") as log: subprocess.run(commands[-1],stdout=log,stderr=subprocess.STDOUT,check=True,cwd=project)
    run([a.java8,"-version"],"java8-version")
    if 'version "1.8.' not in (out/"java8-version.log").read_text(): raise ValueError("Require genuine Java8 runtime")
    for name,wanted in manifest["sharedCurrentSourceHashes"].items():
        if sha((project/name).read_bytes())!=wanted: raise ValueError("Accepted shared dependency changed: "+name)
    for gson in a.gson:
        if not gson.is_file(): raise ValueError("Missing genuine Gson artifact")
        tag=sha(gson.read_bytes())[:12]; reports=[]
        receipts.append({"gson":str(gson),"sha256":sha(gson.read_bytes()),"bytes":gson.stat().st_size})
        for mode,release,runtime in [("original-modern","17",a.java),("candidate-java8","8",a.java8)]:
            selected=[]; sources=[]
            overlay=a.original_root if mode=="original-modern" else a.candidate_root
            for name in manifest["productionSourcePaths"]:
                alternative=overlay/name
                path=alternative if alternative.is_file() else project/name
                if not path.is_file(): raise ValueError("Missing authentic source owner: "+name)
                blob=path.read_bytes()
                if mode=="original-modern" and name.removeprefix("engine-core/src/main/java/") in manifest["knownOriginalSkillOwnerHashes"]:
                    if sha(blob)!=manifest["knownOriginalSkillOwnerHashes"][name.removeprefix("engine-core/src/main/java/")]: raise ValueError("Original Skill owner changed")
                sources.append(path); selected.append({"logicalPath":name,"source":str(path),"sha256":sha(blob),"bytes":len(blob)})
            classes=out/(tag+"-"+mode+"-classes"); classes.mkdir()
            run([a.javac,"--release",release,"-proc:none","-encoding","UTF-8","-cp",gson,"-d",classes,*sources,fixture],tag+"-"+mode+"-compile")
            run([runtime,"-cp",str(classes)+__import__("os").pathsep+str(gson),"dev.openallay.skill.RetainedSkillJava8Fixture"],tag+"-"+mode+"-runtime")
            report=(out/(tag+"-"+mode+"-runtime.log")).read_bytes(); reports.append(report)
            receipts.append({"host":tag,"mode":mode,"sources":selected,"fixtureSha256":sha(fixture.read_bytes()),"reportSha256":sha(report),"classCount":len(list(classes.rglob("*.class")))})
        if reports[0]!=reports[1]: raise ValueError("Actual original modern/Java8 reports differ")
    (out/"receipt.json").write_text(json.dumps({"actualSourceClosure":True,"sourceCount":len(manifest["productionSourcePaths"]),"receipts":receipts,"wholeRuntimeJava8Acceptance":False},indent=2)+"\n")
    print("PASS original modern=actual fullsource Java8 retained Skill fixture with genuine Gson hosts")


if __name__=="__main__": main()
