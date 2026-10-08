#!/usr/bin/env python3
"""One actual complete engine Java8 source/API diagnostic plus runtime artifact audit. No source edits."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile


def sha(blob):return hashlib.sha256(blob).hexdigest()


def classes(path):
    if path.is_file():
        with zipfile.ZipFile(path)as archive:
            return [(name,archive.read(name))for name in archive.namelist()if name.endswith(".class")]
    if path.is_dir():return [(str(file.relative_to(path)).replace(os.sep,"/"),file.read_bytes())for file in sorted(path.rglob("*.class"))]
    raise ValueError("Missing real classpath artifact: "+str(path))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--project",type=Path,required=True);p.add_argument("--classpath-metadata",type=Path,required=True);p.add_argument("--javac",type=Path,required=True);p.add_argument("--javac8",type=Path,required=True);p.add_argument("--output",type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();root=project/"engine-core/src/main/java";out=a.output.resolve()
    if out==project or project in out.parents:raise ValueError("Fresh externaldiagnosticoutput required")
    out.mkdir(parents=True,exist_ok=False);metadata=json.loads(a.classpath_metadata.read_text());sources=sorted(root.rglob("*.java"));source_paths=[str(path.resolve())for path in sources]
    if Path(metadata["sourceRoot"]).resolve()!=root or sorted(metadata["sources"])!=source_paths or metadata["producer"]!=":engine-core:compileJava":raise ValueError("Require exactnormal complete production source/classpath export")
    source_rows=[];covered=set()
    for path in sources:
        blob=path.read_bytes();relative=str(path.relative_to(root));package=re.search(rb"\bpackage\s+([A-Za-z_$][A-Za-z0-9_$.]*)\s*;",blob)
        if not package:raise ValueError("Production source package missing")
        binary=package.group(1).decode().replace(".","/")+"/"+path.stem;covered.add(binary)
        source_rows.append({"path":str(path.relative_to(project)),"sha256":sha(blob),"bytes":len(blob)})
    filtered=[];artifact_rows=[];runtime_blockers=[]
    compileItems={str(Path(item).resolve())for item in metadata["compileClasspath"]}
    runtimeItems={str(Path(item).resolve())for item in metadata["runtimeClasspath"]}
    for item in sorted(compileItems|runtimeItems):
        path=Path(item).resolve();entries=classes(path);majors={};source_overlap=[]
        for name,blob in entries:
            if blob[:4]!=b"\xca\xfe\xba\xbe":raise ValueError("Malformed actualclass")
            major=struct.unpack(">H",blob[6:8])[0];majors[major]=majors.get(major,0)+1
            canonical=re.sub(r"^META-INF/versions/[0-9]+/","",name).removesuffix(".class").split("$",1)[0]
            if canonical in covered:source_overlap.append(name)
        engine_owned=path==project/"engine-core/build/classes/java/main" or project/"engine-core/build/classes"in path.parents
        source_tree_artifact=(project/"engine-core/src/main/java"==path or project/"engine-core/src/main/java"in path.parents)
        reason="source-covered-engine-classes"if source_overlap or engine_owned or source_tree_artifact else None
        receipt={"path":str(path),"kind":"jar"if path.is_file()else"class-directory","classes":len(entries),"classMajors":majors,"excludedReason":reason,"sourceCoveredClasses":source_overlap,"compileDependency":str(path)in compileItems,"runtimeDependency":str(path)in runtimeItems}
        if path.is_file():receipt.update({"sha256":sha(path.read_bytes()),"bytes":path.stat().st_size})
        else:receipt["classHashes"]=[{"path":name,"sha256":sha(blob)}for name,blob in entries]
        artifact_rows.append(receipt)
        if reason:continue
        if str(path)in compileItems:filtered.append(str(path))
        high=[{"class":name,"major":struct.unpack(">H",blob[6:8])[0]}for name,blob in entries if struct.unpack(">H",blob[6:8])[0]>52 and not name.startswith("META-INF/versions/")]
        if high:runtime_blockers.append({"artifact":str(path),"actualBaseClassesAbove52":high,"status":"BLOCKED-runtime-Java8; retained only as real external type oracle for modern --release8 source/API census"})
    cp=os.pathsep.join(filtered);(out/"production-source-receipt.json").write_text(json.dumps(source_rows,indent=2)+"\n");(out/"classpath-artifact-receipt.json").write_text(json.dumps({"artifacts":artifact_rows,"filteredCompilerClasspath":filtered,"runtimeBlockers":runtime_blockers},indent=2)+"\n")
    commands=[];results=[]
    def run(command,label):
        commands.append([str(x)for x in command]);(out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with(out/(label+".log")).open("wb")as log:completed=subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=False)
        return completed.returncode
    if run([a.javac8,"-version"],"javac8-version")!=0 or not(out/"javac8-version.log").read_text().strip().startswith("javac 1.8."):raise ValueError("Require real javac8")
    for name,compiler,flags in[("modern-release8",a.javac,["--release","8"]),("true-javac8",a.javac8,["-source","8","-target","8"])]:
        destination=out/(name+"-classes");destination.mkdir();arguments=out/(name+"-sources.args")
        arguments.write_text("\n".join(json.dumps(str(path))for path in sources)+"\n",encoding="utf-8")
        code=run([compiler,*flags,"-proc:none","-encoding","UTF-8","-Xmaxerrs","10000","-Xmaxwarns","10000","-classpath",cp,"-d",destination,"@"+str(arguments)],name+"-compiler")
        text=(out/(name+"-compiler.log")).read_text(errors="replace");diagnostics=[]
        for match in re.finditer(r"(?m)^(.+\.java):(\d+): (error|warning): (.*)$",text):diagnostics.append({"source":match.group(1),"line":int(match.group(2)),"kind":match.group(3),"message":match.group(4)})
        class_majors={}
        for path in destination.rglob("*.class"):
            blob=path.read_bytes();major=struct.unpack(">H",blob[6:8])[0];class_majors[major]=class_majors.get(major,0)+1
        results.append({"compilerMode":name,"exitCode":code,"diagnostics":diagnostics,"logSha256":sha((out/(name+"-compiler.log")).read_bytes()),"emittedClassMajors":class_majors,"compileAccepted":code==0})
    if source_rows!=[{"path":str(path.relative_to(project)),"sha256":sha(path.read_bytes()),"bytes":path.stat().st_size}for path in sorted(root.rglob("*.java"))]:raise ValueError("Canonical source changed during diagnostic")
    (out/"frontier.json").write_text(json.dumps({"scope":"Exact complete current canonical engine sources and actualproductiondependencies; diagnostic only","sourceCount":len(sources),"sourceReceiptSha256":sha((out/"production-source-receipt.json").read_bytes()),"classpathReceiptSha256":sha((out/"classpath-artifact-receipt.json").read_bytes()),"compilerResults":results,"externalRuntimeBlockers":runtime_blockers,"wholeEngineJava8Acceptance":False,"sourceEditsEmitted":False},indent=2)+"\n")
    print("PASS actualJava8 diagnostic captured: completeSources="+str(len(sources))+" compileExitCodes="+str([r["exitCode"]for r in results])+" externalRuntimeBlockedArtifacts="+str(len(runtime_blockers)))


if __name__=="__main__":main()
