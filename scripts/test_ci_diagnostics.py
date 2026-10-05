#!/usr/bin/env python3
"""Offline safety tests plus real cwebp/Pillow tests when CI tools are available."""

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
import zlib

SCRIPT = Path(__file__).with_name("prepare-ci-diagnostics.py")
SPEC = importlib.util.spec_from_file_location("ci_diagnostics", SCRIPT)
diagnostics = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(diagnostics)
HAS_PILLOW = importlib.util.find_spec("PIL") is not None
HAS_CWEBP = shutil.which("cwebp") is not None


def png_bytes(width=2, height=2, alpha=True):
    """Tiny offline RGB/RGBA PNG, with nonzero RGB under transparent alpha."""
    channels = 4 if alpha else 3
    values = ((7, 19, 37, 0), (101, 23, 3, 127), (0, 255, 2, 255), (250, 1, 8, 255))
    raw = b"".join(b"\x00" + b"".join(bytes(values[(x + y) % len(values)][:channels])
                                            for x in range(width)) for y in range(height))
    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data) & 0xffffffff)
    header = struct.pack(">IIBBBBB", width, height, 8, 6 if alpha else 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


class DiagnosticsTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="ci-diagnostics-test-")
        self.addCleanup(self.temporary.cleanup)
        self.repo = Path(self.temporary.name).resolve()
        self.source = self.repo / "build/e2e/explicit-ci-run"
        self.output = self.repo / "build/e2e/compact-ci-run"
        self.source.mkdir(parents=True)

    def write(self, name, value=b"diagnostic\n"):
        path = self.source / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(value)
        return path

    def prepare(self, **kwargs):
        return diagnostics.prepare(self.source, self.output, repo=self.repo, **kwargs)

    def mock_images(self, webp=b"RIFFlossless", decoded=None):
        original = (2, 2, b"exact RGBA including hidden transparent RGB", {"icc_profile": b"", "exif": b""})
        def decode(path, expected_format, limit):
            return original if expected_format == "PNG" or decoded is None else decoded
        def encode(command, **kwargs):
            self.assertEqual(command[1:8], ["-quiet", "-lossless", "-exact", "-m", "6", "-metadata", "all"])
            self.assertEqual(command[-2], "-o")
            self.assertNotEqual(Path(command[-3]), self.source / "screenshots/tiny.png")
            self.assertEqual(kwargs["stdin"], subprocess.DEVNULL)
            self.assertEqual(kwargs["timeout"], 120)
            Path(command[-1]).write_bytes(webp)
            return subprocess.CompletedProcess(command, 0)
        self.enterContext(mock.patch.object(diagnostics.shutil, "which", return_value="/ci/bin/cwebp"))
        self.enterContext(mock.patch.object(diagnostics, "decode_image", side_effect=decode))
        return self.enterContext(mock.patch.object(diagnostics.subprocess, "run", side_effect=encode))

    def assert_no_publication(self):
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.output.parent.glob(".ci-diagnostics-*")), [])

    def test_copies_only_diagnostics_and_preserves_original_source(self):
        values = {"report.json": b'{"outcome":"FAILED"}\n', "trace.json": b'{"events":[]}\n',
                  "source-manifest.json": b'{"files":{}}\n', "summary.json": b'{}\n',
                  "client.log": b"client output\n", "logs/fixture.log": b"fixture output\n"}
        for name, value in values.items():
            self.write(name, value)
        excluded = ["launch.json", "game/config/openallay/models.json", "game/saves/player/level.dat",
                    "game/mods/actual-tested.jar", "natives/library.so", "historical.png"]
        for name in excluded:
            self.write(name, b"not diagnostics")
        manifest = self.prepare()
        self.assertEqual(set(entry["sourcePath"] for entry in manifest["files"]), set(values))
        for entry in manifest["files"]:
            name = entry["sourcePath"]
            expected = hashlib.sha256(values[name]).hexdigest()
            self.assertEqual(entry["sourceSha256"], expected)
            self.assertEqual(entry["outputSha256"], expected)
            self.assertEqual((self.output / name).read_bytes(), values[name])
            self.assertEqual((self.source / name).read_bytes(), values[name])
        for name in excluded:
            self.assertFalse((self.output / name).exists())
            self.assertEqual((self.source / name).read_bytes(), b"not diagnostics")
        self.assertEqual(json.loads((self.output / "diagnostics-manifest.json").read_text()), manifest)
        self.assertNotIn("schemaVersion", manifest)
        self.assertEqual(manifest["sourceBytes"], manifest["outputBytes"])

    def test_mocked_encoder_exact_flags_pixel_mapping_and_source_preservation(self):
        for alpha in (False, True):
            with self.subTest(alpha=alpha):
                original = png_bytes(alpha=alpha)
                self.write("screenshots/tiny.png", original)
                self.mock_images()
                manifest = self.prepare()
                entry = manifest["files"][0]
                self.assertEqual(entry["outputPath"], "screenshots/tiny.webp")
                self.assertEqual(entry["sourceSha256"], hashlib.sha256(original).hexdigest())
                self.assertEqual(entry["outputSha256"], hashlib.sha256(b"RIFFlossless").hexdigest())
                self.assertTrue(entry["image"]["pixelsVerified"])
                self.assertFalse(entry["noSavings"])
                self.assertEqual((self.source / "screenshots/tiny.png").read_bytes(), original)
                shutil.rmtree(self.output)

    def test_webp_not_smaller_keeps_original_png(self):
        original = png_bytes()
        self.write("screenshots/tiny.png", original)
        self.mock_images(webp=b"W" * (len(original) + 1))
        manifest = self.prepare()
        entry = manifest["files"][0]
        self.assertTrue(entry["noSavings"])
        self.assertEqual(entry["outputPath"], "screenshots/tiny.png")
        self.assertEqual(entry["sourceSha256"], entry["outputSha256"])
        self.assertEqual((self.output / "screenshots/tiny.png").read_bytes(), original)
        self.assertGreater(entry["image"]["webpBytes"], entry["sourceBytes"])
        self.assertFalse((self.output / "screenshots/tiny.webp").exists())

    def test_pixel_dimension_and_metadata_mismatches_never_publish(self):
        self.write("screenshots/tiny.png", png_bytes())
        bad_images = [(2, 2, b"altered pixel bytes", {"icc_profile": b"", "exif": b""}),
                      (1, 2, b"exact RGBA including hidden transparent RGB", {"icc_profile": b"", "exif": b""}),
                      (2, 2, b"exact RGBA including hidden transparent RGB", {"icc_profile": b"changed", "exif": b""})]
        for decoded in bad_images:
            with self.subTest(decoded=decoded):
                self.mock_images(decoded=decoded)
                with self.assertRaisesRegex(diagnostics.DiagnosticsError, "does not match|do not match"):
                    self.prepare()
                self.assert_no_publication()
                self.assertEqual((self.source / "screenshots/tiny.png").read_bytes(), png_bytes())

    def test_existing_frozen_stage_is_never_overwritten(self):
        self.write("report.json")
        self.output.mkdir()
        frozen = self.output / "frozen.txt"
        frozen.write_bytes(b"frozen receipt")
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "nonexistent"):
            self.prepare()
        self.assertEqual(frozen.read_bytes(), b"frozen receipt")
        self.assertEqual(list(self.output.iterdir()), [frozen])

    def test_even_empty_existing_stage_is_refused(self):
        self.write("report.json")
        self.output.mkdir()
        with self.assertRaises(diagnostics.DiagnosticsError):
            self.prepare()
        self.assertEqual(list(self.output.iterdir()), [])

    def test_source_root_output_root_traversal_and_overlap_refused(self):
        self.write("report.json")
        pairs = [(self.repo, self.output), (self.repo / "build/e2e", self.output),
                 (self.source, self.repo / "outside"), (self.source, self.source / "compact"),
                 (self.source, self.source.parent), (self.source / ".." / "explicit-ci-run", self.output)]
        for source, output in pairs:
            with self.subTest(source=source, output=output):
                with self.assertRaises(diagnostics.DiagnosticsError):
                    diagnostics.prepare(source, output, repo=self.repo)
        self.assert_no_publication()

    def test_explicit_safe_native_log_include_does_not_scan_game(self):
        selected = self.write("game/logs/latest.log", b"native log")
        self.write("game/logs/secret.dump", b"do not upload")
        manifest = self.prepare(includes=["game/logs/latest.log"])
        self.assertEqual(len(manifest["files"]), 1)
        self.assertEqual((self.output / "game/logs/latest.log").read_bytes(), selected.read_bytes())
        self.assertFalse((self.output / "game/logs/secret.dump").exists())

    def test_denied_and_missing_explicit_includes_fail_closed(self):
        self.write("report.json")
        denied = ["../report.json", "/etc/passwd", "launch.json", "game/config/models.json", "models/a.png",
                  "screenshots/credentials/a.png", "screenshots/../report.json", "game/mods/a.jar", "secrets.txt",
                  "screenshots//a.png", "screenshots/./a.png", "screenshots/a\\b.png", "missing.log"]
        for name in denied:
            with self.subTest(name=name):
                with self.assertRaises(diagnostics.DiagnosticsError):
                    self.prepare(includes=[name])
                self.assert_no_publication()

    def test_symlink_selected_file_directory_and_output_parent_fail(self):
        real = self.repo / "unique.png"
        real.write_bytes(png_bytes())
        screenshot = self.source / "screenshots"
        screenshot.mkdir()
        link = screenshot / "tiny.png"
        link.symlink_to(real)
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "Symlinks"):
            self.prepare()
        link.unlink()
        screenshot.rmdir()
        screenshot.symlink_to(self.repo, target_is_directory=True)
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "Symlinks"):
            self.prepare()
        screenshot.unlink()
        self.write("report.json")
        parent = self.repo / "build/e2e/linked-output"
        parent.symlink_to(self.repo, target_is_directory=True)
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "Symlinks"):
            diagnostics.prepare(self.source, parent / "new", repo=self.repo)
        self.assertEqual(real.read_bytes(), png_bytes())
        self.assert_no_publication()

    def test_size_limits_and_nonregular_files_refused(self):
        self.write("report.json", b"12345")
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "byte limits"):
            self.prepare(max_file_bytes=4)
        self.write("client.log", b"12345")
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "byte limits"):
            self.prepare(max_total_bytes=9)
        for value in (0, -1, diagnostics.MAX_FILE_BYTES + 1):
            with self.assertRaisesRegex(diagnostics.DiagnosticsError, "Limits"):
                self.prepare(max_file_bytes=value)
        (self.source / "report.json").unlink()
        (self.source / "report.json").mkdir()
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "regular"):
            self.prepare()
        self.assert_no_publication()

    def test_changed_source_refuses_publication(self):
        self.write("screenshots/tiny.png", png_bytes())
        self.mock_images()
        real_decode = diagnostics.decode_image
        def changed_source(*args):
            result = real_decode(*args)
            (self.source / "screenshots/tiny.png").write_bytes(b"new bytes from still-running CI client")
            return result
        with mock.patch.object(diagnostics, "decode_image", side_effect=changed_source):
            with self.assertRaisesRegex(diagnostics.DiagnosticsError, "changed before publication"):
                self.prepare()
        self.assert_no_publication()

    def test_missing_encoder_and_encoder_failure_refuse_publication(self):
        self.write("screenshots/tiny.png", png_bytes())
        with mock.patch.object(diagnostics.shutil, "which", return_value=None):
            with self.assertRaisesRegex(diagnostics.DiagnosticsError, "cwebp is required"):
                self.prepare()
        self.mock_images()
        with mock.patch.object(diagnostics.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)):
            with self.assertRaisesRegex(diagnostics.DiagnosticsError, "encoding failed"):
                self.prepare()
        self.assert_no_publication()

    def test_stage_created_during_conversion_is_not_overwritten(self):
        self.write("screenshots/tiny.png", png_bytes())
        self.mock_images()
        real_decode = diagnostics.decode_image
        def create_frozen(*args):
            if not self.output.exists():
                self.output.mkdir()
                (self.output / "receipt.txt").write_bytes(b"another owner published this stage")
            return real_decode(*args)
        with mock.patch.object(diagnostics, "decode_image", side_effect=create_frozen):
            with self.assertRaises(FileExistsError):
                self.prepare()
        self.assertEqual((self.output / "receipt.txt").read_bytes(), b"another owner published this stage")
        self.assertEqual(list(self.output.iterdir()), [self.output / "receipt.txt"])
        self.assertEqual(list(self.output.parent.glob(".ci-diagnostics-*")), [])

    def test_oversized_candidate_and_encoder_timeout_never_publish(self):
        self.write("screenshots/tiny.png", png_bytes())
        self.mock_images(webp=b"W" * 257)
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "Encoded WebP exceeds"):
            self.prepare(max_file_bytes=256)
        self.assert_no_publication()
        with mock.patch.object(diagnostics.subprocess, "run", side_effect=subprocess.TimeoutExpired("cwebp", 120)):
            with self.assertRaisesRegex(diagnostics.DiagnosticsError, "timed out"):
                self.prepare()
        self.assert_no_publication()

    def test_unsafe_descendant_and_credential_named_logs_are_refused(self):
        self.write("report.json")
        unsafe = self.source / "screenshots/config"
        unsafe.mkdir(parents=True)
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "Unsafe path"):
            self.prepare()
        self.assert_no_publication()
        unsafe.rmdir()
        self.write("logs/credential-dump.log", b"never upload")
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "Credential-like"):
            self.prepare()
        self.assert_no_publication()

    def test_empty_run_refused(self):
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "No allowed diagnostics"):
            self.prepare()
        self.assert_no_publication()

    @unittest.skipUnless(HAS_PILLOW and HAS_CWEBP, "requires Pillow in CI tool venv and standard cwebp")
    def test_real_cwebp_decoded_rgb_rgba_pixels_are_exact(self):
        for alpha in (False, True):
            with self.subTest(alpha=alpha):
                original = png_bytes(alpha=alpha)
                self.write("screenshots/tiny.png", original)
                manifest = self.prepare()
                entry = manifest["files"][0]
                self.assertEqual(entry["image"]["width"], 2)
                self.assertEqual(entry["image"]["height"], 2)
                self.assertTrue(entry["image"]["pixelsVerified"])
                self.assertEqual((self.source / "screenshots/tiny.png").read_bytes(), original)
                before = diagnostics.decode_image(self.source / "screenshots/tiny.png", "PNG", 4)
                chosen = self.output / entry["outputPath"]
                after = diagnostics.decode_image(chosen, "WEBP" if chosen.suffix == ".webp" else "PNG", 4)
                self.assertEqual(before, after)
                shutil.rmtree(self.output)

    @unittest.skipUnless(HAS_PILLOW and HAS_CWEBP, "requires Pillow in CI tool venv and standard cwebp")
    def test_real_cwebp_saves_bytes_for_uniform_rgb_rgba_png(self):
        from PIL import Image
        for mode in ("RGB", "RGBA"):
            with self.subTest(mode=mode):
                path = self.source / "screenshots/uniform.png"
                path.parent.mkdir(parents=True, exist_ok=True)
                image = Image.new(mode, (64, 64), (7, 19, 37, 0) if mode == "RGBA" else (7, 19, 37))
                image.save(path, compress_level=0)
                original = path.read_bytes()
                manifest = self.prepare()
                entry = manifest["files"][0]
                self.assertFalse(entry["noSavings"])
                self.assertEqual(entry["outputPath"], "screenshots/uniform.webp")
                self.assertLess(entry["outputBytes"], entry["sourceBytes"])
                self.assertTrue(entry["image"]["pixelsVerified"])
                self.assertEqual(path.read_bytes(), original)
                before = diagnostics.decode_image(path, "PNG", 4096)
                after = diagnostics.decode_image(self.output / entry["outputPath"], "WEBP", 4096)
                self.assertEqual(before, after)
                shutil.rmtree(self.output)

    @unittest.skipUnless(HAS_PILLOW and HAS_CWEBP, "requires Pillow in CI tool venv and standard cwebp")
    def test_real_icc_exif_metadata_preserved_or_refused(self):
        from PIL import Image, ImageCms
        path = self.source / "screenshots/metadata.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        image = Image.new("RGBA", (64, 64), (7, 19, 37, 127))
        exif = Image.Exif()
        exif[0x010e] = "Synthetic CI diagnostic fixture"
        profile = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes()
        image.save(path, compress_level=0, icc_profile=profile, exif=exif)
        original = path.read_bytes()
        try:
            manifest = self.prepare()
        except diagnostics.DiagnosticsError as error:
            # cwebp builds differ in PNG metadata support. Refusal is required if
            # the tool cannot keep optional metadata exactly; do not publish it.
            self.assertIn("metadata does not match", str(error))
            self.assert_no_publication()
        else:
            entry = manifest["files"][0]
            self.assertTrue(entry["image"]["metadataVerified"])
            chosen = self.output / entry["outputPath"]
            self.assertEqual(diagnostics.decode_image(path, "PNG", 4096),
                             diagnostics.decode_image(chosen, "WEBP" if chosen.suffix == ".webp" else "PNG", 4096))
        self.assertEqual(path.read_bytes(), original)

    @unittest.skipUnless(HAS_PILLOW, "requires Pillow in CI tool venv")
    def test_real_decoder_rejects_corrupt_wrong_format_and_pixel_limit(self):
        image = self.write("screenshots/tiny.png", png_bytes())
        with self.assertRaises(diagnostics.DiagnosticsError):
            diagnostics.decode_image(image, "PNG", 3)
        with self.assertRaises(diagnostics.DiagnosticsError):
            diagnostics.decode_image(image, "WEBP", 4)
        image.write_bytes(b"not an image")
        with self.assertRaises(diagnostics.DiagnosticsError):
            diagnostics.decode_image(image, "PNG", 4)
        high_depth = bytearray(png_bytes())
        high_depth[24] = 16
        image.write_bytes(high_depth)
        with self.assertRaisesRegex(diagnostics.DiagnosticsError, "high-depth"):
            diagnostics.decode_image(image, "PNG", 4)


if __name__ == "__main__":
    unittest.main()
