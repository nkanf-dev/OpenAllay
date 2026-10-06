#!/usr/bin/env python3
"""Retain bounded native contract bodies from the actual selected named game artifact."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

CLASSES = {
    "net.minecraft.client.player.LocalPlayer": ("commandSigned", "sendCommand", "sendChat"),
    "net.minecraft.client.gui.screens.ChatScreen": ("handleChatInput",),
    "net.minecraft.server.commands.HelpCommand": ("register", "lambda$register$1", "<clinit>"),
}
# The pre-slotCount manager needs exact admission, clearing and placement bodies.
# This runs inside the same core compile job, never a lookup-only CI job.
TOAST_CLASSES = {
    "net.minecraft.client.gui.components.toasts.ToastComponent": (
        "net.minecraft.client.gui.components.toasts.ToastComponent", "render", "getToast", "clear", "addToast", "getMinecraft"),
    "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance": (
        "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance", "render", "getToast", "getVisibility"),
    "net.minecraft.client.gui.components.toasts.Toast": ("render", "width", "height", "getToken"),
}
OUTPUT_LIMIT = 100 * 1024
TRANSIENT_LIMIT = 400 * 1024


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def method_blocks(text):
    methods = {}
    starts = list(re.finditer(r"(?m)^  (\S[^\n]*\([^\n]*\)[^\n]*;)$", text))
    for index, match in enumerate(starts):
        end = starts[index + 1].start() if index + 1 < len(starts) else text.rfind("\n}")
        declaration = match.group(1)
        name = declaration.split("(", 1)[0].rsplit(" ", 1)[-1]
        methods.setdefault(name, []).append(text[match.start():end].rstrip() + "\n")
    initializer = re.search(r"(?m)^  static \{\};$", text)
    if initializer:
        methods["<clinit>"] = [text[initializer.start():text.rfind("\n}")].rstrip() + "\n"]
    return methods


def selected_closure(methods, selectors, owner):
    selected = set(selectors) & set(methods)
    # Include local callees reached by the retained command methods, not unrelated methods.
    pending = list(selected)
    while pending:
        name = pending.pop()
        for body in methods[name]:
            for match in re.finditer(r"// (?:InterfaceMethod|Method) ([^: ]+):", body):
                reference = match.group(1).replace('"', '')
                prefix, separator, called = reference.rpartition(".")
                if separator and prefix != owner.replace(".", "/"):
                    continue
                if not separator:
                    called = reference
                if called in methods and called not in selected:
                    selected.add(called)
                    pending.append(called)
    return sorted(selected)


def capture(game_jar, javap, output, project, target):
    output.mkdir(parents=True, exist_ok=True)
    receipt = {"evidenceKind": "actual-selected-named-game-bytecode",
               "project": project, "minecraftTarget": target,
               "taskOutputProperty": project + ":createMinecraftArtifacts.gameJarArtifact",
               "gameJar": str(game_jar.resolve()), "gameJarSha256": sha256_file(game_jar),
               "javap": str(javap.resolve()), "classes": [],
               "gameCommandExecution": "not established by bytecode capture"}
    retained_bytes = 0
    classes = dict(CLASSES)
    if target == "1.18.2":
        classes["net.minecraft.client.player.LocalPlayer"] = ("chat",)
        classes["net.minecraft.client.KeyboardHandler"] = ("getClipboard", "setClipboard")
        classes.update(TOAST_CLASSES)
        receipt["toastGeometry"] = "functional-pending: inspect manager admission/32px placement/removal before game acceptance"
    with zipfile.ZipFile(game_jar) as archive:
        for owner, selectors in classes.items():
            member = owner.replace(".", "/") + ".class"
            record = {"owner": owner, "member": member}
            receipt["classes"].append(record)
            if member not in archive.namelist():
                record["status"] = "class-missing"
                continue
            class_bytes = archive.read(member)
            record["classSha256"] = hashlib.sha256(class_bytes).hexdigest()
            record["classMajorVersion"] = int.from_bytes(class_bytes[6:8], "big")
            command = [str(javap), "-p", "-c", "-s", "-classpath", str(game_jar), owner]
            record["command"] = command
            try:
                result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                        timeout=30, check=False)
            except (OSError, subprocess.TimeoutExpired) as failure:
                record["status"] = "javap-unavailable" if isinstance(failure, OSError) else "javap-timeout"
                record["reason"] = str(failure)[:2048]
                continue
            record["exitCode"] = result.returncode
            record["transientStdoutBytes"] = len(result.stdout)
            if result.returncode or len(result.stdout) > TRANSIENT_LIMIT:
                record["status"] = "javap-failed" if result.returncode else "transient-output-limit"
                record["stderr"] = result.stderr[:2048].decode("utf-8", errors="replace")
                continue
            methods = method_blocks(result.stdout.decode("utf-8", errors="strict"))
            selected = selected_closure(methods, selectors, owner)
            record["requestedMethods"] = list(selectors)
            record["missingRequestedMethods"] = sorted(set(selectors) - set(methods))
            record["retainedMethods"] = selected
            record["commandDeclarations"] = [body.splitlines()[0].strip()
                for name in sorted(methods) if re.search(r"command|chat|sign", name, re.I)
                for body in methods[name]]
            excerpt = "// Named game artifact SHA256: " + receipt["gameJarSha256"] + "\n"
            excerpt += "// Class: " + owner + "\n"
            if owner in TOAST_CLASSES:
                # Keep the real field declarations alongside all selected render/admission closures.
                native_text = result.stdout.decode("utf-8", errors="strict")
                first_method = re.search(r"(?m)^  \S[^\n]*\([^\n]*\)[^\n]*;$", native_text)
                if first_method:
                    excerpt += native_text[:first_method.start()] + "\n"
            excerpt += "\n".join(
                body for name in selected for body in methods[name])
            content = excerpt.encode("utf-8")
            if retained_bytes + len(content) > OUTPUT_LIMIT - 8192:
                record["status"] = "retained-output-limit"
                continue
            destination = output / (owner.rsplit(".", 1)[-1] + ".bytecode.txt")
            destination.write_bytes(content)
            retained_bytes += len(content)
            record["excerpt"] = destination.name
            record["excerptSha256"] = hashlib.sha256(content).hexdigest()
            record["excerptBytes"] = len(content)
            record["status"] = "captured" if selected else "requested-methods-missing"
    receipt["retainedExcerptBytes"] = retained_bytes
    encoded = (json.dumps(receipt, indent=2) + "\n").encode("utf-8")
    if retained_bytes + len(encoded) > OUTPUT_LIMIT:
        raise ValueError("Native command evidence exceeds 100 KiB")
    (output / "receipt.json").write_bytes(encoded)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-jar", type=Path)
    parser.add_argument("--javap", type=Path)
    parser.add_argument("--project", default=":forge")
    parser.add_argument("--target", default="1.19.2")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "build/forge-native-contracts")
    args = parser.parse_args()
    if args.game_jar is None or args.javap is None:
        # Existing always-run workflow call must not replace successful task-owned facts.
        args.output.mkdir(parents=True, exist_ok=True)
        if not (args.output / "receipt.json").exists():
            (args.output / "receipt.json").write_text(json.dumps({
                "evidenceKind": "actual-selected-named-game-bytecode",
                "status": "not-captured", "reason": "captureNativeCommandContracts task did not run"}, indent=2) + "\n")
        return
    capture(args.game_jar, args.javap, args.output, args.project, args.target)


if __name__ == "__main__":
    main()
