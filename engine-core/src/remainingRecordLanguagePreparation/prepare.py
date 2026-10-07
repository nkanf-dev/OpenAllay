#!/usr/bin/env python3
"""Finite source-only remaining engine record batch; uses authentic compiler inventory/materializer."""
import argparse, base64, difflib, hashlib, json, subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument("--source-root",type=Path,required=True);p.add_argument("--inventory",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();source=root/"engine-core/src/main/java";contract_path=Path(__file__).with_name("source-contract.json");c=json.loads(contract_path.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest()
assert sha(Path(__file__).read_bytes())==c["runner_sha256"],"Driver source differs"
for tool in c["tools"]:assert sha((root/tool["path"]).read_bytes())==tool["sha256"],"Accepted buildtool differs: "+tool["path"]
inventory_blob=a.inventory.read_bytes()
assert sha(inventory_blob)==c["inventory_sha256"],"Authoritative current compiler inventory differs"
report=json.loads(inventory_blob);assert report["sourceEditsEmitted"] is False
assert report["sourceCount"]==len(report["owners"]),"Compiler frontier count differs"
selected=[];excluded=[];seen=set()
for owner in report["owners"]:
 path=owner["path"];assert path not in seen;seen.add(path)
 actual=source/path;assert actual.is_file() and actual.resolve().is_relative_to(source.resolve()),"Invalid compiler owner"
 blob=actual.read_bytes();assert sha(blob)==owner["rawSha256"] and len(blob)==owner["rawBytes"],"Actual compiler owner drift: "+path
 if not owner["recordPaths"]:continue
 if any(path.startswith(prefix) for prefix in c["excluded_prefixes"]):excluded.append(owner);continue
 assert owner["counts"]["records"]==len(owner["recordPaths"]),"Parsed record count mismatch"
 selected.append(owner)
assert selected,"No remaining supported-scope record owners"
assert len(selected)==c["expected_selected_owners"] and sum(len(i["recordPaths"]) for i in selected)==c["expected_selected_records"],"Exact remaining compiler record selection differs"
a.output.mkdir(parents=True,exist_ok=False)
request=a.output/"remaining-records-request.tsv";request.write_text("\n".join("\t".join([i["path"],i["rawSha256"],",".join(i["recordPaths"])]) for i in selected)+"\n")
def run(command):
 r=subprocess.run(list(map(str,command)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
classes=a.output/"tool-classes";classes.mkdir();run([a.jdk17/"bin/javac","--release","17","-encoding","UTF-8","-d",classes]+[root/i["path"] for i in c["tools"]])
materialized=a.output/"materialized";r=run([a.jdk17/"bin/java","-cp",classes,"dev.openallay.build.CanonicalRecordBatchMaterializer",source,request,materialized]);(a.output/"materializer.log").write_bytes(r.stdout+r.stderr)
status={};supported=[];unsupported=[];patch=[]
for line in (materialized/"owner-status.tsv").read_text().splitlines():
 columns=line.split("\t");path=columns[0];assert path not in status;status[path]=columns
 item=next(i for i in selected if i["path"]==path)
 if columns[1]=="REJECTED":
  assert not(materialized/"post"/path).exists(),"Partial rejected owner output"
  unsupported.append({"path":path,"recordPaths":item["recordPaths"],"reason":base64.b64decode(columns[4]).decode()});continue
 assert columns[1]=="SUPPORTED" and int(columns[2])==len(item["recordPaths"])==int(columns[3]),"Whole owner record count differs"
 before=(materialized/"pre"/path).read_bytes();after=(materialized/"post"/path).read_bytes()
 assert sha(before)==item["rawSha256"] and before==(source/path).read_bytes(),"Materializer source custody differs"
 assert sha(after)==columns[6] and len(after)==int(columns[7]),"Materializer target custody differs"
 supported.append({"path":path,"recordPaths":item["recordPaths"],"pre_sha256":sha(before),"post_sha256":sha(after),"post_bytes":len(after),"authentic_ast_sha256":columns[4],"format_operations":base64.b64decode(columns[8]).decode()})
 logical="engine-core/src/main/java/"+path
 patch.append("".join(difflib.unified_diff(before.decode().splitlines(True),after.decode().splitlines(True),fromfile="a/"+logical,tofile="b/"+logical)))
assert set(status)=={i["path"] for i in selected},"Selected materialization frontier mismatch"
(a.output/"supported-remaining-records.patch").write_text("".join(patch));(a.output/"unsupported-whole-owners.json").write_text(json.dumps(unsupported,indent=2)+"\n")
receipt={"scope":c["scope"],"inventory_sha256":sha(a.inventory.read_bytes()),"actual_frontier_sources":report["sourceCount"],"selected_owner_count":len(selected),"selected_records":sum(len(i["recordPaths"]) for i in selected),"supported":supported,"unsupported":unsupported,"excluded_owner_count":len(excluded),"tool_pins":c["tools"],"source_runtime_compiled":False}
(a.output/"remaining-record-batch-receipt.json").write_text(json.dumps(receipt,indent=2)+"\n")
print("PREPARED actual remaining language-only whole-owner batch supported="+str(len(supported))+" rejected="+str(len(unsupported)))
