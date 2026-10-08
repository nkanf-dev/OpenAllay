"""Exact catalog-group filtering. No builds, dispatch or network."""
from importlib.util import spec_from_file_location,module_from_spec
from pathlib import Path
import unittest
from unittest.mock import patch
import json

ROOT=Path(__file__).resolve().parents[1]
spec=spec_from_file_location('target_filter',ROOT/'scripts/build-minecraft-artifacts.py')
m=module_from_spec(spec);spec.loader.exec_module(m)


class TargetFilterTest(unittest.TestCase):
    def setUp(self):self.data=m.catalog()
    def test_empty_preserves_all_twenty_catalog_groups(self):
        self.assertEqual(m.filter_groups(self.data),m.groups(self.data))
        self.assertEqual(m.filter_groups(self.data,''),m.groups(self.data))
        self.assertEqual(len(m.filter_groups(self.data)),20)
    def test_legacy_targets_are_real_native_groups_no_alias(self):
        selected=m.filter_groups(self.data,'1.16.5,1.12.2')
        self.assertEqual([target for target,_ in selected],['1.12.2','1.16.5'])
        self.assertEqual([f['id'] for _,families in selected for f in families],['forge-1.12.2','forge-1.16.5'])
    def test_duplicates_unknown_partial_version_whitespace_empty_component_fail(self):
        for value in ('1.12.2,1.12.2','unknown','1.12','1.12.2,',' 1.12.2','1.12.2, 1.16.5',',',None):
            if value is None:continue
            with self.subTest(value=value),self.assertRaises(ValueError):m.filter_groups(self.data,value)
    def test_stage_only_refuses_target_filter(self):
        with patch.dict(m.os.environ,RELEASE_DISPATCH_INPUTS=json.dumps({'stage_only':True,'build_targets':'1.12.2'})),self.assertRaises(ValueError):
            m.validate_stage_only_selection()
    def test_normal_workflow_routes_safe_input_and_skips_partial_merge(self):
        text=(ROOT/'.github/workflows/minecraft-native.yml').read_text()
        self.assertIn('RELEASE_BUILD_TARGETS: ${{ inputs.build_targets }}',text)
        self.assertIn('--targets "$RELEASE_BUILD_TARGETS"',text)
        self.assertIn("needs.build-packages.result == 'success' && inputs.build_targets == ''",text)
        self.assertIn('max-parallel: 3',text)
        self.assertIn('cancel-in-progress: false',text)


if __name__=='__main__':unittest.main()
