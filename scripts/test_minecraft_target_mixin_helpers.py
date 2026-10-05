"""Offline Mixin package, source-family, and lifetime contracts.

These tests read the actual selected source/config declarations. They do not load
Mixin, compile Java, or prove native startup. Runtime acceptance remains required.
"""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
MIXIN = re.compile(r"@Mixin\s*\(")
DECLARED_MIXIN = re.compile(r"^@(?:org\.spongepowered\.asm\.mixin\.)?Mixin\s*\([^\r\n]*\)\s*(?:public\s+)?(?:abstract\s+)?(?:class|interface)\s+([\w$]+)\b", re.MULTILINE)
SHARED_STATE = "dev/openallay/client/gui/MinecraftTeardownState.java"
ACCESSOR = "dev/openallay/client/gui/mixin/MinecraftTeardownAccess.java"
STATE_MIXIN = "dev/openallay/client/gui/mixin/MinecraftTeardownStateMixin.java"
WINDOW_STATE = "dev/openallay/client/gui/GuideNativeWindowState.java"
WINDOW = "dev/openallay/client/gui/MinecraftClientWindow.java"


def source(path):
    return path.read_text(encoding="utf-8")


def declared_map(root, name):
    text = source(root / "gradle/minecraft-targets.gradle")
    match = re.search(r"def " + re.escape(name) + r" = \[(.*?)\]", text)
    if match is None:
        raise ValueError("Missing source-family map: " + name)
    pairs = re.findall(r"'([^']+)':\s*'([^']+)'", match.group(1))
    if not pairs or len({key for key, _ in pairs}) != len(pairs):
        raise ValueError("Empty or duplicate source-family map: " + name)
    return dict(pairs)


def selected_layers(root, target):
    families = declared_map(root, "nativeFamilies")
    parents = declared_map(root, "nativeFamilyParents")
    layers = [families[target]]
    while layers[0] in parents:
        parent = parents[layers[0]]
        if parent in layers:
            raise ValueError("Source-family cycle")
        layers.insert(0, parent)
    if target not in layers:
        layers.append(target)
    return layers


def selected_files(root, module, target, kind):
    """Model only the checked-in exact-relative-path replacement rule."""
    selected = {}
    roots = [root / module / "src/main" / kind]
    roots += [root / module / "src/targets" / layer / kind
              for layer in selected_layers(root, target)]
    for directory in roots:
        if not directory.is_dir():
            continue
        for path in directory.rglob("*"):
            if path.is_file() and (kind != "java" or path.suffix == ".java"):
                selected[path.relative_to(directory).as_posix()] = path
    return selected


def mixin_package_violations(java, configs, read=None):
    """Classify by actual annotations and configuration, never Accessor names."""
    violations = []
    read = source if read is None else read
    for path in java.values():
        text = read(path)
        package = PACKAGE.search(text)
        if package is None:
            continue
        owners = [name for name, config in configs.items()
                  if package.group(1) == config["package"]
                  or package.group(1).startswith(config["package"] + ".")]
        if owners and MIXIN.search(text) is None:
            violations.append((path, owners))
    return violations


def legacy_depth_contract(text, descriptors):
    """Reject changes to real Java depth bodies/selectors; no fake game execution."""
    getter = re.search(r"boolean\s+openallay\$teardownInProgress\(\)\s*\{([^{}]*)\}", text)
    begin = re.search(r"void\s+openallay\$beginTeardown\([^)]*\)\s*\{([^{}]*)\}", text)
    end = re.search(r"void\s+openallay\$endTeardown\([^)]*\)\s*\{([^{}]*)\}", text)
    if not all((getter, begin, end)):
        return False
    compact = lambda body: re.sub(r"\s+", "", body)
    if (compact(getter.group(1)) != "returnopenallay$teardownDepth>0;"
            or compact(begin.group(1)) != "openallay$teardownDepth++;"
            or compact(end.group(1)) != "openallay$teardownDepth--;"
            or not re.search(r"@Unique\s+private\s+int\s+openallay\$teardownDepth\s*;", text)):
        return False
    injections = re.findall(r'@Inject\(method\s*=\s*\{([^}]*)\},\s*at\s*=\s*@At\("(HEAD|RETURN)"\)\)', text)
    return (len(injections) == 2
            and {at for _, at in injections} == {"HEAD", "RETURN"}
            and all(re.findall(r'"([^"]+)"', methods) == descriptors
                    for methods, _ in injections))


def configured_bindings(java, configs, read=None):
    """Independent ownership model; does not execute the Gradle closure or AP."""
    read = source if read is None else read
    packages, configured = set(), set()
    for config in configs.values():
        package = config.get("package")
        if not isinstance(package, str) or not package:
            raise ValueError("missing Mixin package")
        packages.add(package)
        for scope in ("mixins", "client", "server"):
            names = config.get(scope, [])
            if not isinstance(names, list) or any(not isinstance(name, str) or not name for name in names):
                raise ValueError("invalid Mixin declarations")
            for name in names:
                identity = package + "." + name
                if identity in configured:
                    raise ValueError("duplicate configured Mixin")
                configured.add(identity)
    declarations = {}
    for relative, path in java.items():
        text = re.sub(r"/\*.*?\*/|//[^\r\n]*", "", read(path), flags=re.DOTALL)
        match = PACKAGE.search(text)
        package = match.group(1) if match else ""
        expected = (package.replace(".", "/") + "/" if package else "") + path.name
        if relative != expected:
            raise ValueError("package/file mismatch")
        identity = (package + "." if package else "") + path.stem
        if identity in declarations:
            raise ValueError("duplicate source identity")
        declared = DECLARED_MIXIN.search(text)
        declarations[identity] = (path, package, text, declared is not None and declared.group(1) == path.stem)
    for identity in configured:
        if identity not in declarations or not declarations[identity][3]:
            raise ValueError("configured Mixin needs actual source")
    excluded = {}
    for identity, (path, package, text, mixin) in declarations.items():
        reserved = any(package == owner or package.startswith(owner + ".") for owner in packages)
        if reserved and not mixin:
            raise ValueError("ordinary source in reserved package")
        if reserved and identity not in configured:
            excluded[identity] = path
    for identity, (path, package, text, mixin) in declarations.items():
        if identity in excluded:
            continue
        text = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', "", text)
        for binding in excluded:
            owner, simple = binding.rsplit(".", 1)
            qualified = re.search(r"(?<![\w$])" + re.escape(binding) + r"(?![\w$])", text)
            visible = package == owner or ("import " + owner + ".*;") in text
            if qualified or (visible and re.search(r"(?<![\w$])" + re.escape(simple) + r"(?![\w$])", text)):
                raise ValueError("direct reference to unconfigured Mixin")
    return configured, excluded


def pure_subtitle_accessor(text):
    """An accessor interface for class Gui cannot contain concrete default behavior."""
    return ("@Mixin(Gui.class)" in text and "public interface GuiSubtitleAccess" in text
            and re.search(r"\bdefault\s", text) is None and ".render(" not in text
            and re.search(r'@(Accessor|Invoker)\("[^"]+"\).*;', text) is not None)


def immediate_1211_frame_contract(text):
    """Exact callback boundaries; official 1.21.1 render has one final flush, not two."""
    text = re.sub(r"/\*.*?\*/|//[^\r\n]*", "", text, flags=re.DOTALL)
    compact = lambda value: re.sub(r"\s+", "", value)
    method = 'method="render(Lnet/minecraft/client/DeltaTracker;Z)V"'
    constructor = ('Lnet/minecraft/client/gui/GuiGraphics;<init>'
                   '(Lnet/minecraft/client/Minecraft;'
                   'Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V')
    expected = {
        "worldFrame": (method + ',at=@At(value="INVOKE",target="' + constructor
                       + '",shift=At.Shift.AFTER),require=1', "beforeGui"),
        "gameUiFrame": (method + ',at=@At(value="INVOKE",'
                        'target="Lnet/minecraft/client/gui/GuiGraphics;flush()V",'
                        'ordinal=0,shift=At.Shift.AFTER),require=1', "afterGui")}
    callbacks = re.findall(
        r"@Inject\((.*?)\)\s*private\s+void\s+openallay\$(\w+)"
        r"\(([^)]*)\)\s*\{([^{}]*)\}", text, re.DOTALL)
    if len(callbacks) != 2 or {name for _, name, _, _ in callbacks} != expected.keys():
        return False
    for annotation, name, parameters, body in callbacks:
        selector, capture = expected[name]
        if (compact(annotation) != selector
                or compact(parameters) != "DeltaTrackerdeltaTracker,booleanadvanceGameTime,CallbackInfocallback"
                or compact(body) != ("MinecraftClientViewCapture." + capture
                                     + "(Minecraft.getInstance(),advanceGameTime);")):
            return False
    return ("@Mixin(GameRenderer.class)" in compact(text)
            and "import net.minecraft.client.DeltaTracker;" in text
            and len(re.findall(r"@Inject\(", text)) == 2)


class MinecraftTargetMixinHelpersTest(unittest.TestCase):
    def source(self, path):
        return source(path)

    def test_source_model_matches_real_build_rule(self):
        family = self.source(ROOT / "gradle/minecraft-source-family.gradle")
        self.assertIn("roots.drop(index + 1).any", family)
        self.assertIn("new File(it, element.relativePath.pathString).isFile()", family)
        loader = self.source(ROOT / "build-logic/src/main/groovy/multiloader-loader.gradle")
        self.assertIn("source(commonSources.allJava)", loader)
        self.assertIn("from(commonSources.resources)", loader)
        self.assertEqual("1.20.1", selected_layers(ROOT, "1.20.1")[-1])

    def test_ordinary_teardown_state_is_one_shared_non_mixin_contract(self):
        expected = ROOT / "common/src/targets/1.21.8/java" / SHARED_STATE
        text = self.source(expected)
        self.assertIn("package dev.openallay.client.gui;", text)
        self.assertIn("public interface MinecraftTeardownState", text)
        self.assertIn("boolean openallay$teardownInProgress();", text)
        self.assertIsNone(MIXIN.search(text))
        declared = {selected_files(ROOT, "common", target, "java").get(SHARED_STATE)
                    for target in declared_map(ROOT, "nativeFamilies")}
        self.assertEqual({None, expected}, declared)
        for target in declared_map(ROOT, "nativeFamilies"):
            with self.subTest(target=target):
                java = selected_files(ROOT, "common", target, "java")
                if "1.21.8" in selected_layers(ROOT, target):
                    self.assertEqual(expected, java[SHARED_STATE])
                else:
                    self.assertNotIn(SHARED_STATE, java)
        for target in ("1.20.1", "1.20.2"):
            java = selected_files(ROOT, "common", target, "java")
            self.assertEqual(ROOT / "common/src/targets/1.21.8/java" / ACCESSOR, java[ACCESSOR])
            self.assertIn("implements MinecraftTeardownState", self.source(java[STATE_MIXIN]))
            self.assertIn("((MinecraftTeardownState) client).openallay$teardownInProgress()",
                          self.source(java[WINDOW_STATE]))
            resources = selected_files(ROOT, "common", target, "resources")
            config = json.loads(self.source(resources["openallay.client.mixins.json"]))
            self.assertIn("MinecraftTeardownStateMixin", config["client"])
            self.assertNotIn("MinecraftTeardownAccess", config["client"])
            if target == "1.20.1":
                self.assertEqual("openallay.refmap.json", config["refmap"])

    def test_true_accessor_and_selected_consumers_preserve_typed_state(self):
        accessor = self.source(ROOT / "common/src/targets/1.21.8/java" / ACCESSOR)
        self.assertIn("@Mixin(Minecraft.class)", accessor)
        self.assertIn("public interface MinecraftTeardownAccess extends MinecraftTeardownState", accessor)
        self.assertIn('@Accessor("clientLevelTeardownInProgress") boolean openallay$teardownInProgress();', accessor)
        consumer_families = {"1.20.1": "1.20.1", "1.20.2": "1.20.4", "1.20.3": "1.20.4",
                             "1.20.4": "1.20.4", "1.20.5": "1.20.6", "1.20.6": "1.20.6",
                             "1.21": "1.21.1", "1.21.1": "1.21.1"}
        for target in declared_map(ROOT, "nativeFamilies"):
            java = selected_files(ROOT, "common", target, "java")
            if "1.21.8" not in selected_layers(ROOT, target):
                continue
            with self.subTest(target=target):
                for relative in (WINDOW_STATE, WINDOW):
                    self.assertNotIn("dev.openallay.client.gui.mixin.MinecraftTeardownAccess",
                                     self.source(java[relative]))
                if target in consumer_families:
                    self.assertEqual(ROOT / "common/src/targets" / consumer_families[target] / "java" / WINDOW_STATE,
                                     java[WINDOW_STATE])
                    self.assertIn("((MinecraftTeardownState) client).openallay$teardownInProgress()",
                                  self.source(java[WINDOW_STATE]))
                else:
                    self.assertIn("!((MinecraftTeardownState) minecraft).openallay$teardownInProgress()",
                                  self.source(java[WINDOW]))
                resources = selected_files(ROOT, "common", target, "resources")
                config = json.loads(self.source(resources["openallay.client.mixins.json"]))
                self.assertTrue(config["required"])
                self.assertEqual(1, config["injectors"]["defaultRequire"])
                if target not in ("1.20.1", "1.20.2"):
                    self.assertIn("MinecraftTeardownAccess", config["client"])

    def test_every_reserved_package_class_has_actual_mixin_annotation(self):
        for target in declared_map(ROOT, "nativeFamilies"):
            for loader in ("fabric", "neoforge"):
                with self.subTest(target=target, loader=loader):
                    java, resources = {}, {}
                    for module in ("common", loader):
                        selected = selected_files(ROOT, module, target, "java")
                        self.assertFalse(java.keys() & selected.keys(), "Duplicate selected Java path")
                        java.update(selected)
                        selected = selected_files(ROOT, module, target, "resources")
                        self.assertFalse(resources.keys() & selected.keys(), "Duplicate selected resource")
                        resources.update(selected)
                    configs = {name: json.loads(self.source(path)) for name, path in resources.items()
                               if name.endswith(".mixins.json")}
                    self.assertEqual([], mixin_package_violations(java, configs))
                    for config in configs.values():
                        for kind in ("mixins", "client", "server"):
                            for name in config.get(kind, []):
                                relative = (config["package"] + "." + name).replace(".", "/") + ".java"
                                self.assertIn(relative, java)
                                self.assertIsNotNone(MIXIN.search(self.source(java[relative])))

    def test_reserved_package_guard_rejects_plain_helper_not_actual_accessor(self):
        path = ROOT / "plain-helper.java"
        configs = {"exact.mixins.json": {"package": "dev.openallay.client.gui.mixin"}}
        plain = "package dev.openallay.client.gui.mixin; public interface MinecraftTeardownAccess {}"
        self.assertEqual([(path, ["exact.mixins.json"])],
                         mixin_package_violations({"helper": path}, configs, lambda _: plain))
        actual = ("package dev.openallay.client.gui.mixin; @Mixin(Minecraft.class) "
                  "public interface MinecraftTeardownAccess { @Accessor(\"field\") boolean state(); }")
        self.assertEqual([], mixin_package_violations({"accessor": path}, configs, lambda _: actual))
        ordinary = "package dev.openallay.client.gui; public interface MinecraftTeardownState {}"
        self.assertEqual([], mixin_package_violations({"helper": path}, configs, lambda _: ordinary))

    def test_legacy_depth_bodies_and_native_scope_selectors_preserve_nested_lifetime(self):
        descriptors = {"1.20.1": ["clearLevel()V", "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V"],
                       "1.20.2": ["disconnect()V", "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V",
                                  "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V"]}
        for target, methods in descriptors.items():
            with self.subTest(target=target):
                text = self.source(ROOT / "common/src/targets" / target / "java" / STATE_MIXIN)
                self.assertTrue(legacy_depth_contract(text, methods))
                for old, new in (("openallay$teardownDepth > 0", "openallay$teardownDepth >= 0"),
                                 ("openallay$teardownDepth++", "openallay$teardownDepth = 1"),
                                 ("openallay$teardownDepth--", "openallay$teardownDepth = 0"),
                                 ('@At("RETURN")', '@At("HEAD")'),
                                 (methods[0], "fakeNativeAlias()V")):
                    self.assertFalse(legacy_depth_contract(text.replace(old, new), methods))

    def test_subtitle_mixins_are_pure_accessor_or_invoker_interfaces(self):
        relative = "dev/openallay/client/gui/mixin/GuiSubtitleAccess.java"
        for target in declared_map(ROOT, "nativeFamilies"):
            java = selected_files(ROOT, "common", target, "java")
            if relative not in java:
                continue
            with self.subTest(target=target):
                text = self.source(java[relative])
                self.assertTrue(pure_subtitle_accessor(text))
                bad = text.replace("\n}", "\n    default void concreteRender() {}\n}")
                self.assertFalse(pure_subtitle_accessor(bad))

    def test_exact_subtitle_family_selects_real_native_render_api(self):
        relative = "dev/openallay/client/gui/GuideNativeSubtitleRender.java"
        for target in declared_map(ROOT, "nativeFamilies"):
            java = selected_files(ROOT, "common", target, "java")
            if "1.21.8" not in selected_layers(ROOT, target):
                self.assertNotIn(relative, java)
                continue
            family = ("1.21.5" if target in ("1.20.1", "1.20.2", "1.20.3", "1.20.4",
                                            "1.20.5", "1.20.6", "1.21", "1.21.1",
                                            "1.21.2", "1.21.3", "1.21.4", "1.21.5") else "1.21.8")
            with self.subTest(target=target, family=family):
                self.assertEqual(ROOT / "common/src/targets" / family / "java" / relative, java[relative])
                text = self.source(java[relative])
                self.assertIn("package dev.openallay.client.gui;", text)
                if family == "1.21.5":
                    self.assertIn(".openallay$subtitleOverlay().render(graphics);", text)
                    self.assertNotIn("DeltaTracker", text)
                else:
                    self.assertIn(".openallay$renderSubtitles(graphics, client.getDeltaTracker());", text)
                window = self.source(java[WINDOW])
                if java[WINDOW] == ROOT / "common/src/targets/1.21.8/java" / WINDOW:
                    self.assertIn("GuideNativeSubtitleRender.render(minecraft, graphics.nativeGraphics());", window)
                else:
                    self.assertIn(".openallay$subtitleOverlay().render(graphics.nativeGraphics());", window)

    def test_configured_binding_rule_matches_all_real_module_source_closures(self):
        for target in declared_map(ROOT, "nativeFamilies"):
            for loader in ("fabric", "neoforge"):
                java, configs = {}, {}
                for module in ("common", loader):
                    selected = selected_files(ROOT, module, target, "java")
                    self.assertFalse(java.keys() & selected.keys())
                    java.update(selected)
                    resources = selected_files(ROOT, module, target, "resources")
                    for name, path in resources.items():
                        if name.endswith(".mixins.json"):
                            self.assertNotIn(name, configs)
                            configs[name] = json.loads(self.source(path))
                with self.subTest(target=target, loader=loader):
                    configured, excluded = configured_bindings(java, configs)
                    expected = ({"dev.openallay.client.gui.mixin.MinecraftTeardownAccess"}
                                if target in ("1.20.1", "1.20.2") else set())
                    self.assertEqual(expected, excluded.keys())
                    for config in configs.values():
                        for scope in ("mixins", "client", "server"):
                            self.assertTrue({config["package"] + "." + name for name in config.get(scope, [])}
                                            <= configured)
                    self.assertIn("dev.openallay.client.gui.mixin.GameRendererObservationMixin", configured)
                    if "openallay.fabric.client.mixins.json" in configs:
                        self.assertIn("dev.openallay.fabric.mixin.FabricGuideHudLayerMixin", configured)
                    self.assertNotIn("dev.openallay.client.gui.MinecraftTeardownState", excluded)
        family = self.source(ROOT / "gradle/minecraft-source-family.gradle")
        self.assertIn("selectConfiguredMinecraftMixins", family)
        self.assertIn("['mixins', 'client', 'server']", family)
        self.assertIn("Ordinary Java source in reserved Mixin package", family)
        self.assertIn("Configured Mixin must have one actual @Mixin source", family)
        self.assertIn("references unconfigured Mixin", family)
        self.assertNotIn("disableTargetValidator", family)
        self.assertIn("declarations.values()*.identity.countBy", family)
        self.assertLess(family.index("validateSources([sourceSets.main.allJava])"),
                        family.index("ext.minecraftMixinSelection ="))
        loader = self.source(ROOT / "build-logic/src/main/groovy/multiloader-loader.gradle")
        self.assertIn("commonProject.selectConfiguredMinecraftMixins(", loader)
        self.assertIn("[sourceSets.main.resources, commonSources.resources]", loader)
        self.assertIn("commonProject.minecraftMixinSelection", loader)

    def test_configured_binding_rejects_missing_ordinary_mismatched_and_referenced_sources(self):
        package = "demo.mixin"
        config = {"exact.mixins.json": {"package": package, "client": ["Active"]}}
        active = Path("Active.java")
        unused = Path("Unused.java")
        ordinary = Path("Native.java")
        java = {"demo/mixin/Active.java": active, "demo/mixin/Unused.java": unused, "demo/Native.java": ordinary}
        texts = {active: "package demo.mixin;\n@Mixin(Object.class)\npublic interface Active {}",
                 unused: "package demo.mixin;\n@Mixin(Object.class)\npublic interface Unused {}",
                 ordinary: "package demo; public class Native {}"}
        selected, excluded = configured_bindings(java, config, texts.__getitem__)
        self.assertEqual({"demo.mixin.Active"}, selected)
        self.assertEqual({"demo.mixin.Unused": unused}, excluded)
        cases = [({name: path for name, path in java.items() if path != active}, config, texts,
                  "configured Mixin"),
                 (java, {"wrong.json": {"package": "wrong.mixin", "client": ["Active"]}}, texts,
                  "configured Mixin"),
                 (java, config, {**texts, active: texts[active].replace("interface Active", "interface Wrong")},
                  "configured Mixin"),
                 (java, {**config, "duplicate.json": config["exact.mixins.json"]}, texts,
                  "duplicate configured"),
                 (java, config, {**texts, unused: "package demo.mixin; public interface Unused {}"},
                  "ordinary source"),
                 ({**java, "wrong/Active.java": java["demo/mixin/Active.java"]}, config, texts,
                  "package/file mismatch"),
                 (java, config, {**texts, ordinary: "package demo; public class Native implements demo.mixin.Unused {}"},
                  "direct reference"),
                 (java, config, {**texts, ordinary: "package demo; import demo.mixin.Unused; public class Native implements Unused {}"},
                  "direct reference"),
                 (java, config, {**texts, ordinary: "package demo; import demo.mixin.*; public class Native implements Unused {}"},
                  "direct reference")]
        for sources, configs, contents, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(ValueError, message):
                configured_bindings(sources, configs, contents.__getitem__)
        both = {"one.json": {"package": package, "mixins": ["Active"], "server": ["Unused"]}}
        self.assertEqual(({"demo.mixin.Active", "demo.mixin.Unused"}, {}),
                         configured_bindings(java, both, texts.__getitem__))

    def test_1_21_1_frame_hooks_use_sole_final_flush_and_keep_native_capture_admission(self):
        relative = "dev/openallay/client/gui/mixin/GameRendererObservationMixin.java"
        expected = ROOT / "common/src/targets/1.21.1/java" / relative
        for target in ("1.21", "1.21.1"):
            with self.subTest(target=target):
                java = selected_files(ROOT, "common", target, "java")
                self.assertEqual(expected, java[relative])
                text = self.source(java[relative])
                self.assertTrue(immediate_1211_frame_contract(text))
                config = json.loads(self.source(selected_files(ROOT, "common", target, "resources")
                                                ["openallay.client.mixins.json"]))
                self.assertTrue(config["required"])
                self.assertEqual(1, config["injectors"]["defaultRequire"])
                self.assertIn("GameRendererObservationMixin", config["client"])
                readback = self.source(java["dev/openallay/client/observation/MinecraftNativeImageCapture.java"])
                self.assertIn("Screenshot.takeScreenshot(Objects.requireNonNull(target", readback)
                self.assertIn("CompletableFuture.completedFuture(", readback)
        # Keep the native break local: other immediate/deferred/float render families are unchanged.
        for target in declared_map(ROOT, "nativeFamilies"):
            if target not in ("1.21", "1.21.1"):
                self.assertNotEqual(expected, selected_files(ROOT, "common", target, "java")[relative])
        text = self.source(expected)
        for old, new in (("ordinal = 0", "ordinal = 1"),
                         ("At.Shift.AFTER", "At.Shift.BEFORE"),
                         ('value = "INVOKE"', 'value = "TAIL"'),
                         ('render(Lnet/minecraft/client/DeltaTracker;Z)V', 'render'),
                         ("require = 1", "require = 0"),
                         ("advanceGameTime);", "true);"),
                         ("beforeGui(", "afterGui("),
                         ("afterGui(", "beforeGui("),
                         ("MinecraftClientViewCapture.afterGui(", "Minecraft.setScreen(")):
            with self.subTest(mutation=new):
                self.assertFalse(immediate_1211_frame_contract(text.replace(old, new)))
        capture = self.source(ROOT / "common/src/main/java/dev/openallay/client/observation/MinecraftClientViewCapture.java")
        for hook, target in (("beforeGui", "WORLD"), ("afterGui", "GAME_UI")):
            body = re.search(r"public static void " + hook + r"\([^)]*\)\s*\{(.*?)\n    \}",
                             capture, re.DOTALL).group(1)
            self.assertIn("!advanceGameTime || client.level == null", body)
            self.assertIn("GuideNativeWindowState.frameReady(client)", body)
            self.assertIn("capture.frame(WorldViewRequest.Target." + target + ");", body)
        self.assertIn("MinecraftNativeImageCapture.capture(nativeTarget)", capture)
        self.assertNotIn("setScreen(", capture)

    def test_1_20_1_frame_admission_still_rejects_native_teardown(self):
        text = self.source(ROOT / "common/src/targets/1.20.1/java" / WINDOW_STATE)
        self.assertRegex(text, r"return client\.isRunning\(\) && client\.level != null && client\.player != null\s*&& !teardownInProgress\(client\);")


if __name__ == "__main__":
    unittest.main()
