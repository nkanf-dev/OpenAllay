#!/usr/bin/env python3
"""Pure canonical packet byte tests; public compiler checks belong to the remote JDK fixture."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("core_var_port", ROOT / "scripts/port-core-var-sources.py")
port = importlib.util.module_from_spec(spec)
spec.loader.exec_module(port)


class CanonicalVarBytesTest(unittest.TestCase):
    def test_utf16_reverse_offsets_preserve_raw_newlines_and_unicode(self):
        source = "// 🐝\r\nvar first=1;\r\nvar second=2;\r\n"
        offsets = [(len(source[:source.index("var")].encode("utf-16-le"))//2, "int"),
                   (len(source[:source.rindex("var")].encode("utf-16-le"))//2, "long")]
        converted = port.replace_sites(source.encode(), offsets)
        self.assertEqual(source.replace("var first", "int first").replace("var second", "long second").encode(), converted)
    def test_duplicate_offsets_fail(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            port.replace_sites(b"var first=1;", [(0,"int"),(0,"long")])
    def test_unmatched_token_fails(self):
        with self.assertRaisesRegex(ValueError, "Exact parsed source token differs"):
            port.replace_sites(b"int first=1;", [(0,"long")])
    def test_no_sites_preserves_exact_bytes(self):
        original = "// 🐝\r\nint first=1;\r\n".encode()
        self.assertEqual(original, port.replace_sites(original, []))


if __name__ == "__main__":
    unittest.main()
