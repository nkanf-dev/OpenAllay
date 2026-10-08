#!/usr/bin/env python3
"""Resolve one frozen original group and run only the resource package command."""
import argparse
from pathlib import Path
import subprocess
from package_canonical_builder import ROOT, originals

p=argparse.ArgumentParser(description=__doc__);p.add_argument('--target',required=True);a=p.parse_args()
group=originals(ROOT)[a.target]
subprocess.run(['python3','-B',str(ROOT/'scripts/package_canonical_builder.py'),'--target',a.target,
    '--original-directory',str(ROOT/'build/builder-package-original-cache'/group['artifactName']),
    '--output',str(ROOT/'release-group')],check=True)
