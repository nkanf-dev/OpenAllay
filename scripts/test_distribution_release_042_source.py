"""Offline 0.4.2 source metadata guards. No builds, tags, or publication."""
from importlib.util import module_from_spec, spec_from_file_location
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = spec_from_file_location("release_launcher_version", ROOT / "scripts/run-packaged-builder-acceptance.py")
launcher = module_from_spec(SPEC)
SPEC.loader.exec_module(launcher)


class ReleaseCoordinateTest(unittest.TestCase):
    def test_product_and_independent_versions(self):
        self.assertEqual(launcher.product_version(ROOT), "0.4.2")
        self.assertIn("version = '0.4.0'", (ROOT / "extension-api/build.gradle").read_text())
        self.assertIn('"version": "0.4.0"', (ROOT / "distribution/extensions.lock.json").read_text())
        self.assertTrue((ROOT / "docs/releases/0.4.1.md").read_text().startswith("# OpenAllay v0.4.1\n"))
        self.assertTrue((ROOT / "docs/releases/0.4.2.md").read_text().startswith("# OpenAllay v0.4.2\n"))

    def test_tag_validation_uses_product_metadata_not_public_sdk_version(self):
        source = (ROOT / "scripts/verify-release-tag.sh").read_text()
        self.assertIn("version=$(sed -n 's/^version=//p' gradle.properties)", source)
        self.assertIn('[[ "$version" == "${tag#v}" ]]', source)
        self.assertIn('tag must exist locally and be annotated', source)
        self.assertIn('a GitHub release already exists for $tag', source)

    def test_default_artifact_uses_actual_product_version(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temporary:
            repo = Path(temporary)
            (repo / "gradle.properties").write_text("version=0.4.2\n")
            # The identity guard must reject the old filename before archive/dependency work.
            with patch.object(launcher, "bundled_extension_verifier") as verifier:
                with self.assertRaisesRegex(ValueError, "openallay-fabric-26.2-0.4.2.jar"):
                    launcher.packaged_artifact(repo / "openallay-fabric-26.2-0.4.1.jar", "fabric", repo=repo)
                verifier.assert_not_called()

    def test_version_binding_refuses_ambiguous_or_invalid_source(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temporary:
            repo = Path(temporary)
            for value in ("", "version=0.4.2\nversion=0.4.1\n", "version=../../escape\n"):
                (repo / "gradle.properties").write_text(value)
                with self.subTest(value=value), self.assertRaises(ValueError):
                    launcher.product_version(repo)


if __name__ == "__main__":
    unittest.main()
