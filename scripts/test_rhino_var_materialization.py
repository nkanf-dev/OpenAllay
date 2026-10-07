#!/usr/bin/env python3
"""Canonical Rhino materialization regressions without running the Java compiler."""
import ast
import difflib
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
# Load only the actual pure function; do not execute argparse/compiler orchestration.
tree = ast.parse((ROOT / "scripts/port-rhino-var-sources.py").read_text())
node = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "delta_hunks")
namespace = {"difflib": difflib}
exec(compile(ast.Module(body=[node], type_ignores=[]), "actual_delta_hunks", "exec"), namespace)
delta_hunks = namespace["delta_hunks"]

class RhinoMaterializationTest(unittest.TestCase):
    def replay(self, before, hunks):
        for hunk in hunks:
            self.assertEqual(1, before.count(hunk["before"]))
            before = before.replace(hunk["before"], hunk["after"], 1)
        return before
    def test_unique_context_stays_small(self):
        before = "one\ntwo\nvar value = 1;\nthree\nfour\n"
        after = before.replace("var value", "int value")
        self.assertEqual(after, self.replay(before, delta_hunks(before, after)))
    def test_duplicate_context_uses_one_whole_owner(self):
        block = "a\nb\nc\nvar value = 1;\nd\ne\nf\n"
        before = "owner-start\n" + block + "separator\n" + block + "owner-end\n"
        after = before.replace("var value = 1;", "int value = 1;", 1)
        hunks = delta_hunks(before, after)
        self.assertEqual([{"before": before, "after": after}], hunks)
        self.assertEqual(after, self.replay(before, hunks))
    def test_unchanged_owner_has_no_hunks(self):
        self.assertEqual([], delta_hunks("owner", "owner"))
    def test_empty_preimage_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Empty owner"): delta_hunks("", "new")

if __name__ == "__main__": unittest.main()
