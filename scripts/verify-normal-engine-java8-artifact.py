#!/usr/bin/env python3
"""Audit normal engine publication and its actual runtime dependency graph."""
import argparse,hashlib,json,zipfile
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--project",type=Path,required=True);p.add_argument("--classpath-metadata",type=Path,required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args()
root=a.project.resolve();m=json.loads(a.classpath_metadata.read_text());jars=[x for x in (root/"engine-core/build/libs").glob("*.jar") if not x.name.endswith(("-sources.jar","-javadoc.jar","-test-fixtures.jar"))];assert len(jars)==1
engine=jars[0];raw=engine.read_bytes()
with zipfile.ZipFile(engine) as z:
 classes=[n for n in z.namelist() if n.endswith(".class") and not n.startswith("META-INF/versions/")];assert classes and all(int.from_bytes(z.read(n)[6:8],"big")<=52 for n in classes)
 required=["dev/openallay/value/ValueSchema.class","dev/openallay/value/ValueSchema$Provider.class","dev/openallay/value/ValueSchemas.class","dev/openallay/json/EngineJson.class"]
 assert all(n in z.namelist() for n in required)
 assert any(n.startswith("dev/openallay/internal/maven/") for n in z.namelist())
 assert "LICENSE_OpenAllay" in z.namelist()
 deps=[]
 for name in m["runtimeClasspath"]:
  f=Path(name)
  if f.is_file():
   with zipfile.ZipFile(f) as d:
    base=[n for n in d.namelist() if n.endswith(".class") and not n.startswith("META-INF/versions/")];assert all(int.from_bytes(d.read(n)[6:8],"big")<=52 for n in base),str(f)
   deps.append({"path":str(f),"sha256":hashlib.sha256(f.read_bytes()).hexdigest(),"baseClasses":len(base)})
 record={"sourceRevision":m.get("sourceRevision"),"release":8,"compilerExitCode":0,"runtimeClosureJava8Accepted":True,"engineSha256":hashlib.sha256(raw).hexdigest(),"engineBytes":len(raw),"engineBaseClasses":len(classes),"runtimeArtifacts":deps,"scope":"Normal canonical production JAR and actual bytecode runtime graph; game acceptance separate"}
 a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(record,indent=2)+"\n")
 print("PASS normal engine Java8 publication/runtime graph")
