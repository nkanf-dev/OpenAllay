#!/usr/bin/env python3
"""Focused stdlib mock checks. No game, Java, build, network, or dependency resolution."""
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

path = Path(__file__).with_name("collect-engine-prerequisite.py")
spec = importlib.util.spec_from_file_location("engine_collector_tested", path)
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class BoundaryTests(unittest.TestCase):
    def test_failure_marker_wins(self):
        self.assertEqual(collector.terminal(collector.PASS + "\n" + collector.FAIL + "identity"),
                         "probe-failure-marker")

    def test_read_failure_keeps_first_stage(self):
        receipt = {"status": "FAIL", "prerequisiteOnly": True, "fullNativeSupport": False,
                   "failureStage": "bound-engine-json", "fullCause": "original cause and stack",
                   "stages": [{"stage": "identity", "status": "PASS"},
                              {"stage": "bound-engine-json", "status": "FAIL"}]}
        with patch.object(Path, "read_text", return_value=json.dumps(receipt)):
            self.assertEqual(collector.read_receipt(Path("mock"), "a"*64, [])["fullCause"],
                             "original cause and stack")
            receipt["stages"].append({"stage": "json-trees-readers", "status": "PASS"})
        with patch.object(Path, "read_text", return_value=json.dumps(receipt)):
            with self.assertRaises(ValueError):
                collector.read_receipt(Path("mock"), "a"*64, [])

    def test_partial_pass_rejected(self):
        receipt = {"status": "PASS", "prerequisiteOnly": True, "fullNativeSupport": False,
                   "stages": [{"stage": "identity", "status": "PASS"}]}
        with patch.object(Path, "read_text", return_value=json.dumps(receipt)):
            with self.assertRaises(ValueError):
                collector.read_receipt(Path("mock"), "a"*64, [])

    def test_complete_pass_checks_same_pid_and_host_origin(self):
        stages = [{"stage": name, "status": "PASS", "details": {}} for name in collector.STAGES]
        details = stages[0]["details"]
        details["pid"] = "42"
        for name, major in [("dev.openallay.forge36probe.Probe", 61),
                            ("dev.openallay.script.RhinoJavascriptRuntime", 61),
                            ("dev.openallay.json.EngineJson", 61), ("dev.latvian.mods.rhino.Context", 61),
                            ("dev.openallay.api.extension.OpenAllayExtension", 52)]:
            details[name] = {"classMajor": major, "archiveSha256": "a"*64,
                             "loaderIdentity": 10, "codeSource": "/mod.jar"}
        stages[-1]["details"]["dev.openallay.builder.BuilderExtension"] = {
            "classMajor": 52, "archiveSha256": "a"*64, "loaderIdentity": 10, "codeSource": "/mod.jar"}
        classpath = []
        for name, coordinate, file in [("com.google.gson.Gson", "com.google.code.gson:gson:2.8.0", "/gson.jar"),
                                      ("com.google.common.collect.ImmutableList", "com.google.guava:guava:21.0", "/guava.jar"),
                                      ("org.slf4j.Logger", "official:slf4j", "/slf4j.jar")]:
            details[name] = {"archiveSha256": "b"*64, "codeSource": file}
            classpath.append({"path": file, "sha256": "b"*64, "coordinate": coordinate})
        receipt = {"status": "PASS", "prerequisiteOnly": True, "fullNativeSupport": False, "stages": stages}
        with patch.object(Path, "read_text", return_value=json.dumps(receipt)):
            self.assertEqual(collector.read_receipt(Path("mock"), "a"*64, classpath, 42)["status"], "PASS")
            with self.assertRaises(ValueError):
                collector.read_receipt(Path("mock"), "a"*64, classpath, 43)
        details["com.google.gson.Gson"]["codeSource"] = "/override.jar"
        with patch.object(Path, "read_text", return_value=json.dumps(receipt)):
            with self.assertRaises(ValueError):
                collector.read_receipt(Path("mock"), "a"*64, classpath, 42)

    def test_terminal_marker_stops_before_wait(self):
        from io import StringIO
        class Process:
            def poll(self): raise AssertionError("No poll after marker")
            def wait(self, timeout): raise AssertionError("No wait after marker")
        with patch.object(Path, "open", return_value=StringIO(collector.PASS)):
            self.assertEqual(collector.collect(Process(), Path("mock"), 120),
                             "probe-pass-marker")

    def test_early_exit_and_timeout(self):
        from io import StringIO
        class Exited:
            def poll(self): return 1
        with patch.object(Path, "open", return_value=StringIO("native crash detail")):
            self.assertEqual(collector.collect(Exited(), Path("mock"), 120),
                             "client-exited-before-terminal-marker")
        class Live:
            def poll(self): return None
            def wait(self, timeout): raise AssertionError("No blocking wait after deadline")
        with patch.object(Path, "open", return_value=StringIO("")), \
                patch.object(collector.time, "monotonic", side_effect=[0, 121]):
            self.assertEqual(collector.collect(Live(), Path("mock"), 120), "probe-timeout")


if __name__ == "__main__":
    unittest.main()
