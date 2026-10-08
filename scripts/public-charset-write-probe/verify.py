#!/usr/bin/env python3
"""One cheap native-modern public Charset probe, no Gradle or Java8 acceptance."""
import argparse,hashlib,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument("--source-root",type=Path,required=True);p.add_argument("--jdk17",type=Path,required=True);p.add_argument("--output",type=Path,required=True);a=p.parse_args();root=a.source_root.resolve();contract=json.loads(Path(__file__).with_name("source-contract.json").read_bytes());sha=lambda b:hashlib.sha256(b).hexdigest();assert sha(Path(__file__).read_bytes())==contract["runner_sha256"];source=root/contract["source_path"];assert sha(source.read_bytes())==contract["source_sha256"];a.output.mkdir(parents=True,exist_ok=False);classes=a.output/"classes";classes.mkdir()
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode(errors="replace"))
 return r
r=run([a.jdk17/"bin/javac","--release","17","-d",classes,source]);(a.output/"compile.log").write_bytes(r.stdout+r.stderr)
r=run([a.jdk17/"bin/java","-cp",classes,"dev.openallay.build.PublicCharsetWriteProbe",a.output/"destinations"]);facts=json.loads(r.stdout);assert len(facts["rows"])==20;(a.output/"native-public-charset-facts.json").write_bytes(r.stdout);print(r.stdout.decode())
