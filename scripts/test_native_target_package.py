import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location("native_package", Path(__file__).with_name("verify-native-target-package.py"))
PACKAGE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PACKAGE)


class NativeTargetPackageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.path = Path(self.temporary.name) / "native.jar"
        self.engine = {"dev/openallay/OpenAllayBootstrap.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x3dsynthetic"}

    def write(self, changes=None):
        import json
        values = dict(self.engine)
        values["fabric.mod.json"] = json.dumps({"id": "openallay", "depends": {"minecraft": "~1.20.4", "java": ">=17"}}).encode()
        values.update(changes or {})
        with zipfile.ZipFile(self.path, "w") as jar:
            for name, blob in values.items():
                jar.writestr(name, blob)

    def test_identical_compiled_entries_only_establish_package_identity(self):
        self.write()
        receipt = PACKAGE.verify(self.path, "fabric", "1.20.4", 17, self.engine)
        self.assertEqual(receipt["sharedEngineEntries"], 1)
        self.assertNotIn("runtimeAcceptance", receipt)

    def test_changed_engine_refused(self):
        self.write({next(iter(self.engine)): b"changed"})
        with self.assertRaisesRegex(ValueError, "Shared engine"):
            PACKAGE.verify(self.path, "fabric", "1.20.4", 17, self.engine)

    def test_class_above_target_java_refused(self):
        self.write({"dev/openallay/Native.class": b"\xca\xfe\xba\xbe\x00\x00\x00\x41synthetic"})
        with self.assertRaisesRegex(ValueError, "exceeds target Java"):
            PACKAGE.verify(self.path, "fabric", "1.20.4", 17, self.engine)

    def test_bundled_builder_not_admitted_on_old_native_target(self):
        self.write({"META-INF/openallay/bundled-extensions/builder.jar": b"synthetic"})
        with self.assertRaisesRegex(ValueError, "Builder support"):
            PACKAGE.verify(self.path, "fabric", "1.20.4", 17, self.engine)

    def test_wrong_target_metadata_refused(self):
        self.write()
        with self.assertRaisesRegex(ValueError, "target metadata"):
            PACKAGE.verify(self.path, "fabric", "1.20.1", 17, self.engine)

    def test_missing_legacy_refmap_refused(self):
        values = dict(self.engine)
        values["META-INF/mods.toml"] = b'modLoader="javafml"\n[[mods]]\nmodId="openallay"\n[[dependencies.openallay]]\nmodId="minecraft"\nversionRange="[1.20.1]"\n[[dependencies.openallay]]\nmodId="forge"\n'
        values["openallay.client.mixins.json"] = b'{"refmap":"openallay.refmap.json"}'
        with zipfile.ZipFile(self.path, "w") as jar:
            for name, blob in values.items():
                jar.writestr(name, blob)
        with self.assertRaisesRegex(ValueError, "Mixin refmap"):
            PACKAGE.verify(self.path, "neoforge", "1.20.1", 17, self.engine)


if __name__ == "__main__":
    unittest.main()
