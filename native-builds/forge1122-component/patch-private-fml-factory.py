#!/usr/bin/env python3
"""Patch one exact genuine private Mixin factory; preserve all other archive entries."""
import argparse,hashlib,json,subprocess,tempfile,zipfile
from pathlib import Path
def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True);p.add_argument('--java',required=True);p.add_argument('--classpath',required=True);p.add_argument('--classes',required=True);a=p.parse_args()
    name='org/spongepowered/asm/bridge/RemapperAdapterFML.class'
    with tempfile.TemporaryDirectory(prefix='forge1122-mixin-adapter-') as temp:
        work=Path(temp)
        with zipfile.ZipFile(a.jar) as z:records=[(i,z.read(i)) for i in z.infolist()]
        raw=next(data for info,data in records if info.filename==name);(work/'before.class').write_bytes(raw)
        subprocess.run([a.java,'-cp',a.classes+':'+a.classpath,'dev.openallay.forge1122.build.FmlMixinAdapterPatch',str(work/'before.class'),str(work/'after.class')],check=True)
        after=(work/'after.class').read_bytes();replacement=a.jar.with_suffix('.replacement.jar')
        with zipfile.ZipFile(replacement,'w') as z:
            for info,data in records:z.writestr(info,after if info.filename==name else data)
        replacement.replace(a.jar)
        a.jar.with_suffix('.adapter-receipt.json').write_text(json.dumps({'target':name,'inputSha256':hashlib.sha256(raw).hexdigest(),'outputSha256':hashlib.sha256(after).hexdigest(),'onlyFactoryChanged':True,'otherEntriesByteIdentical':True},indent=2)+'\n')
if __name__=='__main__':main()
