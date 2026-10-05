#!/usr/bin/env python3
"""Check a real software GL baseline on the same display before the unchanged game suite.

Readiness is not Minecraft acceptance. No download, game artifact, target/scenario,
Java option, or acceptance report is changed here. GLFW targets keep their environment.
"""
import argparse
import ctypes
import gzip
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MAX_NATIVE = 16 * 1024 * 1024
MAX_LOG = 2 * 1024 * 1024
SDL_ATTRIBUTES = ((17, 3), (18, 3), (20, 1), (19, 2), (22, 1))
SDL_WINDOW_FLAGS = 0x8002000A  # OPENGL | HIDDEN | UTILITY | NOT_FOCUSABLE
ENV_KEYS = ("DISPLAY", "LIBGL_ALWAYS_SOFTWARE", "GALLIUM_DRIVER", "SDL_VIDEO_DRIVER", "SDL_VIDEO_FORCE_EGL")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data, algorithm="sha256"):
    return hashlib.new(algorithm, data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def verify_file(path, runtime, records):
    require(path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(runtime), "Unsafe official runtime file")
    record = records.get(path.relative_to(runtime).as_posix())
    require(isinstance(record, dict) and set(record) == {"sha1", "sha256", "size"}, "Missing exact runtime file receipt")
    require(type(record["size"]) is int and 0 < record["size"] <= 32 * 1024 * 1024
            and path.stat().st_size == record["size"], "Official runtime hash/size changed: " + path.name)
    data = path.read_bytes()
    require(len(data) == record["size"] and digest(data, "sha1") == record["sha1"]
            and digest(data) == record["sha256"], "Official runtime hash/size changed: " + path.name)
    return data


def linux_rules(rules):
    if rules is None:
        return True
    allowed = not rules
    for rule in rules:
        require(set(rule).issubset({"action", "os"}) and rule.get("action") in ("allow", "disallow"), "Unknown official native selection rule")
        os_rule = rule.get("os", {})
        require(set(os_rule).issubset({"name", "arch", "version"}), "Unknown official OS selection rule")
        match = (os_rule.get("name", "linux") == "linux"
                 and (not os_rule.get("arch") or re.fullmatch(os_rule["arch"], "x86_64"))
                 and (not os_rule.get("version") or re.search(os_rule["version"], platform.release())))
        if match:
            allowed = rule["action"] == "allow"
    return allowed


def select_native(runtime, loader, target, root=ROOT):
    root, runtime = Path(root).resolve(), Path(runtime).resolve()
    require(loader in ("fabric", "neoforge") and re.fullmatch(r"[A-Za-z0-9_.-]+", target), "Invalid runtime target/loader")
    require(runtime == root / "build/e2e/runtime" / target / "minecraft", "Graphics probe must use the exact isolated official runtime")
    receipt_path = runtime / ".provision" / (loader + "-runtime.json")
    receipt = json.loads(receipt_path.read_text())
    profile = root / "gradle/minecraft-targets" / (target + ".properties")
    require(receipt.get("loader") == loader and receipt.get("minecraft") == target
            and receipt.get("minecraftRoot") == str(runtime) and receipt.get("mechanism") == "official-client-installer"
            and receipt.get("sourceProfileSha256") == digest(profile.read_bytes()), "Official graphics runtime identity differs")
    records = receipt.get("files")
    require(isinstance(records, dict), "Official runtime has no file hashes")
    metadata = json.loads(verify_file(runtime / "versions" / target / (target + ".json"), runtime, records))
    require(metadata.get("id") == target, "Official game metadata target differs")
    selected = [library for library in metadata["libraries"]
                if library["name"].startswith("org.lwjgl:lwjgl-sdl:") and linux_rules(library.get("rules"))]
    if not selected:
        return None
    main = [library for library in selected if len(library["name"].split(":")) == 3]
    native = [library for library in selected if library["name"].endswith(":natives-linux")]
    require(len(main) == len(native) == 1 and native[0]["name"] == main[0]["name"] + ":natives-linux"
            and len(selected) == 2, "Unknown or ambiguous official Linux SDL native selection")
    result = {"receiptSha256": digest(receipt_path.read_bytes())}
    for label, library in (("main", main[0]), ("native", native[0])):
        artifact = library["downloads"]["artifact"]
        relative = artifact["path"]
        require(isinstance(relative, str) and "\\" not in relative and not Path(relative).is_absolute()
                and all(part not in ("", ".", "..") for part in relative.split("/")), "Unsafe official SDL artifact path")
        path = runtime / "libraries" / relative
        data = verify_file(path, runtime, records)
        require(len(data) == artifact["size"] and digest(data, "sha1") == artifact["sha1"], "SDL artifact differs from official metadata")
        result[label] = {"path": path, "sha256": digest(data), "coordinate": library["name"]}
    return result


def validate_elf(data):
    require(len(data) >= 64 and data[:6] == b"\x7fELF\x02\x01" and int.from_bytes(data[16:18], "little") == 3
            and int.from_bytes(data[18:20], "little") == 62,
            "Official SDL native is not Linux x86_64 ELF64")


def extract_native(selected, output):
    prefix = "linux/x64/org/lwjgl/sdl/libSDL3.so"
    require(digest(selected["native"]["path"].read_bytes()) == selected["native"]["sha256"]
            and digest(selected["main"]["path"].read_bytes()) == selected["main"]["sha256"],
            "Verified SDL artifacts changed before extraction")
    with zipfile.ZipFile(selected["native"]["path"]) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), "Duplicate SDL native archive entries")
        info = archive.getinfo(prefix)
        require(0 < info.file_size <= MAX_NATIVE and not info.is_dir(), "SDL native exceeds extraction limit")
        for name in ("META-INF/" + prefix + ".sha1", "META-INF/" + prefix + ".git"):
            require(archive.getinfo(name).file_size <= 128, "SDL supplier identity exceeds size limit")
        data = archive.read(info)
        native_sha1 = archive.read("META-INF/" + prefix + ".sha1").decode("ascii").strip()
        supplier_git = archive.read("META-INF/" + prefix + ".git").decode("ascii").strip()
    require(re.fullmatch(r"[0-9a-f]{40}", supplier_git) and native_sha1 == digest(data, "sha1"), "SDL supplier native hash/pin differs")
    validate_elf(data)
    with zipfile.ZipFile(selected["main"]["path"]) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), "Duplicate SDL Java archive entries")
        require(archive.getinfo("META-INF/" + prefix + ".sha1").file_size <= 128
                and archive.getinfo("org/lwjgl/sdl/SDLVideo.class").file_size <= 4 * 1024 * 1024,
                "SDL Java binding identity exceeds size limit")
        require(archive.read("META-INF/" + prefix + ".sha1").decode("ascii").strip() == native_sha1,
                "SDL Java binding expects a different native")
        class_sha = digest(archive.read("org/lwjgl/sdl/SDLVideo.class"))
    native_dir = output / "native"
    native_dir.mkdir(exist_ok=True)
    path = native_dir / "libSDL3.so"
    require(not path.exists(), "Refuse to replace an existing SDL probe native")
    path.write_bytes(data)
    return path, {"supplierGit": supplier_git, "javaClassSha256": class_sha,
                  "nativeSha1": native_sha1, "nativeSha256": digest(data), "nativeSize": len(data),
                  "javaArtifact": selected["main"]["coordinate"], "javaArtifactSha256": selected["main"]["sha256"],
                  "nativeArtifact": selected["native"]["coordinate"], "nativeArtifactSha256": selected["native"]["sha256"],
                  "runtimeReceiptSha256": selected["receiptSha256"]}


def graphics_environment(original, sdl):
    environment = dict(original)
    if sdl:
        environment.update(SDL_VIDEO_DRIVER="x11", SDL_VIDEO_FORCE_EGL="1", GALLIUM_DRIVER="llvmpipe")
    return environment


def diagnostic(command, output, name, environment, timeout=30):
    try:
        result = subprocess.run(command, env=environment, capture_output=True, timeout=timeout, check=False)
        data, code = result.stdout + result.stderr, result.returncode
    except subprocess.TimeoutExpired as error:
        data, code = (error.stdout or b"") + (error.stderr or b"") + b"\nDIAGNOSTIC TIMEOUT\n", 124
    except OSError as error:
        data, code = str(error).encode(), 127
    truncated = len(data) > MAX_LOG
    data = data[:MAX_LOG]
    with gzip.open(output / (name + ".log.gz"), "wb") as stream:
        stream.write(data)
    return {"command": command, "exitCode": code, "log": name + ".log.gz", "logSha256": digest(data),
            "logBytes": len(data), "truncated": truncated}


def validate_context(value):
    require(value["version"] >= [3, 3], "Actual desktop OpenGL context is older than 3.3")
    require(value["profileMask"] & 1 and value["contextFlags"] & 1, "Actual context is not core/forward-compatible")
    require(value["framebufferEncoding"] == 0x8C40, "Actual default framebuffer is not sRGB")
    require("llvmpipe" in value["renderer"].lower(), "Actual renderer is not Mesa llvmpipe")
    require(value["eglDisplay"], "Actual context does not use EGL")


def mapped_libraries():
    result = []
    paths = {line.split(maxsplit=5)[5].strip() for line in Path("/proc/self/maps").read_text().splitlines()
             if len(line.split(maxsplit=5)) == 6 and line.split(maxsplit=5)[5].startswith("/")
             and ".so" in line.split(maxsplit=5)[5]}
    for name in sorted(paths):
        path = Path(name)
        require(path.is_file(), "Mapped native library is missing: " + name)
        checksum = hashlib.sha256()
        with path.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                checksum.update(block)
        result.append({"path": name, "size": path.stat().st_size, "sha256": checksum.hexdigest()})
    return result


def sdl_probe(library, result_path, expected_sha256):
    """C signatures/enums follow supplier SDL3 headers; GL queries are actual driver calls."""
    data = library.read_bytes()
    require(not library.is_symlink() and digest(data) == expected_sha256, "Extracted supplier SDL native changed before loading")
    validate_elf(data)
    sdl = ctypes.CDLL(str(library))
    def bind(name, result, arguments):
        function = getattr(sdl, name)
        function.restype, function.argtypes = result, arguments
        return function
    get_error = bind("SDL_GetError", ctypes.c_char_p, [])
    init = bind("SDL_Init", ctypes.c_bool, [ctypes.c_uint32])
    quit_sdl = bind("SDL_Quit", None, [])
    load_gl = bind("SDL_GL_LoadLibrary", ctypes.c_bool, [ctypes.c_char_p])
    unload_gl = bind("SDL_GL_UnloadLibrary", None, [])
    attribute = bind("SDL_GL_SetAttribute", ctypes.c_bool, [ctypes.c_int, ctypes.c_int])
    create_window = bind("SDL_CreateWindow", ctypes.c_void_p, [ctypes.c_char_p, ctypes.c_int, ctypes.c_int, ctypes.c_uint64])
    destroy_window = bind("SDL_DestroyWindow", None, [ctypes.c_void_p])
    create_context = bind("SDL_GL_CreateContext", ctypes.c_void_p, [ctypes.c_void_p])
    destroy_context = bind("SDL_GL_DestroyContext", ctypes.c_bool, [ctypes.c_void_p])
    make_current = bind("SDL_GL_MakeCurrent", ctypes.c_bool, [ctypes.c_void_p, ctypes.c_void_p])
    swap_window = bind("SDL_GL_SwapWindow", ctypes.c_bool, [ctypes.c_void_p])
    get_proc = bind("SDL_GL_GetProcAddress", ctypes.c_void_p, [ctypes.c_char_p])
    video_driver = bind("SDL_GetCurrentVideoDriver", ctypes.c_char_p, [])
    version = bind("SDL_GetVersion", ctypes.c_int, [])
    revision = bind("SDL_GetRevision", ctypes.c_char_p, [])
    def checked(value, operation):
        require(value, operation + ": " + (get_error() or b"no SDL error").decode("utf-8", "replace"))
        return value
    def proc(name, result, arguments):
        address = checked(get_proc(name.encode()), name)
        return ctypes.CFUNCTYPE(result, *arguments)(address)
    value, windows, context, loaded = {"ready": False}, [], None, False
    try:
        checked(init(0x20), "SDL_Init(VIDEO)")
        value.update(sdlVersion=version(), sdlRevision=(revision() or b"").decode(), videoDriver=(video_driver() or b"").decode())
        require(value["videoDriver"] == "x11", "Actual SDL driver is not x11")
        checked(load_gl(None), "SDL_GL_LoadLibrary")
        loaded = True
        for key, setting in SDL_ATTRIBUTES:
            checked(attribute(key, setting), "SDL_GL_SetAttribute " + str(key))
        window = checked(create_window(b"Minecraft - RenderPearl OpenGL Hidden Utility Window", 320, 480, SDL_WINDOW_FLAGS), "SDL_CreateWindow utility")
        windows.append(window)
        context = checked(create_context(window), "SDL_GL_CreateContext")
        checked(make_current(window, context), "SDL_GL_MakeCurrent")
        # The official device creates a second identical window after making its context.
        windows.append(checked(create_window(b"Minecraft - RenderPearl OpenGL Hidden Test Window", 320, 480, SDL_WINDOW_FLAGS), "SDL_CreateWindow test"))
        get_integer = proc("glGetIntegerv", None, [ctypes.c_uint, ctypes.POINTER(ctypes.c_int)])
        get_string = proc("glGetString", ctypes.c_char_p, [ctypes.c_uint])
        get_attachment = proc("glGetFramebufferAttachmentParameteriv", None, [ctypes.c_uint, ctypes.c_uint, ctypes.c_uint, ctypes.POINTER(ctypes.c_int)])
        get_gl_error = proc("glGetError", ctypes.c_uint, [])
        def integer(key):
            result = ctypes.c_int()
            get_integer(key, ctypes.byref(result))
            require(get_gl_error() == 0, "Actual GL integer query failed")
            return result.value
        encoding = ctypes.c_int()
        get_attachment(0x8D40, 0x0402, 0x8210, ctypes.byref(encoding))  # FRAMEBUFFER, BACK_LEFT, COLOR_ENCODING
        require(get_gl_error() == 0, "Actual framebuffer sRGB query failed")
        egl = ctypes.CDLL("libEGL.so.1")
        egl.eglGetCurrentDisplay.restype, egl.eglGetCurrentDisplay.argtypes = ctypes.c_void_p, []
        value.update(version=[integer(0x821B), integer(0x821C)], profileMask=integer(0x9126), contextFlags=integer(0x821E),
                     framebufferEncoding=encoding.value, renderer=(get_string(0x1F01) or b"").decode(),
                     vendor=(get_string(0x1F00) or b"").decode(), versionString=(get_string(0x1F02) or b"").decode(),
                     eglDisplay=bool(egl.eglGetCurrentDisplay()))
        validate_context(value)
        value["mappedLibraries"] = mapped_libraries()
        require(any(Path(item["path"]).resolve() == library.resolve() for item in value["mappedLibraries"]),
                "Actual context did not map the verified official SDL native")
        checked(swap_window(window), "SDL_GL_SwapWindow")
        value["ready"] = True
    except (OSError, ValueError, AttributeError) as failure:
        value["failure"] = str(failure)
        if "mappedLibraries" not in value:
            try:
                value["mappedLibraries"] = mapped_libraries()
            except (OSError, ValueError) as mapping_failure:
                value["mappedLibraryFailure"] = str(mapping_failure)
    finally:
        if context:
            make_current(windows[0], None)
            if not destroy_context(context):
                value.update(ready=False, failure="SDL_GL_DestroyContext failed")
        for window in reversed(windows):
            destroy_window(window)
        if loaded:
            unload_gl()
        quit_sdl()
        write_json(result_path, value)
    return 0 if value["ready"] else 1


def preflight(selected, output, environment):
    require(selected is not None, "Only selected official SDL targets need graphics preflight")
    report = {"ready": False, "environment": {key: environment[key] for key in ENV_KEYS if key in environment},
              "baseline": "SDL3 desktop OpenGL via EGL / Mesa llvmpipe", "diagnostics": []}
    require(environment.get("DISPLAY"), "Graphics preflight needs the same Xvfb DISPLAY as the game")
    require(environment.get("LIBGL_ALWAYS_SOFTWARE") == "1", "Software graphics CI requires LIBGL_ALWAYS_SOFTWARE=1")
    require(not any(environment.get(key) for key in ("MESA_GL_VERSION_OVERRIDE", "MESA_GLSL_VERSION_OVERRIDE", "SDL_VIDEO_EGL_SRGB_FRAMEBUFFER")),
            "Graphics preflight refuses version or sRGB overrides")
    # These tools inspect real capabilities. Vulkan is diagnostic only, never an acceptance fallback.
    for name, command in (("glx-summary", ["glxinfo", "-B"]), ("glx-visuals", ["glxinfo"]),
                          ("egl", ["eglinfo", "-B"]), ("vulkan", ["vulkaninfo", "--summary"]),
                          ("packages", ["dpkg-query", "-W", "xvfb", "libgl1-mesa-dri", "libglx-mesa0", "libegl-mesa0", "mesa-vulkan-drivers"]),
                          ("libraries", ["ldconfig", "-p"])):
        report["diagnostics"].append(diagnostic(command, output, name, environment))
    library, report["supplier"] = extract_native(selected, output)
    closure = diagnostic(["ldd", str(library)], output, "sdl-library-closure", environment)
    report["diagnostics"].append(closure)
    with gzip.open(output / closure["log"], "rt", errors="replace") as stream:
        closure_text = stream.read()
    if closure["exitCode"] or closure["truncated"] or "not found" in closure_text:
        report["failure"] = "Official SDL native library closure is not ready"
        return report
    result_path = output / "sdl-probe.json"
    child = diagnostic([sys.executable, "-B", str(Path(__file__).resolve()), "--probe-library", str(library),
                        "--probe-result", str(result_path), "--probe-sha256", report["supplier"]["nativeSha256"]],
                       output, "sdl-probe", environment, timeout=30)
    report["diagnostics"].append(child)
    if child["exitCode"] or child["truncated"] or not result_path.is_file():
        report["failure"] = "Official SDL EGL context probe failed or timed out"
        return report
    result = json.loads(result_path.read_text())
    if result.get("ready") is not True:
        report["failure"] = "Official SDL EGL context probe is not ready"
        return report
    validate_context(result)
    report["ready"], report["context"] = True, result
    return report


def run(runtime, loader, target, output, command, root=ROOT):
    require(command and command[0] != "--", "Exact game command is required after --")
    output = Path(output).resolve()
    require(output.is_relative_to(Path(root).resolve() / "build/ci-graphics") and not output.exists(),
            "Graphics output must be a new directory inside ignored build/ci-graphics")
    output.mkdir(parents=True)
    report = {"ready": False, "gameAcceptance": "NOT_RUN"}
    try:
        selected = select_native(runtime, loader, target, root)
        environment = graphics_environment(os.environ, selected is not None)
        if selected is None:
            report.update(ready=None, preflight="NOT_APPLICABLE", baseline="unchanged GLFW environment")
        else:
            report.update(preflight(selected, output, environment))
            write_json(output / "readiness.json", report)
            if not report["ready"]:
                print("Software graphics not ready; game acceptance NOT_RUN", file=sys.stderr)
                return 1
            print("Software graphics ready; this is not game acceptance", flush=True)
        report["gameAcceptance"] = "DELEGATED_NOT_EVALUATED"
        write_json(output / "readiness.json", report)
        result = subprocess.run(command, cwd=root, env=environment, check=False)
        report["gameCommandExitCode"] = result.returncode
        write_json(output / "readiness.json", report)
        return result.returncode
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as failure:
        report.update(ready=False, failure=str(failure))
        write_json(output / "readiness.json", report)
        print("Software graphics preflight refused: " + str(failure), file=sys.stderr)
        return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loader", choices=("fabric", "neoforge"))
    parser.add_argument("--minecraft-target")
    parser.add_argument("--minecraft-root", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--probe-library", type=Path)
    parser.add_argument("--probe-result", type=Path)
    parser.add_argument("--probe-sha256")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        parser.error("This software CI baseline is Linux x86_64 only")
    if args.probe_library:
        if not args.probe_result or not re.fullmatch(r"[0-9a-f]{64}", args.probe_sha256 or ""):
            parser.error("Native child requires --probe-result and exact --probe-sha256")
        try:
            return sdl_probe(args.probe_library, args.probe_result, args.probe_sha256)
        except (OSError, ValueError) as failure:
            write_json(args.probe_result, {"ready": False, "failure": str(failure)})
            return 1
    if not all((args.loader, args.minecraft_target, args.minecraft_root, args.output)):
        parser.error("Runtime loader, target, root, and fresh output are required")
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    try:
        return run(args.minecraft_root, args.loader, args.minecraft_target, args.output, command)
    except ValueError as failure:
        parser.exit(1, "Software graphics refused: " + str(failure) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
