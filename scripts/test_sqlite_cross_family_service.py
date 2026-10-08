"""Exact two JDBC service byte forms; immutable receipt payload hashes stay separate."""
from importlib.util import module_from_spec,spec_from_file_location
from pathlib import Path
import io,tempfile,unittest,zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('sqlite_cross',ROOT/'scripts/build-minecraft-artifacts.py');m=module_from_spec(spec);spec.loader.exec_module(m)

class SqliteCrossFamilyTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  self.entries={'org/sqlite/entry'+str(i):('exact'+str(i)).encode() for i in range(149)}
 def product(self,name,service,nested=False,mutate=None):
  entries=dict(self.entries);entries['META-INF/services/java.sql.Driver']=service
  if mutate:mutate(entries)
  path=self.root/name
  if nested:
   raw=io.BytesIO()
   with zipfile.ZipFile(raw,'w') as z:
    for key,data in entries.items():z.writestr(key,data)
   with zipfile.ZipFile(path,'w') as z:z.writestr('META-INF/jars/sqlite-jdbc-3.50.3.0.jar',raw.getvalue())
  else:
   with zipfile.ZipFile(path,'w') as z:
    for key,data in entries.items():z.writestr(key,data)
  return path
 def test_exact_no_newline_and_single_lf_crosscompare_equal(self):
  raw=self.product('nested.jar',b'org.sqlite.JDBC',True);flat=self.product('flat.jar',b'org.sqlite.JDBC\n')
  self.assertEqual(m.sqlite_cross_family_payload(raw,{'packagingRecipe':'nested-mod'}),
                   m.sqlite_cross_family_payload(flat,{'packagingRecipe':'forge-stock8'}))
 def test_any_other_whitespace_provider_registration_rejected(self):
  for i,service in enumerate((b'org.sqlite.JDBC\r\n',b' org.sqlite.JDBC',b'org.sqlite.JDBC\n\n',b'org.sqlite.JDBC\nother.Driver',b'other.Driver',b'')):
   path=self.product('bad'+str(i)+'.jar',service)
   with self.assertRaises(ValueError):m.sqlite_cross_family_payload(path,{'packagingRecipe':'forge-stock8'})
 def test_changed_provider_bytes_or_native_path_fail_cross_equality(self):
  base=self.product('good.jar',b'org.sqlite.JDBC')
  changed=self.product('changed.jar',b'org.sqlite.JDBC',mutate=lambda e:e.update({'org/sqlite/entry0':b'changed native'}))
  self.assertNotEqual(m.sqlite_cross_family_payload(base,{'packagingRecipe':'forge-flat'}),m.sqlite_cross_family_payload(changed,{'packagingRecipe':'forge-flat'}))
  extra=self.product('extra.jar',b'org.sqlite.JDBC',mutate=lambda e:e.update({'org/sqlite/extra':b'not admitted'}))
  with self.assertRaises(ValueError):m.sqlite_cross_family_payload(extra,{'packagingRecipe':'forge-flat'})
 def test_original_receipt_hash_check_not_rewritten(self):
  text=(ROOT/'scripts/build-minecraft-artifacts.py').read_text()
  self.assertIn('require(sqlite == receipt["sqlite"]',text)
  self.assertIn('"SQLite bytes changed after package checks"',text)
  self.assertIn('"73a3c5413e82e8d3ffceea4b78690e9539b8e64d82764696dff52b8efbd81839"',text)

if __name__=='__main__':unittest.main()
