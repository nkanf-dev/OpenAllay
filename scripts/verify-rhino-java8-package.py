#!/usr/bin/env python3
"""Verify ordinary canonical Rhino production/source archives without generated evidence copies."""
import hashlib,json,sys,zipfile
from pathlib import Path
root=Path(sys.argv[1]);rows=[]
for path in sorted((root/"runtime-rhino/build/libs").glob("*.jar")):
    with zipfile.ZipFile(path) as z:
        classes=[n for n in z.namelist() if n.endswith(".class")]
        if classes:
            majors={int.from_bytes(z.read(n)[6:8],"big") for n in classes}
            assert majors=={52},(str(path),majors)
            assert z.read("META-INF/MANIFEST.MF").decode().find("OpenAllay Rhino shared runtime")>=0
            assert "META-INF/licenses/rhino/LICENSE-MPL-2.0.txt" in z.namelist()
            assert any(n.startswith("dev/latvian/mods/rhino/resources/") for n in z.namelist())
        else:
            sources=[n for n in z.namelist() if n.endswith(".java")]
            assert len(sources)==277,(str(path),len(sources))
            assert "LICENSE-MPL-2.0.txt" in z.namelist()
        rows.append({"file":path.name,"bytes":path.stat().st_size,"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"productionClassCount":len(classes),"productionClassMajors":[52] if classes else []})
assert len(rows)==2,rows
out=root/"runtime-rhino/build/java8-package-receipt.json";out.write_text(json.dumps({"scope":"Ordinary canonical Rhino runtime/source archive class ABI and legal resources","archives":rows,"fullForgeAcceptance":False},indent=2)+"\n")
print("PASS ordinary Rhino Java8 runtime/source package")
