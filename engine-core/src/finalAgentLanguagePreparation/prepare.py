#!/usr/bin/env python3
"""Remote review-only authentic final agent owner language/API patch; no fake Java8 runtime closure."""
import argparse, difflib, hashlib, json, subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--source-root",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();contract_path=Path(__file__).with_name("source-contract.json");c=json.loads(contract_path.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c["runner_sha256"]
def run(command):
 r=subprocess.run(list(map(str,command)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
assert sha((root/c["converter"]["path"]).read_bytes())==c["converter"]["sha256"]
assert sha((root/c["helper"]["path"]).read_bytes())==c["helper"]["sha256"],"Accepted exceptionallyAsync helper differs"
a.output.mkdir(parents=True,exist_ok=False);original=a.output/"original"
for i in c["owners"]:
 b=run(["git","-C",root,"cat-file","blob",i["git_blob"]]).stdout;assert sha(b)==i["pre_sha256"]
 assert (root/i["path"]).read_bytes()==b,"Current original owner differs"
 dest=original/i["path"];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b)
tools=a.output/"converter-classes";tools.mkdir();run([a.jdk17/"bin/javac","--release","17","-d",tools,root/c["converter"]["path"]])
req=a.output/"converter-request.tsv";req.write_text("\n".join("\t".join([str(original/i["path"]),i["path"],i["pre_sha256"],i["record"]]) for i in c["owners"])+"\n")
converted=a.output/"converted";run([a.jdk17/"bin/java","-cp",tools,"dev.openallay.build.RecordValueSourceConverter",req,converted])
patch=[];records=[]
for i in c["owners"]:
 source=converted/"candidate"/i["path"];raw=source.read_bytes();text=raw.decode()
 for op in i["api_substitutions"]:
  assert text.count(op["old"])==op["count"],"ExactAPIpreimage differs: "+i["path"]
  text=text.replace(op["old"],op["new"])
 import re
 text=re.sub(r"(?m)^[ \t]+(?=\r?$)","",text);post=text.encode();before=(root/i["path"]).read_bytes()
 dest=a.output/"authenticated"/i["path"];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(post)
 records.append({"path":i["path"],"before_sha256":sha(before),"converter_sha256":sha(raw),"post_sha256":sha(post)})
 patch.append("".join(difflib.unified_diff(before.decode().splitlines(True),text.splitlines(True),fromfile="a/"+i["path"],tofile="b/"+i["path"])))
(a.output/"authentic-source-only-agent.patch").write_text("".join(patch));(a.output/"source-preparation-receipt.json").write_text(json.dumps({"scope":c["scope"],"owners":records,"contract_sha256":sha(contract_path.read_bytes())},indent=2)+"\n")
print("PREPARED authentic2agentowners sourceonly; no fullJava8Rhino closure claimed")
