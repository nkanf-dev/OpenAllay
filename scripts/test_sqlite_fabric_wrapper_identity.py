"""Fabric SQLite exact generated wrapper must preserve the complete original closure."""
from importlib.util import spec_from_file_location,module_from_spec
from pathlib import Path
import tempfile,unittest,zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('fabric_sqlite',ROOT/'scripts/build-minecraft-artifacts.py');m=module_from_spec(spec);spec.loader.exec_module(m)

class FabricSqliteIdentityTest(unittest.TestCase):
 def test_fabric_cannot_claim_raw_archive_or_unknown_wrapper(self):
  family={'loader':'fabric','packagingRecipe':'nested-mod'}
  for value in ('a3f53a2aa15ae9425a9e793bbe9c8e5288febeb4b65ef5c1a4e80d4c2045cf08','e'*64):
   with self.assertRaises(ValueError):m.verify_sqlite_archive_identity(Path('unused.jar'),family,{'artifactSha256':value})
 def test_other_loaders_require_original_raw_identity(self):
  raw='a3f53a2aa15ae9425a9e793bbe9c8e5288febeb4b65ef5c1a4e80d4c2045cf08'
  m.verify_sqlite_archive_identity(Path('unused.jar'),{'loader':'neoforge'},{'artifactSha256':raw})
  with self.assertRaises(ValueError):m.verify_sqlite_archive_identity(Path('unused.jar'),{'loader':'neoforge'},{'artifactSha256':'0bc822a176492a4d3e2547b13a54bdf8540ea4e3a8ede8edc2d02f9a94c3c12a'})
 def test_fabric_wrapper_actual_bytes_guard_is_failclosed(self):
  with tempfile.TemporaryDirectory() as temp:
   path=Path(temp)/'product.jar'
   with zipfile.ZipFile(path,'w') as z:z.writestr('META-INF/jars/sqlite-jdbc-3.50.3.0.jar',b'changed bytes')
   with self.assertRaises(ValueError):m.verify_sqlite_archive_identity(path,{'loader':'fabric','packagingRecipe':'nested-mod'},
      {'artifactSha256':'0bc822a176492a4d3e2547b13a54bdf8540ea4e3a8ede8edc2d02f9a94c3c12a'})
 def test_all_original_entries_not_only_runtime_prefix_are_authenticated(self):
  text=(ROOT/'scripts/build-minecraft-artifacts.py').read_text();code=text.split('def verify_sqlite_archive_identity',1)[1].split('def sqlite_cross_family_payload',1)[0]
  self.assertIn('len(payload)==162',code);self.assertIn('name!="fabric.mod.json"',code)
  self.assertIn('org_xerial_sqlite-jdbc',code)
  self.assertNotIn('strip(',code)

if __name__=='__main__':unittest.main()
