#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

spec = importlib.util.spec_from_file_location('census', Path(__file__).with_name('collect-forge1122-native-census.py'))
census = importlib.util.module_from_spec(spec)
spec.loader.exec_module(census)

runner_spec = importlib.util.spec_from_file_location('runner', Path(__file__).with_name('run-forge1122-native-census.py'))
runner = importlib.util.module_from_spec(runner_spec)
runner_spec.loader.exec_module(runner)

class NativeCensusTests(unittest.TestCase):
    def test_exact_mcp_csv_join_not_mojang(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            config=root/'config.zip'; snapshot=root/'snapshot.zip'
            with zipfile.ZipFile(config,'w') as z:
                z.writestr('config.json',json.dumps({'data':{'mappings':'joined.tsrg'}}))
                z.writestr('joined.tsrg','a net/minecraft/client/Minecraft\n\ta ()V func_1_a\n\tb field_1_b\n')
            with zipfile.ZipFile(snapshot,'w') as z:
                z.writestr('methods.csv','searge,name,side,desc\nfunc_1_a,exactMethod,2,\n')
                z.writestr('fields.csv','searge,name,side,desc\nfield_1_b,exactField,2,\n')
            result=census.mapping_inventory(config,snapshot)
            self.assertFalse(result['officialMojangMappings'])
            self.assertEqual(['exactMethod','exactField'],[r['mcpMember'] for r in result['members']])
            self.assertEqual('()V',result['members'][0]['obfuscatedDescriptor'])
    def test_bytecode_excerpts_exact_selected_methods(self):
        text='public class Example {\n  public void first();\n    Code:\n       0: return\n\n  private void other();\n    Code:\n       0: return\n}\n'
        result,missing=census.method_excerpt(text,('first','absent'))
        self.assertIn('first()',result)
        self.assertNotIn('other()',result)
        self.assertEqual(['absent'],missing)
    def test_modified_source_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            source=Path(tmp)/'Source.java'; source.write_text('class Source {}')
            with self.assertRaises(ValueError):
                census.verified({'path':str(source),'sha256':'0'*64})

    def test_downloader_records_403_and_public_user_agent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); work=root/'work'; output=root/'output'
            work.mkdir(); output.mkdir()
            url='https://maven.minecraftforge.net/exact-mdk.zip'
            def reject(request, timeout):
                self.assertEqual(url,request.full_url)
                self.assertEqual('OpenAllay-CI-Runtime',request.get_header('User-agent'))
                raise urllib.error.HTTPError(url,403,'Forbidden',{},None)
            record={'url':url,'sha256':'0'*64,'filename':'exact-mdk.zip'}
            with patch.object(runner.urllib.request,'urlopen',side_effect=reject):
                with self.assertRaises(urllib.error.HTTPError):
                    runner.download_pin('mdk',record,work,output)
            receipt=json.loads((output/'download-mdk.json').read_text())
            self.assertEqual('failed',receipt['status'])
            self.assertEqual(403,receipt['httpStatus'])
            self.assertEqual(url,receipt['rejectedUrl'])
            self.assertEqual('0'*64,receipt['expectedSha256'])
            self.assertFalse((work/'exact-mdk.zip').exists())

    def test_downloader_never_accepts_wrong_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); work=root/'work'; output=root/'output'
            work.mkdir(); output.mkdir()
            class Response:
                status=200
                def __enter__(self): return self
                def __exit__(self,*args): return False
                def geturl(self): return 'https://maven.minecraftforge.net/exact.zip'
                def read(self,limit): return b'wrong content'
            record={'url':'https://maven.minecraftforge.net/exact.zip','sha256':'0'*64,'filename':'exact.zip'}
            with patch.object(runner.urllib.request,'urlopen',return_value=Response()):
                with self.assertRaises(ValueError): runner.download_pin('mdk',record,work,output)
            self.assertFalse((work/'exact.zip').exists())
            self.assertEqual('failed',json.loads((output/'download-mdk.json').read_text())['status'])

if __name__=='__main__':
    unittest.main()
