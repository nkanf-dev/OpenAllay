import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile

SPEC = importlib.util.spec_from_file_location("ci_software_graphics", Path(__file__).with_name("run-ci-software-graphics.py"))
GRAPHICS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GRAPHICS)


class SoftwareGraphicsTest(unittest.TestCase):
    def make_runtime(self, base, sdl=True):
        root = Path(base).resolve()
        runtime = root / "build/e2e/runtime/26.3/minecraft"
        (runtime / ".provision").mkdir(parents=True)
        profile = root / "gradle/minecraft-targets/26.3.properties"
        profile.parent.mkdir(parents=True)
        profile.write_text("minecraft_version=26.3\njava_version=25\n")
        records = {}
        def record(path):
            data = path.read_bytes()
            value = {"sha1": hashlib.sha1(data).hexdigest(), "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
            records[path.relative_to(runtime).as_posix()] = value
            return value
        metadata = {"id": "26.3", "libraries": []}
        native = runtime / "libraries/org/lwjgl/lwjgl-sdl/3.4.3/lwjgl-sdl-3.4.3-natives-linux.jar"
        if sdl:
            native.parent.mkdir(parents=True)
            elf = bytearray(64)
            elf[:6] = b"\x7fELF\x02\x01"
            elf[16:18] = (3).to_bytes(2, "little")
            elf[18:20] = (62).to_bytes(2, "little")
            payload = bytes(elf) + b"fixture only; never loaded"
            prefix = "linux/x64/org/lwjgl/sdl/libSDL3.so"
            with zipfile.ZipFile(native, "w") as archive:
                archive.writestr(prefix, payload)
                archive.writestr("META-INF/" + prefix + ".sha1", hashlib.sha1(payload).hexdigest())
                archive.writestr("META-INF/" + prefix + ".git", "a" * 40)
            main = native.with_name("lwjgl-sdl-3.4.3.jar")
            with zipfile.ZipFile(main, "w") as archive:
                archive.writestr("org/lwjgl/sdl/SDLVideo.class", b"fixture class")
                archive.writestr("META-INF/" + prefix + ".sha1", hashlib.sha1(payload).hexdigest())
            for path, coordinate, rules in [(main, "org.lwjgl:lwjgl-sdl:3.4.3", []),
                    (native, "org.lwjgl:lwjgl-sdl:3.4.3:natives-linux", [{"action": "allow", "os": {"name": "linux"}}])]:
                value = record(path)
                metadata["libraries"].append({"name": coordinate, "rules": rules, "downloads": {"artifact": {
                    "path": path.relative_to(runtime / "libraries").as_posix(), "sha1": value["sha1"], "size": value["size"]}}})
        version = runtime / "versions/26.3/26.3.json"
        version.parent.mkdir(parents=True)
        version.write_text(json.dumps(metadata))
        record(version)
        receipt = {"loader": "fabric", "minecraft": "26.3", "minecraftRoot": str(runtime),
                   "mechanism": "official-client-installer", "sourceProfileSha256": hashlib.sha256(profile.read_bytes()).hexdigest(),
                   "files": records}
        (runtime / ".provision/fabric-runtime.json").write_text(json.dumps(receipt))
        return root, runtime, native

    def test_verified_supplier_and_class_bytes_are_recorded(self):
        with tempfile.TemporaryDirectory() as temporary:
            root, runtime, _ = self.make_runtime(temporary)
            selected = GRAPHICS.select_native(runtime, "fabric", "26.3", root)
            output = root / "diagnostics"
            output.mkdir()
            path, identity = GRAPHICS.extract_native(selected, output)
            self.assertEqual(path.name, "libSDL3.so")
            self.assertEqual(identity["supplierGit"], "a" * 40)
            self.assertEqual(identity["javaClassSha256"], hashlib.sha256(b"fixture class").hexdigest())
            self.assertEqual(identity["nativeSha1"], hashlib.sha1(path.read_bytes()).hexdigest())

    def test_changed_native_receipt_or_classifier_is_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            root, runtime, native = self.make_runtime(temporary)
            native.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "hash/size"):
                GRAPHICS.select_native(runtime, "fabric", "26.3", root)

    def test_supplier_native_sha_and_elf_architecture_are_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            root, runtime, native = self.make_runtime(temporary)
            selected = GRAPHICS.select_native(runtime, "fabric", "26.3", root)
            import warnings
            with warnings.catch_warnings(), zipfile.ZipFile(native, "a") as archive:
                warnings.simplefilter("ignore", UserWarning)
                archive.writestr("META-INF/linux/x64/org/lwjgl/sdl/libSDL3.so.sha1", "0" * 40)
            with self.assertRaises(ValueError):
                GRAPHICS.extract_native(selected, root)
            with self.assertRaisesRegex(ValueError, "ELF"):
                GRAPHICS.validate_elf(b"not a library")

    def test_glfw_environment_is_unchanged_and_sdl_uses_real_llvmpipe_egl(self):
        original = {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1", "TOKEN": "not logged"}
        self.assertEqual(GRAPHICS.graphics_environment(original, False), original)
        selected = GRAPHICS.graphics_environment(original, True)
        self.assertEqual(selected["SDL_VIDEO_DRIVER"], "x11")
        self.assertEqual(selected["SDL_VIDEO_FORCE_EGL"], "1")
        self.assertEqual(selected["GALLIUM_DRIVER"], "llvmpipe")
        self.assertNotIn("MESA_GL_VERSION_OVERRIDE", selected)
        self.assertEqual(original, {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1", "TOKEN": "not logged"})

    def test_readiness_failure_never_launches_any_acceptance_command(self):
        with tempfile.TemporaryDirectory() as temporary:
            root, runtime, _ = self.make_runtime(temporary)
            output = root / "build/ci-graphics/fabric-26.3"
            command = ["python3", "-B", "scripts/run-ci-game-workflow.py", "--scenarios", "builder-restricted", "builder-cancel", "ui-stop"]
            with mock.patch.object(GRAPHICS, "preflight", return_value={"ready": False, "failure": "real context failed"}), mock.patch.object(GRAPHICS.subprocess, "run") as launched:
                self.assertEqual(GRAPHICS.run(runtime, "fabric", "26.3", output, command, root), 1)
                launched.assert_not_called()
            report = json.loads((output / "readiness.json").read_text())
            self.assertEqual(report["gameAcceptance"], "NOT_RUN")
            self.assertFalse(report["ready"])

    def test_readiness_delegates_exact_command_and_preserves_failure_status(self):
        with tempfile.TemporaryDirectory() as temporary:
            root, runtime, _ = self.make_runtime(temporary, False)
            command = ["python3", "-B", "scripts/run-ci-game-workflow.py", "--scenarios", "builder-restricted", "builder-cancel", "ui-stop"]
            original = {"TEST_FIXTURE": "unchanged env"}
            with mock.patch.dict(GRAPHICS.os.environ, original, clear=True), mock.patch.object(GRAPHICS, "preflight") as probe, mock.patch.object(GRAPHICS.subprocess, "run", return_value=mock.Mock(returncode=7)) as launched:
                self.assertEqual(GRAPHICS.run(runtime, "fabric", "26.3", root / "build/ci-graphics/test", command, root), 7)
                self.assertEqual(launched.call_args.args[0], command)
                self.assertEqual(launched.call_args.kwargs["cwd"], root)
                self.assertEqual(launched.call_args.kwargs["env"], original)
                probe.assert_not_called()
            report = json.loads((root / "build/ci-graphics/test/readiness.json").read_text())
            self.assertEqual(report["gameAcceptance"], "DELEGATED_NOT_EVALUATED")
            self.assertEqual(report["gameCommandExitCode"], 7)
            self.assertEqual(report["preflight"], "NOT_APPLICABLE")
            self.assertIsNone(report["ready"])

    def test_selected_sdl_delegates_same_display_and_command_after_readiness(self):
        with tempfile.TemporaryDirectory() as temporary:
            root, runtime, _ = self.make_runtime(temporary)
            original = {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1"}
            command = ["python3", "-B", "scripts/run-ci-game-workflow.py", "--scenarios", "builder-restricted", "builder-cancel", "ui-stop"]
            with mock.patch.dict(GRAPHICS.os.environ, original, clear=True), mock.patch.object(GRAPHICS, "preflight", return_value={"ready": True}) as probe, mock.patch.object(GRAPHICS.subprocess, "run", return_value=mock.Mock(returncode=9)) as launched:
                self.assertEqual(GRAPHICS.run(runtime, "fabric", "26.3", root / "build/ci-graphics/test", command, root), 9)
                self.assertEqual(launched.call_args.args[0], command)
                self.assertEqual(launched.call_args.kwargs["env"]["DISPLAY"], ":99")
                self.assertEqual(launched.call_args.kwargs["env"], probe.call_args.args[2])
                self.assertEqual(launched.call_args.kwargs["env"]["SDL_VIDEO_FORCE_EGL"], "1")

    def test_context_requires_actual_core_forward_srgb_software(self):
        good = {"version": [4, 5], "profileMask": 1, "contextFlags": 1, "framebufferEncoding": 0x8C40, "renderer": "llvmpipe (LLVM)", "eglDisplay": True}
        GRAPHICS.validate_context(good)
        for name, value in [("version", [3, 2]), ("profileMask", 2), ("contextFlags", 0), ("framebufferEncoding", 0x2601), ("renderer", "physical GPU"), ("eglDisplay", False)]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                GRAPHICS.validate_context({**good, name: value})

    def test_changed_extracted_native_never_calls_ctypes(self):
        with tempfile.TemporaryDirectory() as temporary:
            native = Path(temporary) / "libSDL3.so"
            native.write_bytes(b"altered")
            with mock.patch.object(GRAPHICS.ctypes, "CDLL") as loaded:
                with self.assertRaisesRegex(ValueError, "changed before loading"):
                    GRAPHICS.sdl_probe(native, Path(temporary) / "result.json", "0" * 64)
                loaded.assert_not_called()

    def test_no_display_or_capability_override_is_ready(self):
        with tempfile.TemporaryDirectory() as temporary:
            for environment in ({"LIBGL_ALWAYS_SOFTWARE": "1"},
                                {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1", "MESA_GL_VERSION_OVERRIDE": "4.6"},
                                {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1", "SDL_VIDEO_EGL_SRGB_FRAMEBUFFER": "skip"}):
                with self.subTest(environment=environment), mock.patch.object(GRAPHICS, "diagnostic") as queried:
                    with self.assertRaises(ValueError):
                        GRAPHICS.preflight({"fixture": True}, Path(temporary), environment)
                    queried.assert_not_called()

    def test_real_child_command_has_exact_library_digest_and_timeout(self):
        with tempfile.TemporaryDirectory() as temporary:
            import gzip
            output = Path(temporary)
            with gzip.open(output / "closure.log.gz", "wt") as stream:
                stream.write("verified shared library closure")
            diagnostics = []
            def query(command, directory, name, environment, timeout=30):
                diagnostics.append((command, timeout))
                if name == "sdl-probe":
                    return {"exitCode": 124, "log": "unused.log.gz", "truncated": False}
                return {"exitCode": 0, "log": "closure.log.gz", "truncated": False}
            with mock.patch.object(GRAPHICS, "diagnostic", side_effect=query), mock.patch.object(GRAPHICS, "extract_native", return_value=(output / "libSDL3.so", {"nativeSha256": "a" * 64})):
                result = GRAPHICS.preflight({"fixture": True}, output, {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1"})
            self.assertFalse(result["ready"])
            self.assertEqual(diagnostics[-1][1], 30)
            self.assertEqual(diagnostics[-1][0][-2:], ["--probe-sha256", "a" * 64])
            self.assertIn("timed out", result["failure"])

    def test_success_exit_without_actual_context_report_is_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            import gzip
            output = Path(temporary)
            with gzip.open(output / "closure.log.gz", "wt") as stream:
                stream.write("verified shared library closure")
            with mock.patch.object(GRAPHICS, "diagnostic", return_value={"exitCode": 0, "log": "closure.log.gz", "truncated": False}), mock.patch.object(GRAPHICS, "extract_native", return_value=(output / "libSDL3.so", {"nativeSha256": "a" * 64})):
                result = GRAPHICS.preflight({"fixture": True}, output, {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1"})
            self.assertFalse(result["ready"])

    def test_probe_timeout_or_bad_json_cannot_be_ready(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            import gzip
            with gzip.open(output / "closure.log.gz", "wt") as stream:
                stream.write("diagnostic timed out")
            with mock.patch.object(GRAPHICS, "diagnostic", return_value={"exitCode": 124, "log": "closure.log.gz", "truncated": False}), mock.patch.object(GRAPHICS, "extract_native", return_value=(output / "libSDL3.so", {"supplierGit": "a" * 40})):
                result = GRAPHICS.preflight({"fixture": True}, output, {"DISPLAY": ":99", "LIBGL_ALWAYS_SOFTWARE": "1"})
                self.assertFalse(result["ready"])
                self.assertIn("closure", result["failure"])


if __name__ == "__main__":
    unittest.main()
