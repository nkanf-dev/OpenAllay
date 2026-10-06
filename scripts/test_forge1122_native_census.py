#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('census', Path(__file__).with_name('collect-forge1122-native-census.py'))
census = importlib.util.module_from_spec(spec)
spec.loader.exec_module(census)

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

if __name__=='__main__':
    unittest.main()
