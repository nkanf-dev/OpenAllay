"""Finite raw source-pair admission. No lexer, compilation, network or bytecode edits."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from release_comment_custody import PATHS, POLICY_PATH, policy, verify_pair

ROOT=Path(__file__).resolve().parents[1]
ORIGINAL_SOURCE="83b3b3ddb0cfd5b90d11a8fdb81c77ee05bf66f5"


class CommentCustodyTest(unittest.TestCase):
    def setUp(self):
        self.rows=policy(ROOT)
        self.temporary=tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)
        dest=self.root/POLICY_PATH;dest.parent.mkdir(parents=True)
        self.original_policy=json.loads((ROOT/POLICY_PATH).read_text())
        dest.write_text(json.dumps(self.original_policy))

    def test_only_exact_same_line_comment_authority(self):
        self.assertEqual(set(self.rows),PATHS)
        self.assertEqual(self.original_policy['reviewedPatchSha256'],"fa62db45c08976472933576dd85bb82120297fba65cee551d3768126e7a7e607")
        for path,row in self.rows.items():
            original=subprocess.check_output(['git','-C',str(ROOT),'show',ORIGINAL_SOURCE+':'+path])
            current=(ROOT/path).read_bytes()
            self.assertEqual(hashlib.sha256(original).hexdigest(),row['beforeSha256'])
            self.assertEqual(hashlib.sha256(current).hexdigest(),row['afterSha256'])
            self.assertEqual(original.count(b'\n'),current.count(b'\n'))
            checked=verify_pair(ROOT,path,original,current)
            self.assertEqual(checked['reviewedNoncommentCharLineColumnSha256'],row['noncommentCharLineColumnSha256'])
            for changed_before,changed_after in ((original+b' ',current),(original,current+b' '),(current,original)):
                with self.assertRaises(ValueError):verify_pair(ROOT,path,changed_before,changed_after)

    def test_unknown_or_extra_java_owner_rejected(self):
        with self.assertRaises(ValueError):verify_pair(ROOT,'engine-core/src/main/java/dev/openallay/Unknown.java',b'old',b'new')
        for mutate in (lambda v:v['files'][0].update(path='engine-core/src/main/java/dev/openallay/Unknown.java'),
                       lambda v:v['files'].append(v['files'][0])):
            changed=deepcopy(self.original_policy);mutate(changed)
            (self.root/POLICY_PATH).write_text(json.dumps(changed))
            with self.assertRaises(ValueError):policy(self.root)

    def test_old_constructor_insertion_hash_is_not_admitted(self):
        rejected={
          'engine-core/src/main/java/dev/openallay/benchmark/BenchmarkTraceAudit.java':'a728a8843cc3bd56d8e56c00d4dac5bc8d4a8f9cfc87a747be29ac4b99f9854d',
          'engine-core/src/main/java/dev/openallay/recipe/RecipeCatalogStatus.java':'ece78db671fe675b0d47f867d52f629a5f5faa9b4d2e6c9dc839106869789b55',
          'engine-core/src/main/java/dev/openallay/recipe/RecipeProviderReadiness.java':'5381db1b6a4249dbd4e4e99a907e65cab9541bfacff81aa1866caa787738377f',
          'engine-core/src/main/java/dev/openallay/recipe/config/RecipeClientConfig.java':'60f213f7d8bc6189023333509033cc50df4339f4f2efece6b2dcf73e9ada2efc'}
        for path,row in self.rows.items():self.assertNotEqual(row['afterSha256'],rejected[path])

    def test_existing_runtime_byte_identity_guards_remain(self):
        stock=(ROOT/'scripts/materialize-stock8-release.py').read_text()
        self.assertIn('digest(raw) == accepted["jarSha256"]',stock)
        self.assertIn('== ENGINE_SHA == native["currentEngineSha256"]',stock)
        self.assertIn('verify_comment_pair(root, path, git(root, "show", packing_source + ":" + path)',stock)
        group=(ROOT/'scripts/build-minecraft-artifacts.py').read_text()
        self.assertIn('verify_comment_pair(ROOT, path, git_output("show", old_source + ":" + path, binary=True)',group)
        self.assertIn('"Shared engine was changed or omitted: "',group)
        self.assertIn('"Original artifact/receipt bytes differ from exact reuse approval"',group)


if __name__=='__main__':unittest.main()
