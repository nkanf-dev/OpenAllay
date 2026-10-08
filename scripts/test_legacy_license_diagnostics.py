"""Exact legal-resource custody and bounded failure evidence; no JVM/network."""
from importlib.util import module_from_spec,spec_from_file_location
from pathlib import Path
import io
import json
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def load(name,file):
 spec=spec_from_file_location(name,ROOT/'scripts'/file);m=module_from_spec(spec);spec.loader.exec_module(m);return m

b=load('license_build','build-minecraft-artifacts.py');p=load('diagnostics_prep','prepare-legacy-forge-release.py')

class LicenseDiagnosticsTest(unittest.TestCase):
 def archive(self,entries):
  raw=io.BytesIO()
  with zipfile.ZipFile(raw,'w') as z:
   for name,data in entries.items():z.writestr(name,data)
  return zipfile.ZipFile(io.BytesIO(raw.getvalue()))
 def test_exact_stock8_license_alias_rehashes_original_expected_content(self):
  source='data/openallay/models/LICENSE.models.dev';target='META-INF/licenses/engine/'+source;raw=b'exact legal'
  class Owner:
   @staticmethod
   def engine_legal_owners(path):return {source:{"path":target,"sha256":b.hashlib.sha256(raw).hexdigest()}}
  with self.archive({target:raw}) as z,patch.object(b,'module',return_value=Owner):
   self.assertEqual(b.engine_entry(z,{'packagingRecipe':'forge-stock8'},source,Path('product.jar')),raw)
  with self.archive({source:raw,target:raw}) as z,patch.object(b,'module',return_value=Owner),self.assertRaises(ValueError):
   b.engine_entry(z,{'packagingRecipe':'forge-stock8'},source,Path('product.jar'))
  with self.archive({target:b'changed license'}) as z,patch.object(b,'module',return_value=Owner),self.assertRaises(ValueError):
   b.engine_entry(z,{'packagingRecipe':'forge-stock8'},source,Path('product.jar'))
 def test_no_class_or_other_resource_alias(self):
  for recipe,name in [('forge-stock8','Own.class'),('forge-stock8','data/other/LICENSE'),('nested-mod','data/openallay/models/LICENSE.models.dev')]:
   with self.archive({'META-INF/licenses/engine/'+name:b'value'}) as z,self.assertRaises(KeyError):
    b.engine_entry(z,{'packagingRecipe':recipe},name,Path('product.jar'))
 def test_failed_command_prints_bounded_original_log_before_error(self):
  with tempfile.TemporaryDirectory() as temp:
   log=Path(temp)/'native-build.log'
   class Result:returncode=1
   def run(command,**kwargs):kwargs['stdout'].write(b'genuine compiler error');return Result()
   with patch.object(p.subprocess,'run',side_effect=run),patch.object(p.sys,'stderr',new=io.StringIO()) as stderr,self.assertRaises(ValueError):
    p.execute(['native-compiler'],log)
   self.assertIn('genuine compiler error',stderr.getvalue());self.assertEqual(log.read_bytes(),b'genuine compiler error')
 def test_failed_log_tail_is_bounded_and_original_file_preserved(self):
  with tempfile.TemporaryDirectory() as temp:
   log=Path(temp)/'native-build.log';raw=('first unique line\n'+'compiler detail\n'*10000+'final actual failure\n').encode()
   class Result:returncode=9
   def run(command,**kwargs):kwargs['stdout'].write(raw);return Result()
   with patch.object(p.subprocess,'run',side_effect=run),patch.object(p.sys,'stderr',new=io.StringIO()) as stderr,self.assertRaises(ValueError):
    p.execute(['native-compiler'],log)
   self.assertEqual(log.read_bytes(),raw)
   self.assertLessEqual(len(stderr.getvalue().encode()),64*1024+1)
   self.assertLessEqual(len(stderr.getvalue().splitlines()),160)
   self.assertIn('final actual failure',stderr.getvalue());self.assertNotIn('first unique line',stderr.getvalue())
 def test_workflow_captures_only_known_small_legacy_files(self):
  workflow=(ROOT/'.github/workflows/minecraft-native.yml').read_text();block=workflow.split('name: Preserve compiler diagnostics',1)[1].split('  stage:',1)[0]
  for name in ['canonical.log','native-inputs.log','native-build.log','package.log','native-request.json','compiler-lock.json']:
   self.assertIn('build/legacy-release-inputs/*/'+name,block)
  self.assertNotIn('build/legacy-release-inputs/\n',block)
  self.assertNotIn('build/e2e',block);self.assertNotIn('*.jar',block)

if __name__=='__main__':unittest.main()
