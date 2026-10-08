"""No-network docs-descendant stage reuse. No compilation or receipt restamping."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('stage_build',ROOT/'scripts/build-minecraft-artifacts.py')
m=module_from_spec(spec);spec.loader.exec_module(m)


class StageOnlyTest(unittest.TestCase):
    def setUp(self):
        self.data=m.catalog();self.source='f'*40;self.original='a'*40
        self.selection={target:{'sourceSha':self.original,'familyArtifacts':[{'id':row['id']} for row in families]}
                        for target,families in m.groups(self.data)}

    def test_complete_twenty_targets_pass_without_compilation(self):
        with patch.object(m,'read_reuse_selection',return_value=self.selection),patch.object(m,'source_identity',return_value=self.source), \
                patch.object(m,'verify_reused_source') as custody:
            result=m.validate_stage_only_selection()
        self.assertEqual(result['targetCount'],20);self.assertEqual(result['familyCount'],35)
        self.assertEqual(custody.call_count,20)
        self.assertEqual(result['stageSourceSha'],self.source)
        self.assertEqual(result['originalPackageSources'],[self.original])

    def test_empty_partial_unknown_and_missing_family_fail(self):
        for selection in ({},{k:v for k,v in self.selection.items() if k!='1.12.2'},dict(self.selection,unknown={}),
                          dict(self.selection,**{'1.12.2':{'sourceSha':self.original,'familyArtifacts':[]}})):
            with patch.object(m,'read_reuse_selection',return_value=selection),patch.object(m,'source_identity',return_value=self.source), \
                    patch.object(m,'verify_reused_source'),self.assertRaises(ValueError):m.validate_stage_only_selection()

    def test_stage_only_rejects_competing_dispatch_modes(self):
        with patch.object(m.os,'environ',{'RELEASE_DISPATCH_INPUTS':json.dumps({'stage_only':True,'metadata_only':True})}),self.assertRaises(ValueError):
            m.validate_stage_only_selection()

    def test_exact_docs_whitelist_rejects_production_changes(self):
        docs=['README.md','README.zh-CN.md','docs/releases/0.4.4.md','docs/forge-runtime-installation.md',
              'docs/native-binary-artifacts.md','docs/minecraft-support-policy.md']
        self.assertTrue(set(docs).issubset(m.REUSE_ORCHESTRATION_PATHS))
        families=[m.artifacts.family_for('forge','1.12.2',['1.12.2'])]
        def git(*args,**kwargs):
            if args[0]=='diff':return '\0'.join(docs)+'\0'
            return ''
        with patch.object(m,'git_output',side_effect=git),patch.object(m.subprocess,'run'):
            m.verify_reused_source(self.original,self.source,families)
        for changed in ('engine-core/src/main/java/Changed.java','docs/arbitrary-unapproved.md','distribution/extensions.lock.json'):
            with patch.object(m,'git_output',return_value=changed+'\0'),patch.object(m.subprocess,'run'),self.assertRaises(ValueError):
                m.verify_reused_source(self.original,self.source,families)

    def test_unapproved_original_receipt_hash_source_run_and_engine_reject(self):
        family=m.artifacts.family_for('forge','1.12.2',['1.12.2'])
        receipt={'sourceSha':self.original,'sourceRunId':'42','sourceRunAttempt':'1','artifactSha256':'b'*64}
        group={'sourceSha':self.original,'runId':42,'runAttempt':1,'engineManifestSha256':'c'*64,
               'target':'1.12.2','familyArtifacts':[{'id':family['id'],'artifactSha256':'b'*64,'receiptSha256':'d'*64}]}
        with patch.object(m.artifacts,'file_hash',return_value='d'*64),patch.object(m,'verify_reused_source'):
            m.verify_receipt_source(family,receipt,Path('original.json'),self.source,'c'*64,{'1.12.2':group},set())
        with self.assertRaises(ValueError):m.verify_receipt_source(family,receipt,Path('original.json'),self.source,'c'*64,{},set())
        for changed in (dict(receipt,sourceSha='e'*40),dict(receipt,sourceRunId='43'),dict(receipt,artifactSha256='e'*64)):
            with patch.object(m.artifacts,'file_hash',return_value='d'*64),self.assertRaises(ValueError):
                m.verify_receipt_source(family,changed,Path('original.json'),self.source,'c'*64,{'1.12.2':group},set())
        with patch.object(m.artifacts,'file_hash',return_value='e'*64),self.assertRaises(ValueError):
            m.verify_receipt_source(family,receipt,Path('original.json'),self.source,'c'*64,{'1.12.2':group},set())
        with self.assertRaises(ValueError):m.verify_receipt_source(family,receipt,Path('original.json'),self.source,'e'*64,{'1.12.2':group},set())

    def test_verification_approval_precedes_legacy_product_consumer(self):
        source=(ROOT/'scripts/build-minecraft-artifacts.py').read_text()
        start=source.index('def verify_build_receipts');end=source.index('def publication_records');code=source[start:end]
        self.assertLess(code.index('verify_receipt_source('),code.index('approved_package_sources[family["id"]] = receipt["sourceSha"]'))
        self.assertLess(code.index('approved_package_sources[family["id"]] = receipt["sourceSha"]'),code.index('records = verify('))
        self.assertIn('packagingProofSha256',code)
        workflow=(ROOT/'.github/workflows/minecraft-native.yml').read_text()
        self.assertIn('!inputs.stage_only && !inputs.metadata_only',workflow)
        self.assertIn('inputs.stage_only || needs.build-packages.result',workflow)
        self.assertIn('stage-only-selection',workflow)
        self.assertIn('if: ${{ !inputs.stage_only }}',workflow)


if __name__=='__main__':unittest.main()
