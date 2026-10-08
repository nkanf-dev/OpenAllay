#!/usr/bin/env python3
"""Focused Forge16 package tests. No downloads, Java compilation, Gradle or game."""
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

P = Path(__file__).with_name('package-legacy-forge-release.py')
spec = importlib.util.spec_from_file_location('legacy', P)
legacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(legacy)

def jar(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as z:
        for name, data in entries.items(): z.writestr(name, data)
    return out.getvalue()

class PlayerPacketTests(unittest.TestCase):
    def test_duplicate_and_traversal_reject(self):
        for name in ['../outside', '/absolute', 'a\\b', 'a/../b']:
            with self.assertRaises(ValueError): legacy.archive(jar({name: b'x'}))
        with self.assertRaises(ValueError): legacy.archive(jar({'A': b'x', 'a': b'y'}))

    def test_metadata_is_actual_not_filename(self):
        bad = jar({'META-INF/mods.toml': b'[[mods]]\nmodId="openallay"\nversion="0.4.3"\n'})
        with self.assertRaises(ValueError): legacy.mod_version(bad, 'forge16165', '0.4.4')
        good = jar({'META-INF/mods.toml': b'[[mods]]\nmodId="openallay"\nversion="0.4.4"\n'})
        legacy.mod_version(good, 'forge16165', '0.4.4')


    def test_class_entries_cannot_be_silently_rewritten(self):
        raw = jar({'A.class': b'\xca\xfe\xba\xbe\0\0\0\x34rest'})
        inv = legacy.inventory(raw)
        self.assertEqual(inv['A.class']['major'], 52)
        with self.assertRaises(ValueError): legacy.check_inventory(raw, {'A.class': {'sha256': '0'*64}})

    def test_remote_only_admission(self):
        with self.assertRaises(ValueError): legacy.require_remote({})
        legacy.require_remote({'GITHUB_ACTIONS': 'true'})


    def test_new_file_refuses_existing_and_symlink(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / 'file'
            legacy.write_new(p, b'first')
            with self.assertRaises(FileExistsError): legacy.write_new(p, b'next')
            self.assertEqual(p.read_bytes(), b'first')
            symlink = Path(td) / 'link'; symlink.symlink_to(p)
            with self.assertRaises(FileExistsError): legacy.write_new(symlink, b'next')


    def test_actual_central_family_dictionary(self):
        root = Path(__file__).resolve().parents[1]
        data = json.loads((root/'gradle/minecraft-artifacts.json').read_text())
        family = next(f for f in data['acceptedFamilies'] if f['id']=='forge-1.16.5')
        self.assertEqual(legacy.validate_family(family,'forge16165',root),'forge-1.16.5')
        for changes in [{'artifactKind':'zip'},{'buildTarget':'1.12.2'},
                        {'publicationChannels':['github']},{'packagingRecipe':'nested-mod'}]:
            with self.assertRaises(ValueError):legacy.validate_family({**family,**changes},'forge16165',root)
        self.assertEqual(legacy.validate_family('forge16165','forge16165',root),'forge-1.16.5')
        with self.assertRaises(ValueError):legacy.validate_family('forge1122','forge1122',root)

    def test_obsolete_player_installer_and_premain_helpers_are_removed(self):
        for name in ('INSTALLER','PUBLIC_AGENT','SOURCES','compile_helpers','player_profile'):
            self.assertFalse(hasattr(legacy,name),name)

    def test_forge16_verifier_preserves_provider_provenance_and_entry_custody(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as td:
            path = Path(td)/'product.jar'
            raw = jar({'META-INF/mods.toml':b'[[mods]]\nmodId="openallay"\nversion="0.4.4"\n',
                       'Own.class':b'\xca\xfe\xba\xbe\x00\x00\x00\x3dmore'})
            path.write_bytes(raw)
            provider = {'sqlite':{'artifactSha256':'a'*64,'payloadSha256':'b'*64},
                        'sharedRuntimes':{'extension-api':'c'*64,'runtime-rhino':'d'*64}}
            receipt = {'target':'forge16165','version':'0.4.4','outputSha256':legacy.sha(raw),
                       'entries':legacy.inventory(raw),'helperBuild':None,'provider':provider}
            sidecar = Path(str(path)+'.packaging.json')
            sidecar.write_bytes(legacy.encoded(receipt))
            root = Path(__file__).resolve().parents[1]
            with patch.object(legacy,'provider_check') as check_provider, \
                 patch.object(legacy,'provenance_check') as check_provenance:
                result = legacy.verify_release(path,'forge16165','0.4.4',root)
                self.assertEqual(result['coreBytes'],raw)
                check_provider.assert_called_once_with(provider,'0.4.4',{'product':raw})
                check_provenance.assert_called_once_with(provider,root,'0.4.4')
            receipt['helperBuild'] = {'obsolete':'agent'}
            sidecar.write_bytes(legacy.encoded(receipt))
            with self.assertRaisesRegex(ValueError,'no player helper'):
                legacy.verify_release(path,'forge16165','0.4.4',root)
            receipt['helperBuild'] = None
            receipt['entries']['Own.class']['sha256'] = '0'*64
            sidecar.write_bytes(legacy.encoded(receipt))
            with self.assertRaisesRegex(ValueError,'custody differs'):
                legacy.verify_release(path,'forge16165','0.4.4',root)

if __name__ == '__main__': unittest.main()
