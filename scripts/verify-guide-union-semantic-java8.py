#!/usr/bin/env python3
"""Closed actual semantic Java8 union frontier and original modern values; no domain substitutes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess


def sha(blob):return hashlib.sha256(blob).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--project",type=Path,required=True);p.add_argument("--source-packet",type=Path,required=True);p.add_argument("--javac",type=Path,required=True);p.add_argument("--java",type=Path,required=True);p.add_argument("--javac8",type=Path,required=True);p.add_argument("--java8",type=Path,required=True);p.add_argument("--classpath",required=True,help="Actual approvedexternal Gson+acceptedCommonmark only, neverengine classes");p.add_argument("--output",type=Path,required=True)
    a=p.parse_args();project=a.project.resolve();packet=a.source_packet.resolve();out=a.output.resolve()
    if out==project or project in out.parents:raise ValueError("Fresh externaloutput required")
    out.mkdir(parents=True,exist_ok=False);contract_path=project/"engine-core/src/guideUnionAdmissionTest/source-contract.json";contract=json.loads(contract_path.read_text());manifest=json.loads((packet/"manifest.json").read_text())
    if manifest["sourceContractSha256"]!=sha(contract_path.read_bytes()) or manifest["files"]!=contract["owners"]:raise ValueError("Actual repository stage manifest contract changed")
    if sha(Path(__file__).read_bytes())!=contract["semanticRunner"]["sha256"]:raise ValueError("Repository semantic runner pin changed")
    pins={r["path"]:r for r in manifest["files"]};commands=[];receipt=[]
    for row in contract["semanticSourcePaths"]:
        if row in pins:
            for side in ["pre","post"]:
                blob=(packet/side/row).read_bytes()
                if sha(blob)!=pins[row][side+"_sha256"]:raise ValueError("Unionowner custody changed")
        else:
            blob=(project/row).read_bytes()
            if sha(blob)!=contract["semanticSharedSourcePins"][row]:raise ValueError("Actualsemanticsupport source changed")
    for row,pin in contract["fixturePins"].items():
        if sha((project/row).read_bytes())!=pin:raise ValueError("Actualfixture source changed")
    paths=[Path(path) for path in a.classpath.split(os.pathsep)]
    for path in paths:
        if not path.is_file():raise ValueError("Actualexternal approvedJAR only")
        import zipfile
        with zipfile.ZipFile(path)as archive:
            classes=[name for name in archive.namelist()if name.endswith(".class")]
            if any(name.startswith("dev/openallay/") for name in classes):raise ValueError("No engine/core precompiledclass dependency")
        blob=path.read_bytes();gsonMatches=[pin for pin in contract["gsonPins"] if len(blob)==pin["bytes"] and hashlib.new(pin["digest_algorithm"],blob).hexdigest()==pin["digest"]]
        if gsonMatches:
            kind="approved-Gson"
        elif classes and all(name.startswith("org/commonmark/") or name.startswith("META-INF/") for name in classes):
            if not any(name.startswith("org/commonmark/parser/") for name in classes) or not any(name.startswith("org/commonmark/ext/gfm/tables/") for name in classes):raise ValueError("Complete accepted CommonMark core/tables artifact required")
            with zipfile.ZipFile(path)as archive:
                majors={struct.unpack(">H",archive.read(name)[6:8])[0]for name in classes}
            if majors!={52}:raise ValueError("CommonMark class artifact not genuine Java8")
            kind="actual-CommonMark-core-and-tables"
        else:raise ValueError("Unapproved external semantic dependency artifact")
        receipt.append({"externalClasspath":str(path),"kind":kind,"sha256":sha(blob),"bytes":len(blob)})
    if len(paths)!=2 or {row["kind"]for row in receipt}!={"approved-Gson","actual-CommonMark-core-and-tables"}:raise ValueError("Require exact Gson + complete actual CommonMark artifact")
    def run(command,label):
        commands.append([str(x)for x in command]);(out/"commands.json").write_text(json.dumps(commands,indent=2)+"\n")
        with(out/(label+".log")).open("wb")as log:subprocess.run(commands[-1],cwd=project,stdout=log,stderr=subprocess.STDOUT,check=True)
    run([a.javac8,"-version"],"javac8-version");run([a.java8,"-version"],"java8-version")
    if not(out/"javac8-version.log").read_text().strip().startswith("javac 1.8.")or 'version "1.8.'not in(out/"java8-version.log").read_text():raise ValueError("ActualJava8 compiler/runtime required")
    reports=[]
    known=project/"engine-core/src/guideUnionAdmissionTest/java/dev/openallay/guide/semantic/SemanticUnionKnownFixture.java";negative=project/"engine-core/src/guideUnionAdmissionTest/java/dev/openallay/guide/semantic/SemanticUnionJava8Fixture.java"
    for mode,compiler,flags,runtime in[("original17",a.javac,["--release","17"],a.java),("candidate-release8",a.javac,["--release","8"],a.java8),("candidate-true8",a.javac8,["-source","8","-target","8"],a.java8)]:
        sources=[(packet/("pre"if mode=="original17"else"post")/row if row in pins else project/row)for row in contract["semanticSourcePaths"]];classes=out/(mode+"-classes");classes.mkdir();fixtures=[known]+([]if mode=="original17"else[negative])
        run([compiler,*flags,"-encoding","UTF-8","-proc:none","-classpath",a.classpath,"-d",classes,*sources,*fixtures],mode+"-compile")
        majors={}
        for path in classes.rglob("*.class"):
            blob=path.read_bytes();major=struct.unpack(">H",blob[6:8])[0];majors[major]=majors.get(major,0)+1
        if set(majors)!={61 if mode=="original17" else 52}:raise ValueError("Wrongemittedclassmajor")
        cp=str(classes)+os.pathsep+a.classpath;run([runtime,"-classpath",cp,"dev.openallay.guide.semantic.SemanticUnionKnownFixture"],mode+"-known")
        reports.append((out/(mode+"-known.log")).read_bytes())
        if mode!="original17":run([runtime,"-classpath",cp,"dev.openallay.guide.semantic.SemanticUnionJava8Fixture"],mode+"-foreign")
        receipt.append({"mode":mode,"sourceCount":len(sources),"classMajors":majors,"knownReportSha256":sha(reports[-1]),"negativeRan":mode!="original17"})
    if reports[0]!=reports[1]or reports[0]!=reports[2]:raise ValueError("Originalsemantic values differ")
    (out/"receipt.json").write_text(json.dumps({"semanticClosedActualSourceCount":len(contract["semanticSourcePaths"]),"scope":"Actualsemantic35source known/foreign families only; whole10Guide8 remainspending actualfullfrontier compile","receipts":receipt,"wholeGuideJava8Acceptance":False},indent=2)+"\n")
    print("PASS actualclosedsemantic35source Java8 unionadmission andoriginalknownvalues")


if __name__=="__main__":main()
