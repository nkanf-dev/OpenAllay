#!/usr/bin/env python3
"""Finite JVM oracle for existing-resource -> same-owned-alias TWR syntax only."""
import argparse,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--jdk8",type=Path,required=True);p.add_argument("--fixtures",type=Path,required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=False)
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
reports={}
for flavor,name in [("original17","original-existing-resource-fixture.java"),("release8","java8-owned-alias-fixture.java"),("true8","java8-owned-alias-fixture.java")]:
 source=a.output/flavor/"ExistingResourceFlow.java";source.parent.mkdir();source.write_bytes((a.fixtures/name).read_bytes());classes=a.output/(flavor+"-classes");classes.mkdir();compiler=a.jdk8/"bin/javac" if flavor=="true8" else a.jdk17/"bin/javac";options=["-source","8","-target","8"] if flavor=="true8" else ["--release","17" if flavor=="original17" else "8"]
 run([compiler]+options+["-d",classes,source]);vm=a.jdk17/"bin/java" if flavor=="original17" else a.jdk8/"bin/java";reports[flavor]=run([vm,"-cp",classes,"ExistingResourceFlow"]).stdout
assert reports["original17"]==reports["release8"]==reports["true8"]
(a.output/"receipt.json").write_text(json.dumps({"scope":"Same-existing-resource ownedalias closesexactresource closeorder/primaryexception/suppression/null/settledboundary","equal":True,"vector_sha256":hashlib.sha256(reports["original17"]).hexdigest(),"vectors":reports["original17"].decode()},indent=2)+"\n");print("PASS existing-resource ownedalias actual JVM semantic oracle")
