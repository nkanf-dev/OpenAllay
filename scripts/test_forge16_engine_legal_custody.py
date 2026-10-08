"""Finite Forge16 legal ownership checks; executable parity remains strict."""
from copy import deepcopy
from importlib.util import spec_from_file_location,module_from_spec
from pathlib import Path
import io,json,tempfile,unittest,zipfile
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
def load(name,file):
 spec=spec_from_file_location(name,ROOT/'scripts'/file);m=module_from_spec(spec);spec.loader.exec_module(m);return m
p=load('legal_packet','package-legacy-forge-release.py');b=load('legal_build','build-minecraft-artifacts.py')

class LegalOwnerTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.path=Path(self.tmp.name)/'product.jar'
  self.source='META-INF/licenses/jtokkit-MIT.txt';self.target='META-INF/licenses/engine/'+self.source;self.raw=b'real legal bytes';self.sha=p.sha(self.raw)
  entry={'sha256':self.sha,'bytes':len(self.raw),'major':None}
  self.receipt={'entries':{self.target:entry},'provider':{'components':{'product':{'entries':{self.target:entry}}},
    'provenance':{'engine':{'engineEntries':{self.source:self.sha}},'custody':{'engineCustody':{'replaced':[
      {'role':'engine','input':self.source,'output':self.target,'sha256':self.sha}]}}}}}
  self.side=Path(str(self.path)+'.packaging.json');self.write()
 def write(self):self.side.write_text(json.dumps(self.receipt))
 def test_exact_current_legal_owner_and_bytes_pass(self):
  self.assertEqual(len(p.FORGE16_ENGINE_LEGAL_INPUTS),25)
  self.assertEqual(p.engine_legal_owner(self.path,self.source),(self.target,self.sha))
  raw=io.BytesIO()
  with zipfile.ZipFile(raw,'w') as z:z.writestr(self.target,self.raw)
  with zipfile.ZipFile(io.BytesIO(raw.getvalue())) as z,patch.object(b,'module',return_value=p):
   self.assertEqual(b.engine_entry(z,{'packagingRecipe':'forge-flat'},self.source,self.path),self.raw)
 def test_wrong_role_path_hash_class_major_or_duplicate_custody_reject(self):
  original=deepcopy(self.receipt)
  for mutate in (lambda r:r['provider']['provenance']['custody']['engineCustody']['replaced'][0].update(role='native'),
                 lambda r:r['provider']['provenance']['custody']['engineCustody']['replaced'][0].update(output='unknown'),
                 lambda r:r['provider']['provenance']['custody']['engineCustody']['replaced'][0].update(sha256='e'*64),
                 lambda r:r['provider']['components']['product']['entries'][self.target].update(major=52),
                 lambda r:r['provider']['provenance']['custody']['engineCustody']['replaced'].append(r['provider']['provenance']['custody']['engineCustody']['replaced'][0])):
   self.receipt=deepcopy(original);mutate(self.receipt);self.write()
   with self.assertRaises(ValueError):p.engine_legal_owner(self.path,self.source)
 def test_unknown_class_jar_or_resource_not_aliased(self):
  for name in ('Own.class','Notice.class','dependency.jar','assets/unreviewed/resource.json'):
   with self.assertRaises(ValueError):p.engine_legal_owner(self.path,name)
   raw=io.BytesIO()
   with zipfile.ZipFile(raw,'w') as z:z.writestr('META-INF/licenses/engine/'+name,b'not admitted')
   with zipfile.ZipFile(io.BytesIO(raw.getvalue())) as z,patch.object(b,'module',return_value=p),self.assertRaises(KeyError):
    b.engine_entry(z,{'packagingRecipe':'forge-flat'},name,self.path)
 def test_duplicate_original_and_relocated_legal_resource_reject(self):
  raw=io.BytesIO()
  with zipfile.ZipFile(raw,'w') as z:z.writestr(self.source,self.raw);z.writestr(self.target,self.raw)
  with zipfile.ZipFile(io.BytesIO(raw.getvalue())) as z,patch.object(b,'module',return_value=p),self.assertRaises(ValueError):
   b.engine_entry(z,{'packagingRecipe':'forge-flat'},self.source,self.path)
 def test_core_class_digest_check_stays_required(self):
  source=(ROOT/'scripts/build-minecraft-artifacts.py').read_text()
  self.assertIn('hashlib.sha256(engine_entry(archive, family, name, path)).hexdigest() == digest',source)
  self.assertIn('"Shared engine was changed or omitted: " + name',source)

if __name__=='__main__':unittest.main()
