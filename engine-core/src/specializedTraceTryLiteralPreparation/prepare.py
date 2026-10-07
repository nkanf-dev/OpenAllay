#!/usr/bin/env python3
"""Review-only exact specialized Java8 syntax boundaries and public literal values; no source writes."""
import argparse,difflib,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--source-root",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
a=p.parse_args();root=a.source_root.resolve();cpath=Path(__file__).with_name("source-contract.json");c=json.loads(cpath.read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest();assert sha(Path(__file__).read_bytes())==c["runner_sha256"]
assert sha((root/c["literal_tool"]["path"]).read_bytes())==c["literal_tool"]["sha256"]
original={i["path"]:(root/i["path"]).read_bytes() for i in c["owners"]}
for i in c["owners"]:assert sha(original[i["path"]])==i["pre_sha256"],"Specialized wholeowner rawsource differs"
a.output.mkdir(parents=True,exist_ok=False)
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
classes=a.output/"literal-tool-classes";classes.mkdir();run([a.jdk17/"bin/javac","--release","17","-d",classes,root/c["literal_tool"]["path"]])
selected=a.output/"literal-owners.txt";selected.write_text("\n".join(c["literal_owners"])+"\n");lit=a.output/"actual-literal-output";run([a.jdk17/"bin/java","-cp",classes,"dev.openallay.build.CanonicalTextBlockPort",root/"engine-core/src/main/java",selected,lit])
patch=[];rows=[]
for i in c["owners"]:
 path=i["path"];rel=path.removeprefix("engine-core/src/main/java/");before=original[path]
 text=(lit/"post"/rel).read_text() if rel in c["literal_owners"] else before.decode()
 for op in i["exact_changes"]:
  assert text.count(op["old"])==op["count"],"Exact specialized boundary source differs: "+path
  text=text.replace(op["old"],op["new"])
 if path.endswith("CommandCapabilityConfigWriter.java"):
  # Exactly one already-materialized compiler string literal return; its value is never redecoded.
  assert text.count("        return ")==1 and text.count(".formatted(config.enabled());")==1
  text=text.replace("        return ","        return String.format(",1).replace(".formatted(config.enabled());",", config.enabled());",1)
 after=text.encode();target=a.output/"post"/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(after)
 rows.append({"path":path,"pre_sha256":sha(before),"post_sha256":sha(after),"actual_literal_source":rel in c["literal_owners"]})
 patch.append("".join(difflib.unified_diff(before.decode().splitlines(True),text.splitlines(True),fromfile="a/"+path,tofile="b/"+path)))
assert original=={path:(root/path).read_bytes() for path in original}
(a.output/"specialized-trace-try-literal.patch").write_text("".join(patch));(a.output/"specialized-source-receipt.json").write_text(json.dumps({"scope":c["scope"],"owners":rows,"literal_tool_sha256":c["literal_tool"]["sha256"],"contract_sha256":sha(cpath.read_bytes()),"runtimeAccepted":False},indent=2)+"\n")
print("PREPARED complete5specializedsourceowners with genuineLiteralTree values")
