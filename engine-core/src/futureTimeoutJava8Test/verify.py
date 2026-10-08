#!/usr/bin/env python3
"""Remote standard CompletableFuture orTimeout semantic oracle, no external production closure."""
import argparse, hashlib, json, os, subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument("--source-root",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--jdk8",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();contract_path=Path(__file__).with_name("source-contract.json");c=json.loads(contract_path.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c["runner_sha256"]
for item in c["sources"]:assert sha((root/item["path"]).read_bytes())==item["sha256"],item["path"]
a.output.mkdir(parents=True,exist_ok=False)
def run(command):
 r=subprocess.run(list(map(str,command)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
versions={}
for name,path in [("java8",a.jdk8/"bin/java"),("javac8",a.jdk8/"bin/javac"),("java17",a.jdk17/"bin/java")]:
 r=run([path,"-version"]);versions[name]=(r.stdout+r.stderr).decode()
assert "1.8." in versions["java8"] and "1.8." in versions["javac8"]
vectors={};majors={}
for flavor in ["modern17","release8","javac8"]:
 out=a.output/(flavor+"-classes");out.mkdir();compiler=a.jdk8/"bin/javac" if flavor=="javac8" else a.jdk17/"bin/javac"
 options=["-source","8","-target","8"] if flavor=="javac8" else ["--release","17" if flavor=="modern17" else "8"]
 r=run([compiler]+options+["-encoding","UTF-8","-sourcepath","","-d",out]+[root/i["path"] for i in c["sources"]]);(a.output/(flavor+"-compile.log")).write_bytes(r.stdout+r.stderr)
 majors[flavor]={}
 for path in out.rglob("*.class"):
  major=int.from_bytes(path.read_bytes()[6:8],"big");majors[flavor][str(path.relative_to(out))]=major
  if flavor!="modern17":assert major==52
 vm=a.jdk17/"bin/java" if flavor=="modern17" else a.jdk8/"bin/java"
 r=run([vm,"-cp",out,"dev.openallay.util.FutureTimeoutJava8Fixture"]+([] if flavor=="modern17" else ["java8"]));vectors[flavor]=r.stdout;(a.output/(flavor+"-vectors.log")).write_bytes(r.stdout+r.stderr)
assert vectors["modern17"]==vectors["release8"]==vectors["javac8"]
(a.output/"receipt.json").write_text(json.dumps({"scope":c["scope"],"jdk_versions":versions,"class_majors":majors,"vectors_equal":True,"vector_sha256":sha(vectors["modern17"]),"contract_sha256":sha(contract_path.read_bytes())},indent=2)+"\n")
print("PASS standard future deadline native modern vs canonical genuineJava8 helper")
