import copy
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("stock_prerequisite", ROOT / "stock-forge36-prerequisite.py")
stock = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stock)


class MetadataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.runtime, cls.launch, cls.freeze = stock.load_helpers(ROOT.parents[1])
        cls.install = json.loads((ROOT / "install_profile.json").read_text())
        cls.version = json.loads((ROOT / "version.json").read_text())
        cls.vanilla = json.loads((ROOT / "minecraft-1.16.5.json").read_text())

    def validate(self, install=None, version=None, vanilla=None):
        return stock.validate_metadata(install or self.install, version or self.version, vanilla or self.vanilla)

    def test_real_installer_identity_and_vanilla_fact(self):
        required = self.validate()
        self.assertEqual(6, len(required))
        self.assertEqual("cpw.mods.modlauncher.Launcher", self.version["mainClass"])
        self.assertEqual(8, self.vanilla["javaVersion"]["majorVersion"])
        self.assertEqual("17", stock.PINS["java_version"])

    def test_fake_bootstrap_or_vanilla_main_rejected(self):
        for main in ("cpw.mods.bootstraplauncher.BootstrapLauncher", "net.minecraft.client.main.Main"):
            bad = copy.deepcopy(self.version)
            bad["mainClass"] = main
            with self.assertRaises(ValueError):
                self.validate(version=bad)

    def test_asm72_or_duplicate_closure_rejected(self):
        bad = copy.deepcopy(self.version)
        next(l for l in bad["libraries"] if l["name"] == "org.ow2.asm:asm:9.6")["name"] = "org.ow2.asm:asm:7.2"
        with self.assertRaises(ValueError):
            self.validate(version=bad)
        bad = copy.deepcopy(self.version)
        bad["libraries"].append(next(l for l in bad["libraries"] if l["name"] == "cpw.mods:modlauncher:8.1.3"))
        with self.assertRaises(ValueError):
            self.validate(version=bad)

    def test_extra_module_open_rejected(self):
        bad = copy.deepcopy(self.version)
        bad["arguments"]["jvm"].append("--add-opens=java.base/java.lang=ALL-UNNAMED")
        with self.assertRaises(ValueError):
            self.validate(version=bad)

    def test_wrong_processor_shape_rejected(self):
        bad = copy.deepcopy(self.install)
        bad["processors"][2]["args"][0] = "--input"
        with self.assertRaises(ValueError):
            self.validate(install=bad)

    def test_metadata_entrypoint_has_no_runtime_actions(self):
        with patch.object(sys, "argv", ["stock", "--repo", str(ROOT.parents[1]), "--metadata-only"]), patch.object(stock, "prepare") as prepare, patch.object(stock, "boot") as boot:
            self.assertEqual(0, stock.main())
            prepare.assert_not_called()
            boot.assert_not_called()

    def test_existing_helpers_accept_exact_processor_output_verification(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory(prefix="forge36-source-mock-") as temporary:
            root = Path(temporary)
            outputs = {key: self.runtime.data_library(root, self.install, key)
                       for key in ("MAPPINGS", "MC_SLIM", "MC_EXTRA", "MC_SRG", "PATCHED")}
            for path in outputs.values():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"source-only mocked output")
            by_path = {str(outputs[key]): self.install["data"][key+"_SHA"]["client"].strip("'")
                       for key in ("MC_SLIM", "MC_EXTRA", "PATCHED")}
            with patch.object(self.runtime, "file_hash", side_effect=lambda path, algorithm="sha256": by_path[str(path)]):
                self.runtime.verify_processor_outputs(root, self.install, outputs)

    def test_empty_url_installer_library_verification_is_reusable(self):
        import hashlib
        from tempfile import TemporaryDirectory
        with TemporaryDirectory(prefix="forge36-source-mock-") as temporary:
            root = Path(temporary)
            content = b"mock bundled primary bytes"
            path = root / "libraries/forge/mock.jar"
            path.parent.mkdir(parents=True)
            path.write_bytes(content)
            metadata = {"libraries": [{"name": "net.minecraftforge:forge:mock",
                "downloads": {"artifact": {"path": "forge/mock.jar", "url": "",
                "sha1": hashlib.sha1(content).hexdigest(), "size": len(content)}}}]}
            self.runtime.verify_downloaded_libraries(metadata, root)
            path.write_bytes(b"bad")
            with self.assertRaises(ValueError):
                self.runtime.verify_downloaded_libraries(metadata, root)

    def test_full_prepare_orchestration_without_runtime_or_download(self):
        # All effects are mocked. Tiny placeholder output files are source-test fixtures only.
        import argparse
        from tempfile import TemporaryDirectory
        with TemporaryDirectory(prefix="forge36-source-mock-") as temporary:
            repo = Path(temporary).resolve()
            root = repo / "build/e2e/runtime/forge16165-stock/minecraft"
            java = repo / "mock-java"
            args = argparse.Namespace(repo=repo, minecraft_root=root, java=java, java_release="17.0.18+8")
            vanilla_path = root / "versions/1.16.5/1.16.5.jar"
            def claim(*unused):
                (root / ".provision").mkdir(parents=True)
                vanilla_path.parent.mkdir(parents=True)
                vanilla_path.write_bytes(b"mock vanilla")
            def installed(*unused):
                profile = root / "versions" / stock.PROFILE / (stock.PROFILE + ".json")
                profile.parent.mkdir(parents=True)
                profile.write_text(json.dumps(version))
                for key in ("MAPPINGS", "MC_SLIM", "MC_EXTRA", "MC_SRG", "PATCHED"):
                    path = self.runtime.data_library(root, self.install, key)
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(b"mock processor output")
            archive = MagicMock()
            archive.__enter__.return_value = archive
            archive.read.return_value = b"mock bundled library"
            archive.testzip.return_value = None
            bundled = [lib["downloads"]["artifact"] for meta in (self.install,self.version)
                       for lib in meta["libraries"] if not lib["downloads"]["artifact"]["url"]]
            # Only bundle bytes/length are substituted in this mock metadata copy;
            # exact release metadata identity is validated separately above.
            install = copy.deepcopy(self.install)
            version = copy.deepcopy(self.version)
            import hashlib
            for meta in (install,version):
                for lib in meta["libraries"]:
                    artifact=lib["downloads"]["artifact"]
                    if not artifact["url"]:
                        artifact.update(size=len(b"mock bundled library"), sha1=hashlib.sha1(b"mock bundled library").hexdigest())
            def file_hash(path, algorithm="sha256"):
                if Path(path) == vanilla_path and algorithm == "sha1":
                    return self.vanilla["downloads"]["client"]["sha1"]
                if Path(path).name.endswith("installer.jar"):
                    return self.freeze["installer"]["sha256"]
                return "a" * 64
            with patch.object(self.runtime,"check_java",return_value="Temurin version \"17.0.18\" +8"), \
                 patch.object(self.runtime,"claim_root",side_effect=claim), \
                 patch.object(self.runtime,"prepare_vanilla",return_value=(self.vanilla, [])) as vanilla, \
                 patch.object(self.runtime,"download", side_effect=lambda url,path,*a,**k: path.write_bytes(b"mock installer")) as download, \
                 patch.object(self.runtime,"inspect_installer",return_value=(install,version)), \
                 patch.object(self.runtime,"prepare_libraries",return_value=[]) as libraries, \
                 patch.object(self.runtime,"run_installer",side_effect=installed) as installer, \
                 patch.object(self.runtime,"verify_downloaded_libraries") as verify_inputs, \
                 patch.object(self.runtime,"verify_processor_outputs") as verify_outputs, \
                 patch.object(self.runtime,"file_hash",side_effect=file_hash), \
                 patch.object(self.launch,"prepare_assets",return_value=root.parent / "assets"), \
                 patch.object(stock.zipfile,"ZipFile",return_value=archive):
                observed = stock.prepare(args,self.runtime,self.launch,self.freeze,install,version,self.vanilla)
            self.assertEqual((root,java,root.parent / "assets"),observed)
            self.assertEqual("8", vanilla.call_args.args[1]["java_version"])
            self.assertTrue(all(lib["downloads"]["artifact"]["url"] for call in libraries.call_args_list
                                for lib in call.args[0]["libraries"]))
            self.assertEqual(3,verify_inputs.call_count)
            self.assertEqual(5,len(verify_outputs.call_args.args[2]))
            self.assertIn("--offline",installer.call_args.args[0])
            self.assertIn("--installClient",installer.call_args.args[0])
            self.assertEqual(2,archive.read.call_count)
            receipt=json.loads((root / ".provision/stock-runtime.json").read_text())
            self.assertFalse(receipt["gameLaunched"])
            self.assertEqual("17",receipt["pins"]["java_version"])

    def test_stock_boot_argv_and_capture_receipts_are_genuine_shapes(self):
        import argparse
        import subprocess
        from tempfile import TemporaryDirectory
        with TemporaryDirectory(prefix="forge36-source-mock-") as temporary:
            repo=Path(temporary).resolve()
            output=repo / "build/e2e/stock-test"
            root=repo / "build/e2e/runtime/forge16165-stock/minecraft"
            assets=root.parent / "assets"
            java=repo / "mock-java"
            args=argparse.Namespace(repo=repo,output=output)
            expected=self.validate()
            forge_lib=[(name,root / "libraries" / self.launch.maven_path(name)) for name in expected]
            old_asm=[("org.ow2.asm:asm:7.2",root / "libraries/old-asm.jar")]
            image=MagicMock()
            image.save.side_effect=lambda path: Path(path).write_bytes(b"mock X11 capture")
            imagegrab=MagicMock()
            imagegrab.grab.return_value=image
            process=MagicMock(pid=23456)
            process.wait.side_effect=subprocess.TimeoutExpired("mock-game",45)
            with patch.object(self.launch,"version_libraries",side_effect=[old_asm,forge_lib]), \
                 patch.object(stock,"inspect_classpath",return_value=[{"coordinate": "mock-closure"}]), \
                 patch.object(self.launch,"native_libraries",return_value=[]), \
                 patch.object(self.launch,"extract_natives",return_value={}), \
                 patch.object(stock.subprocess,"Popen",return_value=process) as popen, \
                 patch.object(stock,"stop_owned",return_value={"pid":23456,"signals":["SIGTERM"],"reaped":True}), \
                 patch.dict(sys.modules,{"PIL":MagicMock(ImageGrab=imagegrab)}), \
                 patch.dict(stock.os.environ,{"DISPLAY":":99"}):
                self.assertEqual(0,stock.boot(args,root,java,assets,self.runtime,self.launch,expected,self.vanilla,self.version))
            command=popen.call_args.args[0]
            self.assertEqual(1,command.count(stock.MAIN))
            self.assertEqual(stock.FLAGS,[v for v in command if v.startswith(("--add-","-XX:+Ignore"))])
            self.assertIn("fmlclient",command)
            self.assertIn("net.minecraftforge",command)
            self.assertNotIn(str(root / "libraries/old-asm.jar"),command[command.index("-cp")+1])
            self.assertEqual(str(assets),command[command.index("--assetsDir")+1])
            self.assertEqual("1.16",command[command.index("--assetIndex")+1])
            self.assertTrue(popen.call_args.kwargs["start_new_session"])
            self.assertEqual([],list((output / "game/mods").iterdir()))
            receipt=json.loads((output / "receipt.json").read_text())
            self.assertFalse(receipt["titleConfirmed"])
            self.assertEqual("captured-awaiting-title-review",receipt["status"])
            self.assertEqual([45,90],[frame["elapsedSeconds"] for frame in receipt["screenshots"]])
            self.assertTrue(receipt["termination"]["reaped"])

    def test_owned_termination_receipt(self):
        process = MagicMock(pid=23456)
        process.poll.side_effect = [None, None, -15]
        process.returncode = -15
        with patch.object(stock.os, "getpgid", return_value=23456), patch.object(stock.os, "killpg") as kill:
            receipt = stock.stop_owned(process)
        kill.assert_called_once_with(23456, stock.signal.SIGTERM)
        self.assertEqual({"pid", "initialExitCode", "signals", "finalExitCode", "reaped"}, set(receipt))
        self.assertTrue(receipt["reaped"])
        self.assertEqual(["SIGTERM"], receipt["signals"])

    def test_unowned_group_is_never_signalled(self):
        process = MagicMock(pid=23456)
        process.poll.return_value = None
        with patch.object(stock.os, "getpgid", return_value=999), patch.object(stock.os, "killpg") as kill:
            with self.assertRaises(RuntimeError):
                stock.stop_owned(process)
            kill.assert_not_called()


if __name__ == "__main__":
    unittest.main()
