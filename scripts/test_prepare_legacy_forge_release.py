#!/usr/bin/env python3
"""Small source/input custody tests. No Gradle, games or downloads."""
import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

sp=importlib.util.spec_from_file_location('legacy_prep',Path(__file__).with_name('prepare-legacy-forge-release.py'))
m=importlib.util.module_from_spec(sp);sp.loader.exec_module(m)

class LegacyInputTest(unittest.TestCase):
    def test_exact_provider_identity_and_original_failed_run(self):
        pin=m.BASE
        meta={'id':pin['artifactId'],'expired':False,'digest':'sha256:'+pin['sha256'],
            'workflow_run':{'id':pin['runId'],'head_sha':pin['sourceRevision']}}
        run={'id':pin['runId'],'head_sha':pin['sourceRevision'],'repository':{'id':42},
            'head_repository':{'id':42},'path':'.github/workflows/minecraft-native.yml',
            'event':'workflow_dispatch','conclusion':'failure'}
        m.provider_matches(meta,pin,run,42)
        for field,value in [('expired',True),('digest','sha256:'+'0'*64),('id',1)]:
            with self.assertRaises(ValueError):m.provider_matches({**meta,field:value},pin,run,42)
        with self.assertRaises(ValueError):m.provider_matches(meta,pin,{**run,'head_repository':{'id':7}},42)
    def test_actual_repository_endpoint_and_full_history_checkout(self):
        from unittest.mock import patch
        import inspect
        source=inspect.getsource(m.retained)
        self.assertNotIn("'repos/'+repo+'/'+endpoint",source)
        self.assertIn("'/'+endpoint if endpoint else ''",source)
        root=Path(__file__).resolve().parents[1]
        workflow=(root/'.github/workflows/minecraft-native.yml').read_text()
        build=workflow.split('  build-packages:',1)[1].split('  stage:',1)[0]
        checkout=build.split('uses: actions/checkout@',1)[1].split('uses: actions/setup-java@',1)[0]
        self.assertIn('fetch-depth: 0',checkout)
        self.assertIn('persist-credentials: false',checkout)

    def test_source_version_never_ambient_or_renamed(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);(root/'gradle.properties').write_text('version=0.4.4\n')
            self.assertEqual(m.source_version(root),'0.4.4')
            (root/'gradle.properties').write_text('version=0.4.3\nversion=0.4.4\n')
            with self.assertRaises(ValueError):m.source_version(root)
    def test_complete_owner_replace_and_unrelated_custody(self):
        old={'a.class':b'old','LICENSE':b'legal'};new={'a.class':b'new','b.class':b'added','LICENSE':b'legal'}
        product={'a.class':b'old','META-INF/licenses/engine/LICENSE':b'legal','sdk.class':b'sdk'}
        out,receipt=m.replace_owner(product,old,new,{'a.class':'a.class','LICENSE':'META-INF/licenses/engine/LICENSE'})
        self.assertEqual(out['a.class'],b'new');self.assertEqual(out['b.class'],b'added')
        self.assertEqual(out['sdk.class'],b'sdk');self.assertEqual(receipt['unchanged'],{'sdk.class':m.digest(b'sdk')})
        with self.assertRaises(ValueError):m.replace_owner(product,old,{**new,'sdk.class':b'collision'},
            {'a.class':'a.class','LICENSE':'META-INF/licenses/engine/LICENSE'})
        with self.assertRaises(ValueError):m.replace_owner({**product,'a.class':b'wrong'},old,new,
            {'a.class':'a.class','LICENSE':'META-INF/licenses/engine/LICENSE'})
    def test_real_service_union_and_container_exclusion(self):
        old={'META-INF/services/X':b'Old\n','META-INF/MANIFEST.MF':b'oldmanifest'}
        new={'META-INF/services/X':b'New\n','META-INF/MANIFEST.MF':b'newmanifest'}
        product={'META-INF/services/X':b'Old\nOther\n','META-INF/MANIFEST.MF':b'container'}
        out,_=m.replace_owner(product,old,new,{'META-INF/services/X':'META-INF/services/X','META-INF/MANIFEST.MF':None})
        self.assertEqual(out['META-INF/services/X'],b'Other\nNew\n')
        self.assertEqual(out['META-INF/MANIFEST.MF'],b'container')
    def test_archive_exact_custody_and_traversal_rejection(self):
        with tempfile.TemporaryDirectory() as t:
            path=Path(t)/'good.jar';payload={'one':b'1','resource/two':b'2'}
            m.archive(path,payload);self.assertEqual(m.entries(path),payload)
            bad=Path(t)/'bad.jar'
            with zipfile.ZipFile(bad,'w') as z:z.writestr('../unsafe',b'x')
            with self.assertRaises(ValueError):m.entries(bad)
    def test_physical_class_major_inventory(self):
        payload={'Own.class':b'\xca\xfe\xba\xbe\x00\x00\x00\x3d','resource':b'bytes'}
        rows=m.physical_inventory(payload)
        self.assertEqual(rows['Own.class']['major'],61);self.assertIsNone(rows['resource']['major'])
        self.assertEqual(rows['resource']['bytes'],5)
    def test_primary_file_cli_and_remote_guard(self):
        with tempfile.TemporaryDirectory() as t:
            original=m.ROOT
            m.ROOT=Path(t);(m.ROOT/'gradle.properties').write_text('version=0.4.4\n')
            self.addCleanup(setattr,m,'ROOT',original)
            args=argparse.Namespace(target='1.16.5',family='forge-1.16.5',version=m.source_version(),
                output=Path(t)/('openallay-forge-1.16.5-'+m.source_version()+'.jar'),plan=True)
            args.receipt=Path(str(args.output)+'.packaging.json')
            result=m.prepare(args);self.assertTrue(result['remoteOnly']);self.assertFalse(result['gameExecuted'])
            args.version='wrong'
            with self.assertRaises(ValueError):m.prepare(args)
    def test_historical_and_current_builder_pins_remain_distinct(self):
        self.assertEqual(m.BUILDER_SOURCE,'6e977110cbe8e0ca0b39c012f0cdfc10bafffef2')
        self.assertEqual(m.PRODUCT16['sourceRevision'],'ee94284188f21003de589d509e880875cabc5cbc')
        self.assertFalse(hasattr(m, 'BOOT12'))
        self.assertFalse(hasattr(m, 'public12'))
        self.assertFalse(hasattr(m, 'prepared12'))
        self.assertFalse(hasattr(m, 'native12'))

if __name__=='__main__':unittest.main()
