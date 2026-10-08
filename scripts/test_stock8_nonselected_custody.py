"""Finite unselected cohort and complete selector custody, no compiler or network."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import stock8_selection_custody as m

ROOT=Path(__file__).resolve().parents[1]


class SelectionCustodyTest(unittest.TestCase):
    def setUp(self):
        self.policy=m.policy(ROOT)
        self.expected={}
        self.java=[];self.resources=[]
        for i in range(263):
            row={'origin':'selected/'+str(i),'sha256':str(i).zfill(64),'logicalPath':'logical/'+str(i)}
            self.expected[row['origin']]=row['sha256']
            (self.java if i<251 else self.resources).append(row)
        # Fixture shadow mappings are exact logical entries, independent of real production bytes.
        self.fixture=deepcopy(self.policy)
        for i,row in enumerate(self.fixture['shadowEvidence']):
            logical=row['changedPath'].split('/java/',1)[1]
            original=next((x for x in self.java if x['logicalPath']==logical),None)
            if original is None:
                original=self.java[i];original['logicalPath']=logical
            row.update(selectedStock12Before=original['origin'],selectedStock12After=original['origin'],
                       selectedSha256=original['sha256'])

    def verify(self,java=None,resources=None):
        with patch.object(m,'policy',return_value=self.fixture),patch.object(m,'sha',return_value='a'*64),patch.object(m,'verify_control_bytes'), \
                patch.object(Path,'read_bytes',return_value=b'control'),patch.object(m.subprocess,'check_output',return_value=b'control'), \
                patch.object(m,'selected_native_units',return_value=(java or self.java,{'resources':resources or self.resources})):
            for row in self.fixture['selectorInputs']:row['sha256']='a'*64;row['gitSha256']='a'*64
            return m.verify_selection(ROOT,self.expected,'b'*40)

    def test_full263_effective_ledger_passes(self):
        result=self.verify();self.assertEqual(result,{'selectedSources':263,'selectedJava':251,'selectedResources':12,'nonselectedCohortLeaves':25})

    def test_added_shadow_deleted_owner_changed_selected_bytes_fail(self):
        for mutate in (lambda v:v.append({'origin':'common/src/targets/1.12.2/java/New.java','logicalPath':'New.java','sha256':'e'*64}),
                       lambda v:v.pop(),lambda v:v[0].update(origin='shadowed/new.java'),lambda v:v[0].update(sha256='e'*64)):
            java=deepcopy(self.java);mutate(java)
            with self.assertRaises(ValueError):self.verify(java=java)
        resources=deepcopy(self.resources);resources[0]['sha256']='e'*64
        with self.assertRaises(ValueError):self.verify(resources=resources)

    def test_unknown_leaf_and_unexpected_raw_pair_reject(self):
        with self.assertRaises(ValueError):m.verify_unselected_pair(ROOT,'common/src/targets/1.12.2/java/New.java',None,b'new')
        row=self.policy['files'][0]
        with self.assertRaises(ValueError):m.verify_unselected_pair(ROOT,row['path'],None,b'unreviewed')
        with self.assertRaises(ValueError):m.verify_unselected_pair(ROOT,row['path'],b'unexpected original',b'unreviewed')

    def test_selector_control_scope_change_reject(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);file=root/m.POLICY_PATH;file.parent.mkdir(parents=True)
            changed=deepcopy(self.policy);changed['selectorInputs'].pop();file.write_text(json.dumps(changed))
            with self.assertRaises(ValueError):m.policy(root)
        with patch.object(m,'policy',return_value=self.fixture),patch.object(Path,'read_bytes',return_value=b'changedcontrol'):
            with self.assertRaises(ValueError):m.verify_selection(ROOT,self.expected,'b'*40)

    def test_only_declared_gradle_lf_and_crlf_raw_encodings_pass(self):
        row=next(r for r in self.policy['selectorInputs'] if r['path']=='gradle/minecraft-source-selection.gradle')
        raw=(ROOT/row['path']).read_bytes()
        lf=raw.replace(b'\r\n',b'\n');crlf=lf.replace(b'\n',b'\r\n')
        m.verify_control_bytes(row,lf);m.verify_control_bytes(row,crlf)
        with self.assertRaises(ValueError):m.verify_control_bytes(row,lf+b' ')
        other=next(r for r in self.policy['selectorInputs'] if r['path']=='native-builds/forge16165/source-selection.json')
        with self.assertRaises(ValueError):m.verify_control_bytes(other,lf)
        forged=dict(row,sha256='a'*64)
        with self.assertRaises(ValueError):m.verify_control_bytes(forged,lf)

    def test_corrected_actual_26_3_page_fields_raw_policy(self):
        row=next(r for r in self.policy['files'] if r['path']=='common/src/targets/26.3/java/dev/openallay/client/gui/GuideInputCodes.java')
        self.assertEqual(row['postSha256'],'b1e5ef6e798bfc65b203a7ba8c29d02f94cd38fe212a8a5eed61665ca91bab10')
        self.assertEqual(row['bytes'],1802)
        raw=(ROOT/row['path']).read_bytes()
        m.verify_unselected_pair(ROOT,row['path'],None,raw)
        self.assertIn(b'InputConstants.KEY_PAGEUP',raw);self.assertIn(b'InputConstants.KEY_PAGEDOWN',raw)
        self.assertNotIn(b'InputConstants.KEY_PAGE_UP',raw);self.assertNotIn(b'InputConstants.KEY_PAGE_DOWN',raw)
        wrong=raw.replace(b'InputConstants.KEY_PAGEUP',b'InputConstants.KEY_PAGE_UP').replace(b'InputConstants.KEY_PAGEDOWN',b'InputConstants.KEY_PAGE_DOWN')
        with self.assertRaises(ValueError):m.verify_unselected_pair(ROOT,row['path'],None,wrong)

    def test_finite_group_operations_and_nullable_deleted_leaf(self):
        repairs=self.policy['approvedGroupChanges']
        self.assertEqual(len(repairs),12)
        deleted=next(r for r in repairs if r['postSha256'] is None)
        self.assertEqual(deleted['path'],'common/src/targets/1.21.11/java/dev/openallay/platform/minecraft/MinecraftNativeRegistries.java')
        with patch.object(m,'policy',return_value=self.policy),patch.object(m,'sha',return_value=deleted['preSha256']):m.verify_group_pair(ROOT,deleted['path'],b'old',None)
        with patch.object(m,'policy',return_value=self.policy),patch.object(m,'sha',return_value='e'*64),self.assertRaises(ValueError):m.verify_group_pair(ROOT,deleted['path'],b'old',None)
        with self.assertRaises(ValueError):m.verify_group_pair(ROOT,'common/src/main/java/Unknown.java',b'old',b'new')
        original=next(r for r in self.policy['files'] if r['path']==deleted['path'])
        self.assertIsNone(original['preSha256']);self.assertIsNone(original['postSha256'])
        m.verify_unselected_pair(ROOT,deleted['path'],None,None)
        with self.assertRaises(ValueError):m.verify_unselected_pair(ROOT,deleted['path'],None,b'resurrected')

    def test_early_producer_is_inactive_only_for_real_other_targets(self):
        row=self.policy['inactiveEarlyProducerChanges'][0]
        original=b'original';current=b'current'
        with patch.object(m,'policy',return_value=self.policy),patch.object(m.Path,'read_bytes',return_value=b'routing'), \
                patch.object(m,'sha',side_effect=list(self.policy['inactiveEarlyRouting'].values())+[row['preSha256'],row['postSha256']]):
            m.verify_inactive_early_producer(ROOT,row['path'],original,current,[{'loader':'neoforge','buildTarget':'26.2'}])
        for target in ('1.20.2','1.20.3','1.20.5'):
            with self.assertRaises(ValueError):m.verify_inactive_early_producer(ROOT,row['path'],original,current,[{'loader':'neoforge','buildTarget':target}])
        with self.assertRaises(ValueError):m.verify_inactive_early_producer(ROOT,'native-builds/unknown/build.gradle',original,current,[])

    def test_original_physical_and_source_custody_guards_remain(self):
        text=(ROOT/'scripts/materialize-stock8-release.py').read_text()
        self.assertIn('verify_selection(root,selected,native_source)',text)
        self.assertIn('digest((root / path).read_bytes()) == expected',text)
        self.assertIn('digest(raw) == accepted["jarSha256"]',text)
        self.assertIn('== ENGINE_SHA == native["currentEngineSha256"]',text)


if __name__=='__main__':unittest.main()
