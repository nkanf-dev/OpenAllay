#!/usr/bin/env python3
"""Small synthetic regression contract; does not build or use Minecraft artifacts."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("reinjection", Path(__file__).with_name("reinject-native-engine.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReinjectionTest(unittest.TestCase):
    def test_restores_engine_and_preserves_every_native_member(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            engine = root / "engine"
            for name, data in {"dev/openallay/FeatureServices.class": b"compiled-engine",
                               "dev/openallay/guide/GuideService.class": b"guide-engine",
                               "engine/resource.txt": b"engine-resource"}.items():
                path = engine / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            source, final = root / "native.jar", root / "final.jar"
            native_members = {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\n\r\n",
                              "dev/openallay/nativebridge/Bridge.class": b"native-reobf",
                              "META-INF/jarjar/metadata.json": b"{jarjar-metadata}",
                              "META-INF/jarjar/sdk.jar": b"nested-sdk-byte-identity",
                              "openallay.refmap.json": b"mixin-refmap",
                              "META-INF/openallay/bundled-extensions/builder.jar": b"builder",
                              "dev/openallay/FeatureServices.class": b"ART-rewritten"}
            with zipfile.ZipFile(source, "w") as archive:
                archive.comment = b"native-comment"
                for name, data in native_members.items():
                    archive.writestr(name, data)
            receipt = module.reinject(source, final, [engine])
            with zipfile.ZipFile(final) as archive:
                self.assertEqual(archive.comment, b"native-comment")
                self.assertEqual(len(archive.namelist()), len(set(archive.namelist())))
                for name, data in native_members.items():
                    self.assertEqual(archive.read(name), b"compiled-engine" if name.endswith("FeatureServices.class") else data)
                self.assertEqual(archive.read("engine/resource.txt"), b"engine-resource")
            self.assertEqual(receipt["changedEngineEntries"], 3)
            self.assertEqual(receipt["nonEngineEntryBytes"], "identical")
            with zipfile.ZipFile(root / "duplicates.jar", "w") as archive:
                archive.writestr("collision", b"one")
                archive.writestr("collision", b"two")
            with self.assertRaisesRegex(ValueError, "Duplicate native entries"):
                module.reinject(root / "duplicates.jar", root / "bad.jar", [engine])
            self.assertFalse((root / "bad.jar").exists())
            with self.assertRaisesRegex(ValueError, "paths must differ"):
                module.reinject(source, source, [engine])


if __name__ == "__main__":
    unittest.main()
