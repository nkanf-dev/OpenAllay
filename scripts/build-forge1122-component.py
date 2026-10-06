#!/usr/bin/env python3
"""Remote-only component packaging driver; retained engine/native inputs only, no rebuild."""
import argparse,os,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--inputs',type=Path,required=True);args=parser.parse_args()
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Remote only')
    command=[str(ROOT/'gradlew'),'--max-workers=2','--stacktrace','-p',str(ROOT/'native-builds/forge1122-component'),
             '-PcomponentInputs='+str(args.inputs.resolve()),'componentCensus']
    raise SystemExit(subprocess.run(command).returncode)
if __name__=='__main__':main()
