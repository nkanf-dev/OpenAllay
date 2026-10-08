#!/usr/bin/env python3
"""Review-only whole remaining switch cohort using accepted public compiler tools."""
import argparse,base64,difflib,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--source-root",type=Path,required=True);p.add_argument("--production-classpath-file",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();source=root/"engine-core/src/main/java";cpath=Path(__file__).with_name("source-contract.json");c=json.loads(cpath.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest();assert sha(Path(__file__).read_bytes())==c["runner_sha256"]
for i in c["tools"]:assert sha((root/i["path"]).read_bytes())==i["sha256"],"Accepted switch/renderer differs"
actual={str(p.relative_to(source)):p for p in source.rglob("*.java")};assert set(actual)=={i["path"] for i in c["sources"]}
for i in c["sources"]:assert sha(actual[i["path"]].read_bytes())==i["sha256"],"Whole current source raw drift: "+i["path"]
selected=c["selected_owners"];assert len(selected)==c["selected_owner_count"]==4 and set(selected).issubset(actual),"Exact four whole owner selection differs"
a.output.mkdir(parents=True,exist_ok=False)
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
classes=a.output/"pattern-tool-classes";classes.mkdir();run([a.jdk17/"bin/javac","--release","17","-d",classes]+[root/i["path"] for i in c["tools"]]);request=a.output/"selected-owners.txt";request.write_text("\n".join(selected)+"\n");out=a.output/"classified"
r=run([a.jdk17/"bin/java","-cp",classes,"dev.openallay.build.CanonicalPatternPort",source,a.production_classpath_file.read_text().strip(),request,out]);(a.output/"pattern-classifier.log").write_bytes(r.stdout+r.stderr)
rows=[];patch=[]
for line in(out/"owner-status.tsv").read_text().splitlines():
 col=line.split("\t");path=col[0];row={"path":path,"status":col[1],"sites":int(col[2])}
 if col[1]=="REJECTED":row["reason"]=base64.b64decode(col[3]).decode();assert not(out/"post"/path).exists(),"No partial rejected owner"
 else:
  assert col[1]=="SUPPORTED";before=(out/"pre"/path).read_bytes();after=(out/"post"/path).read_bytes();assert before==actual[path].read_bytes();row.update(pre_sha256=sha(before),post_sha256=sha(after),changed=before!=after)
  if row["changed"]:
   logical="engine-core/src/main/java/"+path;patch.append("".join(difflib.unified_diff(before.decode().splitlines(True),after.decode().splitlines(True),fromfile="a/"+logical,tofile="b/"+logical)))
 rows.append(row)
assert {r["path"] for r in rows}==set(selected)
(a.output/"supported-four-remaining-pattern-owners.patch").write_text("".join(patch));(a.output/"four-remaining-pattern-owner-receipt.json").write_text(json.dumps({"scope":c["scope"],"source_count":len(actual),"selected_owners":len(selected),"owner_status":rows,"tool_pins":c["tools"],"source_contract_sha256":sha(cpath.read_bytes()),"runtimeJava8Accepted":False},indent=2)+"\n")
print("CLASSIFIED four remaining whole pattern owners="+str(len(selected))+" changed="+str(sum(r.get("changed",False) for r in rows)))
