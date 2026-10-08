#!/usr/bin/env python3
"""All-key source-format checks; no Java/game or package rebuild."""
import importlib.util,json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('legacy_language',ROOT/'scripts/legacy-language-resources.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class LegacyLanguage(unittest.TestCase):
    def test_both_canonical_languages_preserve_all_current_keys(self):
        contents={};owners={}
        for locale in module.LOCALES:
            name='assets/openallay/lang/'+locale+'.json';contents[name]=(ROOT/'common/src/main/resources'/name).read_bytes();owners[name]='native'
        original=dict(contents);proofs=module.project_legacy_languages(contents,owners)
        self.assertEqual(2,len(proofs))
        for row in proofs:
            self.assertEqual(original[row['input']],contents[row['input']]);self.assertTrue(row['allKeysRoundtripExact'])
            self.assertEqual(json.loads(original[row['input']]),dict(line.split('=',1) for line in contents[row['output']].decode().splitlines()))
    def test_utf8_equals_backslash_and_format_literals_are_not_hand_rewritten(self):
        values={'key':'中文 = text \\ %1$s','other':' leading and trailing '}
        contents={f'assets/openallay/lang/{l}.json':json.dumps(values,ensure_ascii=False).encode() for l in module.LOCALES};owners={n:'native' for n in contents}
        module.project_legacy_languages(contents,owners)
        self.assertEqual(values,dict(line.split('=',1) for line in contents['assets/openallay/lang/zh_cn.lang'].decode().splitlines()))
    def test_unrepresentable_line_controls_and_duplicate_keys_reject(self):
        for raw in (b'{"key":"first\\nsecond"}',b'{"key":"a","key":"b"}',b'{"#comment":"x"}',b'{"key":1}'):
            contents={f'assets/openallay/lang/{l}.json':raw for l in module.LOCALES};owners={n:'native' for n in contents}
            with self.assertRaises(ValueError):module.project_legacy_languages(contents,owners)
if __name__=='__main__':unittest.main()
