#!/usr/bin/env python3
"""Exact original Git CoreJavascriptContract vs current real-engine whole UTF8 contract output."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--source-root",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--production-classpath-file",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();cp=a.production_classpath_file.read_text().strip();cpath=Path(__file__).with_name("source-contract.json");c=json.loads(cpath.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c["runner_sha256"]
for i in c["current_sources"]:assert sha((root/i["path"]).read_bytes())==i["sha256"],"Candidate source differs: "+i["path"]
assert sha((root/c["reporter"]["path"]).read_bytes())==c["reporter"]["sha256"]
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
old=run(["git","-C",root,"cat-file","blob",c["original"]["git_blob"]]).stdout;assert sha(old)==c["original"]["sha256"]
assert run(["git","-C",root,"rev-parse",c["original"]["commit"]+":"+c["original"]["path"]]).stdout.decode().strip()==c["original"]["git_blob"]
a.output.mkdir(parents=True,exist_ok=False);original=a.output/"original"/c["original"]["path"];original.parent.mkdir(parents=True,exist_ok=True);original.write_bytes(old)
reporter=root/c["reporter"]["path"];reports={}
for flavor in ["original","current"]:
 classes=a.output/(flavor+"-classes");classes.mkdir()
 sources=[reporter]+([original] if flavor=="original" else [root/c["original"]["path"]])
 run([a.jdk17/"bin/javac","--release","17","-encoding","UTF-8","-classpath",cp,"-sourcepath","","-d",classes]+sources)
 r=run([a.jdk17/"bin/java","-cp",str(classes)+os.pathsep+cp,"dev.openallay.script.schema.CoreContractExactOracle"]);reports[flavor]=r.stdout;(a.output/(flavor+"-contract.txt")).write_bytes(r.stdout)
assert reports["original"]==reports["current"],"Whole original/current literal/catalog UTF8 contract differs"
(a.output/"receipt.json").write_text(json.dumps({"scope":"Full original Git vs current canonical CoreJavascriptContract UTF8 bytes, normal actual engine classpath; modern-only source behavior proof","output_sha256":sha(reports["original"]),"output_bytes":len(reports["original"]),"source_contract_sha256":sha(cpath.read_bytes()),"equal":True},indent=2)+"\n")
print("PASS whole original/current host contract bytes")
