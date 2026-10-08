#!/usr/bin/env python3
"""One complete-source remaining-owner API/pattern classification with accepted public compiler tools."""
import argparse,base64,difflib,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--source-root",type=Path,required=True);p.add_argument("--production-classpath-file",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--output",type=Path,required=True);p.add_argument("--phase",choices=["api","pattern"],required=True)
a=p.parse_args();root=a.source_root.resolve();source=root/"engine-core/src/main/java";cpath=Path(__file__).with_name("source-contract.json");c=json.loads(cpath.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c["runner_sha256"],"Driver differs"
for i in c["tools"]:assert sha((root/i["path"]).read_bytes())==i["sha256"],"Accepted tool differs"
actual={str(p.relative_to(source)):p for p in source.rglob("*.java")};assert set(actual)=={i["path"] for i in c["full_sources"]},"Complete canonical source frontier differs"
for i in c["full_sources"]:assert sha(actual[i["path"]].read_bytes())==i["sha256"],"Raw canonical source drift: "+i["path"]
selected=[i["path"] for i in c["full_sources"] if not any(i["path"].startswith(prefix) for prefix in c["excluded_prefixes"])];assert len(selected)==c["selected_owner_count"]
cp=a.production_classpath_file.read_text().strip();assert cp
# Production classpath is root's normal Gradle export, byte inventory recorded for diagnosis, not claimed Java8-safe.
classpath=[]
import os
for entry in cp.split(os.pathsep):
 path=Path(entry);assert path.exists(),"Production classpath entry missing"
 classpath.append({"path":str(path),"sha256":sha(path.read_bytes()) if path.is_file() else None,"directory":path.is_dir()})
a.output.mkdir(parents=True,exist_ok=False)
def run(command):
 r=subprocess.run(list(map(str,command)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
tools=a.output/"tool-classes";tools.mkdir();run([a.jdk17/"bin/javac","--release","17","-encoding","UTF-8","-d",tools]+[root/i["path"] for i in c["tools"]])
request=a.output/"selected-owners.txt";request.write_text("\n".join(selected)+"\n")
receipts={};outputs={}
for kind,clazz in [(a.phase, "CanonicalJava8ApiPort" if a.phase=="api" else "CanonicalPatternPort")]:
 out=a.output/(kind+"-classification");r=run([a.jdk17/"bin/java","-cp",tools,"dev.openallay.build."+clazz,source,cp,request,out]);(a.output/(kind+".log")).write_bytes(r.stdout+r.stderr)
 status={}
 for line in (out/"owner-status.tsv").read_text().splitlines():
  col=line.split("\t");path=col[0];assert path not in status
  row={"path":path,"status":col[1],"sites":int(col[2])}
  if col[1]=="REJECTED":
   row["reason"]=base64.b64decode(col[3]).decode();assert not(out/"post"/path).exists(),"Partial rejected owner output"
  else:
   assert col[1]=="SUPPORTED";before=(out/"pre"/path).read_bytes();after=(out/"post"/path).read_bytes();assert before==actual[path].read_bytes()
   row.update(pre_sha256=sha(before),post_sha256=sha(after),changed=before!=after);outputs[(kind,path)]=after
  status[path]=row
 assert set(status)==set(selected),"Whole owner classifier frontier differs"
 receipts[kind]=list(status.values())
 # Separate full supported patches are not combined blindly for same-owner source spans.
 patch=[]
 for row in receipts[kind]:
  if row["status"]=="SUPPORTED" and row["changed"]:
   path=row["path"];before=actual[path].read_bytes();after=outputs[(kind,path)];logical="engine-core/src/main/java/"+path
   patch.append("".join(difflib.unified_diff(before.decode().splitlines(True),after.decode().splitlines(True),fromfile="a/"+logical,tofile="b/"+logical)))
 (a.output/(kind+"-supported-whole-owners.patch")).write_text("".join(patch))
overlap=[] # Stages are never merged from independently generated same-owner outputs.
receipt={"scope":c["scope"],"actual_source_count":len(actual),"selected_owner_count":len(selected),"tool_pins":c["tools"],"classifications":receipts,"phase":a.phase,"requires_next_stage_fresh_actual_source_pins":True,"production_classpath":classpath,"contract_sha256":sha(cpath.read_bytes()),"source_runtime_compiled":False}
(a.output/"remaining-api-pattern-classification.json").write_text(json.dumps(receipt,indent=2)+"\n")
print("CLASSIFIED complete current source cohort owners="+str(len(selected))+" phase="+a.phase)
