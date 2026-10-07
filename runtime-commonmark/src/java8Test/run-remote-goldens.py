#!/usr/bin/env python3
"""Remote-only CommonMark source compilation and genuine VM golden checks.

Use a modern JDK compiler and actual Java 8/17 executables. Optional engine goldens require the complete canonical modern engine, never stubs.
The standalone parser/API/actual-Java8 proof does not depend on engine compilation.
"""
import argparse
import hashlib
import os
import re
from pathlib import Path
import subprocess
import zipfile


def run(command, output=None):
    result = subprocess.run([str(x) for x in command], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if output:
        output.write_bytes(result.stdout)
    return result.stdout


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


# Exact compiler-only access markers observed in the real Java8 class artifact.
# No wildcard is accepted. Original private constructor visibility is unchanged.
JAVA8_ACCESS_MARKERS = {'org/commonmark/ext/gfm/tables/internal/TableBlockParser$1.class': [('org/commonmark/ext/gfm/tables/internal/TableBlockParser', '(Ljava/util/List;Lorg/commonmark/parser/SourceLine;)V')], 'org/commonmark/internal/HtmlBlockParser$1.class': [('org/commonmark/internal/HtmlBlockParser', '(Ljava/util/regex/Pattern;)V')], 'org/commonmark/node/Nodes$1.class': [('org/commonmark/node/Nodes$NodeIterable', '(Lorg/commonmark/node/Node;Lorg/commonmark/node/Node;)V'), ('org/commonmark/node/Nodes$NodeIterator', '(Lorg/commonmark/node/Node;Lorg/commonmark/node/Node;)V')], 'org/commonmark/parser/Parser$1.class': [('org/commonmark/parser/Parser', '(Lorg/commonmark/parser/Parser$Builder;)V')], 'org/commonmark/renderer/html/CoreHtmlNodeRenderer$1.class': [('org/commonmark/renderer/html/CoreHtmlNodeRenderer$AltTextVisitor', '()V')], 'org/commonmark/renderer/markdown/CoreMarkdownNodeRenderer$1.class': [('org/commonmark/renderer/markdown/CoreMarkdownNodeRenderer$LineBreakVisitor', '()V')], 'org/commonmark/text/AsciiMatcher$1.class': [('org/commonmark/text/AsciiMatcher', '(Lorg/commonmark/text/AsciiMatcher$Builder;)V'), ('org/commonmark/text/AsciiMatcher$Builder', '(Ljava/util/BitSet;)V')]}


def verify_access_markers(javap, jar, output):
    evidence = []
    for name, bridges in sorted(JAVA8_ACCESS_MARKERS.items()):
        owner = name[:-8]
        text = run([javap, "-classpath", jar, "-v", "-p", name[:-6].replace("/", ".")]).decode("utf-8")
        if not all(token in text for token in ["major version: 52", "flags: (0x1020) ACC_SUPER, ACC_SYNTHETIC",
                 "// java/lang/Object", "interfaces: 0, fields: 0, methods: 0, attributes: 3",
                 'SourceFile: "' + owner.rsplit("/", 1)[-1] + '.java"',
                 "// " + owner.replace("/", "."), "EnclosingMethod:"]):
            raise ValueError("Access marker structure/provenance differs: " + name)
        evidence.append(text)
        for carrier, original_descriptor in bridges:
            original_prefix = original_descriptor[:-2]
            bridge_descriptor = original_prefix + "L" + name[:-6] + ";)V"
            owner_text = run([javap, "-classpath", jar, "-v", "-p", carrier.replace("/", ".")]).decode("utf-8")
            # Read actual method descriptors/flags/code; do not infer from $ names.
            blocks = re.findall(r"^  ([^\n]+)\n    descriptor: ([^\n]+)\n    flags: ([^\n]+)\n(.*?)(?=^  \S|^}|\Z)", owner_text, re.M | re.S)
            constructors = {descriptor: (header, flags, body) for header, descriptor, flags, body in blocks
                            if carrier.replace("/", ".") + "(" in header}
            if original_descriptor not in constructors or "ACC_PRIVATE" not in constructors[original_descriptor][1]:
                raise ValueError("Original private constructor missing: " + carrier)
            if bridge_descriptor not in constructors or constructors[bridge_descriptor][1] != "(0x1000) ACC_SYNTHETIC":
                raise ValueError("Expected non-public synthetic constructor bridge missing: " + carrier)
            body = constructors[bridge_descriptor][2]
            instructions = re.findall(r"^\s+\d+: (.+)$", body, re.M)
            if len(instructions) < 3 or instructions[-1] != "return":
                raise ValueError("Access bridge has unexpected executable code: " + carrier)
            if not all(re.fullmatch(r"aload_[0-3]|aload\s+\d+", op) for op in instructions[:-2]):
                raise ValueError("Access bridge does more than load original constructor arguments: " + carrier)
            invocation = instructions[-2]
            if not re.fullmatch(r'invokespecial\s+#\d+\s+// Method "<init>":' + re.escape(original_descriptor), invocation):
                raise ValueError("Access bridge does not delegate to the original private constructor: " + carrier)
            evidence.append(owner_text)
    output.write_text("\n".join(evidence), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ["javac", "javap", "java8", "java17", "core", "tables", "source_core", "source_tables", "source_root", "output"]:
        parser.add_argument("--" + name.replace("_", "-"), required=True)
    parser.add_argument("--engine")
    parser.add_argument("--engine-support")
    parser.add_argument("--ported-jar")
    parser.add_argument("--sources-jar")
    args = parser.parse_args()
    if bool(args.engine) != bool(args.engine_support):
        parser.error("--engine and --engine-support must be supplied together")
    if bool(args.ported_jar) != bool(args.sources_jar):
        parser.error("--ported-jar and --sources-jar must be supplied together")
    source_root = Path(args.source_root).resolve()
    out = Path(args.output).resolve()
    out.mkdir(parents=True, exist_ok=False)
    run([args.java8, "-version"], out / "java8-version.txt")
    if b'1.8.' not in (out / "java8-version.txt").read_bytes():
        raise ValueError("Java8 gate requires an actual Java8 runtime")
    run([args.java17, "-version"], out / "modern-version.txt")
    prepare = source_root / "scripts/prepare-commonmark-sources.py"
    patches = source_root / "runtime-commonmark/patches"
    generated = out / "generated"
    run(["python3", "-B", prepare, args.source_core, args.source_tables,
         patches / "commonmark-source-manifest.json", patches / "commonmark-java8-hunks.json", generated])
    source_files = sorted((generated / "java").rglob("*.java"))
    classes = out / "classes"
    classes.mkdir()
    argfile = out / "javac-sources.txt"
    argfile.write_text("\n".join('"' + str(p) + '"' for p in source_files) + "\n", encoding="utf-8")
    run([args.javac, "--release", "8", "-Xpkginfo:always", "-encoding", "UTF-8", "-d", classes, "@" + str(argfile)], out / "compile-java8.txt")
    port = out / "openallay-commonmark.jar"
    with zipfile.ZipFile(port, "w", zipfile.ZIP_DEFLATED) as jar:
        for directory in [classes, generated / "resources"]:
            for path in sorted(directory.rglob("*")):
                if path.is_file():
                    jar.write(path, path.relative_to(directory).as_posix())
    upstream_names = set()
    for archive in [Path(args.core), Path(args.tables)]:
        with zipfile.ZipFile(archive) as jar:
            upstream_names.update(n for n in jar.namelist() if n.endswith(".class") and n != "module-info.class" and not n.startswith("META-INF/versions/"))
    with zipfile.ZipFile(port) as jar:
        port_names = {n for n in jar.namelist() if n.endswith(".class")}
        if not upstream_names.issubset(port_names):
            raise ValueError("Ordinary upstream binary classes missing: " + str(upstream_names - port_names))
        extra = port_names - upstream_names
        compatibility_classes = {"org/commonmark/internal/util/Java8Collections.class",
                     "org/commonmark/internal/util/Java8Collections$NullRejectList.class",
                     "org/commonmark/internal/util/Java8Collections$NullRejectSet.class",
                     "org/commonmark/internal/util/Java8Collections$NullRejectMap.class"}
        if extra != compatibility_classes | set(JAVA8_ACCESS_MARKERS):
            raise ValueError("Unexpected produced classes: " + str(extra))
        for name in port_names:
            data = jar.read(name)
            if data[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(data[6:8], "big") != 52:
                raise ValueError("Non-Java8 compiled class: " + name)
    if len(upstream_names) != 202 or len(port_names) != 213:
        raise ValueError("Expected 202 upstream classes, four compatibility classes, and seven verified Java8 access markers")
    verify_access_markers(args.javap, port, out / "java8-access-markers-independent.txt")
    if args.ported_jar:
        verify_access_markers(args.javap, args.ported_jar, out / "java8-access-markers-canonical.txt")
        with zipfile.ZipFile(args.ported_jar) as published, zipfile.ZipFile(port) as compiled:
            actual_names = {n for n in published.namelist() if n.endswith(".class")}
            if actual_names != port_names:
                raise ValueError("Canonical Gradle artifact class closure differs")
            for name in actual_names:
                data = published.read(name)
                if data[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(data[6:8], "big") != 52:
                    raise ValueError("Canonical Gradle artifact contains non-Java8 classes")
            for path in (generated / "resources").rglob("*"):
                if path.is_file() and published.read(path.relative_to(generated / "resources").as_posix()) != path.read_bytes():
                    raise ValueError("Canonical artifact upstream resource/license differs")
            for path in patches.iterdir():
                if published.read("META-INF/openallay/commonmark-source-changes/" + path.name) != path.read_bytes():
                    raise ValueError("Canonical artifact provenance differs")
            for name in ["THIRD-PARTY-NOTICES.txt", "LICENSE-commonmark.txt"]:
                if published.read("META-INF/licenses/commonmark/" + name) != (source_root / "runtime-commonmark" / name).read_bytes():
                    raise ValueError("Canonical binary license/notice differs")
        with zipfile.ZipFile(args.sources_jar) as published:
            actual_sources = {n for n in published.namelist() if n.endswith(".java")}
            expected_sources = {p.relative_to(generated / "java").as_posix() for p in source_files}
            if actual_sources != expected_sources:
                raise ValueError("Published modified source closure differs")
            for path in source_files:
                if published.read(path.relative_to(generated / "java").as_posix()) != path.read_bytes():
                    raise ValueError("Published modified source bytes differ")
            for path in (generated / "resources").rglob("*"):
                if path.is_file() and published.read(path.relative_to(generated / "resources").as_posix()) != path.read_bytes():
                    raise ValueError("Published source resource/license differs")
            for name in ["THIRD-PARTY-NOTICES.txt", "LICENSE-commonmark.txt"]:
                if published.read("META-INF/licenses/commonmark/" + name) != (source_root / "runtime-commonmark" / name).read_bytes():
                    raise ValueError("Published sources license/notice differs")
        port = Path(args.ported_jar).resolve()
    class_names = sorted(n[:-6].replace("/", ".") for n in upstream_names)
    old_cp = os.pathsep.join([args.core, args.tables])
    def api(classpath):
        data = run([args.javap, "-classpath", classpath, "-public", "-s"] + class_names)
        return b"\n".join(line for line in data.splitlines() if not line.startswith(b"Compiled from ")) + b"\n"
    original_api = api(old_cp)
    port_api = api(str(port))
    (out / "api-original.txt").write_bytes(original_api)
    (out / "api-java8.txt").write_bytes(port_api)
    if original_api != port_api:
        raise ValueError("Public CommonMark binary/API descriptors changed")
    fixture_dir = source_root / "runtime-commonmark/src/java8Test"
    parser_classes = out / "parser-fixture"
    parser_classes.mkdir()
    run([args.javac, "--release", "8", "-cp", old_cp, "-d", parser_classes, fixture_dir / "RealParserGolden.java"])
    run([args.javac, "--release", "8", "-d", parser_classes, fixture_dir / "ImmutableFactoriesGolden.java"])
    vector_file = fixture_dir / "vectors.base64"
    baseline_cp = str(parser_classes) + os.pathsep + old_cp
    port_cp = str(parser_classes) + os.pathsep + str(port)
    original_factories = run([args.java17, "-Dgolden.upstream=true", "-cp", baseline_cp,
                              "ImmutableFactoriesGolden"], out / "factories-upstream-modern.txt")
    ported_factories = run([args.java8, "-Xverify:all", "-cp", port_cp,
                            "ImmutableFactoriesGolden"], out / "factories-ported-java8.txt")
    if original_factories != ported_factories:
        raise ValueError("Immutable factory null/duplicate/alias/mutation contracts changed")
    original = run([args.java17, "-cp", baseline_cp, "RealParserGolden", vector_file], out / "parser-upstream-modern.txt")
    modern = run([args.java17, "-cp", port_cp, "RealParserGolden", vector_file], out / "parser-ported-modern.txt")
    java8 = run([args.java8, "-Xverify:all", "-cp", port_cp, "RealParserGolden", vector_file], out / "parser-ported-java8.txt")
    if original != modern or original != java8:
        raise ValueError("Real parser/source spans/table/renderers golden mismatch")
    if args.engine:
        # First compile the same fixture once against the full, existing engine.
        # The support closure must exclude both upstream CommonMark JARs.
        engine_support = args.engine_support
        for item in engine_support.split(os.pathsep):
            if "commonmark" in Path(item).name.lower():
                raise ValueError("engine-support must exclude CommonMark runtime archives")
        engine_classes = out / "engine-fixture"
        engine_classes.mkdir()
        compile_cp = os.pathsep.join([args.engine, engine_support, old_cp])
        run([args.javac, "--release", "17", "-cp", compile_cp, "-d", engine_classes, fixture_dir / "SemanticParserGolden.java"])
        baseline_cp = os.pathsep.join([str(engine_classes), args.engine, engine_support, old_cp])
        port_cp = os.pathsep.join([str(engine_classes), args.engine, engine_support, str(port)])
        original = run([args.java17, "-cp", baseline_cp, "SemanticParserGolden", vector_file], out / "engine-semantic-upstream.txt")
        ported = run([args.java17, "-cp", port_cp, "SemanticParserGolden", vector_file], out / "engine-semantic-java8-library.txt")
        if original != ported:
            raise ValueError("Full canonical engine semantic AST/fallback/diagnostics golden mismatch")
    records = ["ordinary_upstream_classes=" + str(len(upstream_names)), "ported_classes=" + str(len(port_names)), "vectors=10"]
    for name in ["openallay-commonmark.jar", "api-original.txt", "api-java8.txt", "parser-upstream-modern.txt", "parser-ported-modern.txt", "parser-ported-java8.txt", "engine-semantic-upstream.txt", "engine-semantic-java8-library.txt", "factories-upstream-modern.txt", "factories-ported-java8.txt"]:
        if (out / name).is_file():
            records.append(name + " sha256=" + sha256(out / name))
    records.append("engine_goldens=" + ("passed" if args.engine else "not_requested"))
    if args.ported_jar:
        records.append("canonical_runtime_sha256=" + sha256(Path(args.ported_jar)))
        records.append("canonical_sources_sha256=" + sha256(Path(args.sources_jar)))
    (out / "PASSED.txt").write_text("\n".join(records) + "\n", encoding="utf-8")
    print((out / "PASSED.txt").read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
