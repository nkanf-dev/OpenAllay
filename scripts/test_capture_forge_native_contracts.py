import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import zipfile
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("native_capture", ROOT / "scripts/capture-forge-native-contracts.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class CaptureContractTest(unittest.TestCase):
    def test_retains_only_exact_command_owner_method_closure(self):
        text = """public class Sample {
  public void commandSigned(java.lang.String, java.lang.Object);
    descriptor: (Ljava/lang/String;Ljava/lang/Object;)V
    Code:
      0: invokevirtual #1 // Method localHelper:()V
      3: invokevirtual #2 // Method external/Signer.sign:()V
  private void localHelper();
    descriptor: ()V
    Code:
      0: return
  public void unrelated();
    descriptor: ()V
    Code:
      0: return
}
"""
        methods = module.method_blocks(text)
        self.assertEqual(["commandSigned", "localHelper"], module.selected_closure(methods, ["commandSigned"], "Sample"))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "named.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                for owner in module.CLASSES:
                    archive.writestr(owner.replace(".", "/") + ".class", b"\xca\xfe\xba\xbe\x00\x00\x00\x3d")
            with patch.object(module.subprocess, "run", return_value=SimpleNamespace(returncode=0, stdout=text.encode(), stderr=b"")):
                result = module.capture(jar, root / "javap", root / "out", ":forge", "1.19.2")
            self.assertEqual("1.19.2", result["minecraftTarget"])
            self.assertEqual("captured", result["classes"][0]["status"])
            output = (root / "out/LocalPlayer.bytecode.txt").read_text()
            self.assertIn("external/Signer.sign", output)
            self.assertNotIn("unrelated", output)
            self.assertEqual("not established by bytecode capture", result["gameCommandExecution"])

    def test_unavailable_evidence_tool_records_reason_without_product_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "named.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                for owner in module.CLASSES:
                    archive.writestr(owner.replace(".", "/") + ".class", b"\xca\xfe\xba\xbe\x00\x00\x00\x3d")
            with patch.object(module.subprocess, "run", side_effect=FileNotFoundError("missing javap")):
                result = module.capture(jar, root / "javap", root / "out", ":forge", "1.19.2")
            self.assertTrue(all(item["status"] == "javap-unavailable" for item in result["classes"]))
            self.assertTrue((root / "out/receipt.json").is_file())
