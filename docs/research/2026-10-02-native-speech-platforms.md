# Native speech engine 与 Java 25 跨平台打包：当前实证

日期：2026-10-02。范围：Minecraft 26.2，Fabric / NeoForge，Java 25，client-only ASR。麦克风采集、HUD、确认后发送由其他任务负责。

## 1. 结论和未闭环项

**推荐引擎：sherpa-onnx 官方 Java/JNI 1.13.8，默认 CPU。** 不手写 recognizer、AEC、resampler、FFM/JNA struct。保留 SenseVoice / Paraformer / Whisper ONNX 的 typed model-family。默认模型等待同条件实测，不宣布统一赢家。

Root 已选运行形态：**client parent 采集 PCM → 固定 Java 25 worker → 官方 JNI**。worker 只读 PCM，不开麦克风。用同一 Java executable、代码拥有的 helper entrypoint、固定可信 classpath 与参数列表；`ProcessBuilder(List<String>)`，不经 shell，不执行用户提供的命令。这仍是成熟官方 JNI，不是自写 native ASR。

已完成的研究：

- 实际下载并计算 core + 六平台 native JAR 的大小和 SHA-256；逐个与官方 release digest 比对一致。
- 查看 ZIP 内容、Java bytecode、ELF version-needed、Windows PE imports、macOS Mach-O load commands 与 codesign 信息。
- 查看 exact tag 的 Java API、loader、官方构建脚本及第三方许可来源。
- 后续经 Root 明确请求，启动 task-owned macOS ARM64 ASR-only 官方 JNI source build。原样构建在 link 阶段失败，原因和最小构建修补见 §10。

没有做：Gradle、游戏、真实麦克风、私密配置、paid API、native 安装、native 加载。另一个任务的 Python CPU 推理是后端开发比较，**不是 Java/JNI 六平台通过证明**。

证据目录：`/tmp/openallay-native-speech-platform-eval-20261002`。机器可读 pins：`sherpa-jni-1.13.8-artifacts.json`；静态 ELF/PE 结果：`binary-inspection.json`；Mac `*.otool.txt`；TTS 静态存在证据：`tts-binary-presence.json`。

不能隐藏的阻塞：

1. 六平台 stock JNI 均包含 TTS 与静态 eSpeak NG（GPL-3.0）。不能把 binary 说成只有 Apache engine + MIT ORT。发布优先 ASR-only 上游构建；当前还没有六个平台 ASR-only JNI 成品。
2. 本报告没有进行 Java 25 JNI load / fixture decode。静态 ABI/bytecode审计不等于实跑。
3. macOS ARM64 两个 dylib 为 ad-hoc 签名且无 Team ID；Intel JNI 未签名、ORT ad-hoc。没有 Developer ID / notarization 证明。某些 hardened host 可能拒载；没有测试，**不代表所有 Mac launcher 都不可用**。
4. 麦克风 TCC 是 capture host 的另一类权限。给 dylib 签名不能取得麦克风许可，mod 不能替用户授权或控制 launcher app 身份。

## 2. 当前 exact release 与成熟 binding 对比

| 候选 | 本轮当前发布事实 | Java 打包现实 | 首版取舍 |
|---|---|---|---|
| sherpa-onnx | [latest](https://github.com/k2-fsa/sherpa-onnx/releases/latest) → **v1.13.8**；[expanded assets](https://github.com/k2-fsa/sherpa-onnx/releases/expanded_assets/v1.13.8) | 官方 JVM core + 六个 native JAR | 首选；ABI、license 与实跑仍需闭环 |
| whisper.cpp | [latest](https://github.com/ggml-org/whisper.cpp/releases/latest) → **v1.9.4**；[v1.9.4 assets](https://github.com/ggml-org/whisper.cpp/releases/expanded_assets/v1.9.4) 本轮仅 source archives | 自带 Java 是 JNA；Maven `io.github.ggerganov:whispercpp` latest仍1.4.0 | 引擎成熟不等于此 Java 发布组已覆盖六平台 |
| GiviMAD WhisperJNI | [Maven metadata](https://repo.maven.apache.org/maven2/io/github/givimad/whisper-jni/maven-metadata.xml) latest **1.7.1**，2025-01-03更新 | `io.github.givimad:whisper-jni:1.7.1` 单JAR携 native | 现成绑定优于重写；Win ARM64 缺失，不作六平台默认 |

sherpa 官方 [Non-Android Java 文档](https://k2-fsa.github.io/sherpa/onnx/java-api/non-android-java.html) 明列六平台、JDK8+与 JitPack module 坐标。其文档示例仍 v1.13.5，不当当前版本事实。[v1.13.8 JVM POM](https://jitpack.io/com/github/k2-fsa/sherpa-onnx/sherpa-onnx-jvm/v1.13.8/sherpa-onnx-jvm-v1.13.8.pom) 本轮HTTP200。首版建议使用固定 release bytes，不要求玩家依赖 JitPack 临时构建。

这些 native 名字是各自 artifactId，不是 Maven Central 单artifact的六个classifier。产品字段可以叫classifier，但必须保留exact名字：`osx-aarch64`与`win-arm64`不能随意替换为统一别名。

## 3. 六平台真实 artifacts / integrity matrix

所有下列大小与SHA都由本轮**已下载文件**计算，并与官方release digest匹配。

JVM core：

- [sherpa-onnx-jvm-1.13.8.jar](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-jvm-1.13.8.jar)
- 187,490 bytes；SHA-256 `77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b`。
- 全部 `.class` major **52 / Java 8**；无 JNA、无独立 Microsoft Java ORT依赖。Java25可读bytecode不是native加载通过。

| 平台 | exact classifier | 官方 native JAR（链接） | bytes | SHA-256 |
|---|---|---|---:|---|
| Windows x64 | `win-x64` | [sherpa-onnx-native-lib-win-x64-1.13.8.jar](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-native-lib-win-x64-1.13.8.jar) | 8,277,046 | `33fbdbd5410e9ba9bdda94aa164ec8f7825bb49246420d8ce9bdd88219d97039` |
| Windows ARM64 | `win-arm64` | [sherpa-onnx-native-lib-win-arm64-1.13.8.jar](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-native-lib-win-arm64-1.13.8.jar) | 7,889,954 | `986660bef51f0ca4f5635b763c172c64bb056eabebcd319f42185d7b23337fb8` |
| macOS Intel | `osx-x64` | [sherpa-onnx-native-lib-osx-x64-1.13.8.jar](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-native-lib-osx-x64-1.13.8.jar) | 10,782,466 | `9190c28951d85efdbd376bae6b6dff12993311ad605945289e8459bf9c886a96` |
| macOS Apple Silicon | `osx-aarch64` | [sherpa-onnx-native-lib-osx-aarch64-1.13.8.jar](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-native-lib-osx-aarch64-1.13.8.jar) | 9,460,832 | `42e272180c8836127f024f3335d7afcdfb30fe0164b78330d5d449034e34ce34` |
| Linux x64 | `linux-x64` | [sherpa-onnx-native-lib-linux-x64-1.13.8.jar](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-native-lib-linux-x64-1.13.8.jar) | 10,515,871 | `30c93b59381113f9c20aedbbf9fc1ad399158f6bc03dddc0f8934a6e28e069ba` |
| Linux ARM64 | `linux-aarch64` | [sherpa-onnx-native-lib-linux-aarch64-1.13.8.jar](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-native-lib-linux-aarch64-1.13.8.jar) | 13,224,859 | `5123d2e48ae1a7ce82ba89ce153906c651bf418a63db2f7dff82265e6d49c104` |

每个 native JAR 仅含 manifest 与两文件，没有 executable、没有 license 文件：

```text
sherpa-onnx/native/<classifier>/
  Windows: onnxruntime.dll, sherpa-onnx-jni.dll
  macOS:   libonnxruntime.dylib, libsherpa-onnx-jni.dylib
  Linux:   libonnxruntime.so, libsherpa-onnx-jni.so
```

解压后两文件总bytes：Winx64 22,422,528；WinARM64 22,296,576；MacIntel 36,803,112；MacApple 33,224,408；Linuxx64 32,192,969；LinuxARM64 39,339,064。

六native+core研究下载共60,338,518 bytes。**首版只下载core+当前JVM平台的一份native，不bundle六份，不照官方Gradle示例按buildhost选完便发布。** 选正在运行JVM的`os.name`/`os.arch`；Rosetta x64 Java应选`osx-x64`，不凭硬件AppleSilicon选arm64。

### 动态依赖实证

- 配套 **ONNX Runtime 1.28.2**。tag内`cmake/onnxruntime-*.cmake` URL/hash固定该版；Linux JNI version-needed有`VERS_1.28.2`。不混另一模型目录ORT、Microsoft Java ORT或系统版本。
- Linuxx64：ELF EM_X86_64；最高version-needed GLIBC **2.16**、GLIBCXX **3.4.19**。LinuxARM64：EM_AARCH64；GLIBC **2.17**、GLIBCXX **3.4.19**。需系统libstdc++/libgcc_s/libc/libm/pthread/dl/rt；不是musl/Alpine支持声明。JDK25/Minecraft本身要求可能更高。
- Windows x64 PE machine0x8664；ARM64 0xaa64。JNI imports仅onnxruntime.dll、KERNEL32、ADVAPI32；ORT另需Windows系统api-ms-win-core-path、dbghelp、SETUPAPI、dxgi。普通imports无MSVCP/VCRUNTIME/VCOMP，符合官方默认`/MT`构建。未全面证明delay-load/provider运行行为。不能换`/MD` debug archive。
- MacJNI依赖配套ORT、系统libc++/libSystem。ORT另含CoreML/Foundation/CoreFoundation/libiconv/libobjc；即使provider=cpu，CoreML framework load dependency仍在，并非要求CoreML推理。JNI/ORT相对`@rpath`与`@loader_path`。
- 此六pair直接动态依赖中未见OpenMP (`libgomp`/`libomp`/VCOMP)、OpenSSL、CUDA、cuDNN。不将该结果推广到其他GPU/将来版本。HTTPS下载由Java控制面/JDK TLS做，不另装native OpenSSL。
- 默认 `.setProvider("cpu")`；不选CUDA/GPU/DirectML release组；不依赖CUDA启动。architecture header不能证明任意旧x64 ISA可跑，仍须真实CPU fixture。

## 4. Whisper现成Java路径的实际缺口

### GiviMAD WhisperJNI 1.7.1

已下载 [JAR](https://repo.maven.apache.org/maven2/io/github/givimad/whisper-jni/1.7.1/whisper-jni-1.7.1.jar)：4,598,522 bytes；SHA `4b6c6a55c44c2163d363c3ec37597431d33260df30c3b12a07b340cdb5b0f19f`；Java class major55 / Java11。

| 平台 | 实际资源目录 | 缺口/依赖 |
|---|---|---|
| Winx64 | `win-amd64/`，whisper-jni.dll与whisper-jni_full.dll | fullDLL imports MSVCP140/VCRUNTIME140/VCRUNTIME140_1/**VCOMP140**，未内置这些runtime |
| WinARM64 | 无 | 缺。x64仿真不等于ARM64 JVM可load JNI |
| MacIntel | `macos-amd64/`，JNI/whisper/ggml三个dylib | 均unsigned |
| MacApple | `macos-arm64/`，三个dylib | ad-hoc/noTeamID |
| Linuxx64 | `debian-amd64/`，有ggml AVX变体 | 实ELF GLIBC至2.29、GLIBCXX至3.4.22、`libgomp.so.1`外部依赖 |
| LinuxARM64 | `debian-arm64/`，含ggml+crc/+fp16 | 同GLIBC2.29/GLIBCXX3.4.22/libgomp依赖 |

[README](https://github.com/GiviMAD/whisper-jni/blob/main/README.md) 文档说Linux目标GLIBC2.31、Mac11.0；Winx64要求AVX2/FMA/F16C/AVX。文档构建目标与ELF最高needed是不同指标，均保留不混写。Windows external库逻辑可找PATH中的whisper.dll，首版不能默默加载未知系统DLL。

### whisper.cpp官方JNA

[当前README](https://github.com/ggml-org/whisper.cpp/blob/v1.9.4/bindings/java/README.md) 是JNA search/load机制，不是六platform发布承诺。[Maven metadata](https://repo.maven.apache.org/maven2/io/github/ggerganov/whispercpp/maven-metadata.xml) latest1.4.0，2023-09-15。

实际下载 [whispercpp-1.4.0.jar](https://repo.maven.apache.org/maven2/io/github/ggerganov/whispercpp/1.4.0/whispercpp-1.4.0.jar)，266,889bytes；只发现`win32-x86-64/whisper.dll`一份native。POM runtime依赖JNA5.13.0。不能用成熟whisper.cpp引擎声望补齐此artifact的其他五平台，更不能任意配最新1.9.4 native与旧by-value结构声称ABI兼容。

可保留成熟whisper.cpp fixedCLI备选，但首版不为此手写binding/recognizer或要求玩家系统安装。

## 5. 官方 loader 与生命周期

依据 exact tag [LibraryUtils.java](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/LibraryUtils.java) / [LibraryLoader.java](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/java-api/src/main/java/com/k2fsa/sherpa/onnx/LibraryLoader.java)：

1. 优先 `sherpa_onnx.native.path` 指定目录。先绝对路径 `System.load(onnxruntime)`，再 `System.load(sherpa-onnx-jni)`。
2. 再试 classpath 的 `sherpa-onnx/native/<os-arch>/` 两个资源。多个平台JAR在classpath也会自动按当前`os.name/os.arch`选一份，但首版只下载当前平台。
3. 最后 fallback `System.loadLibrary("sherpa-onnx-jni")` / `java.library.path`。产品不应在可信文件缺失后悄悄落到未知系统版本。

默认resource loader提取到`Files.createTempDirectory("sherpa-onnx-java")`，即`java.io.tmpdir`；依次load两文件，deleteOnExit。close不unload。Linux noexec temp可能失败；Windows已load DLL不能立刻删除；同进程其他ORT版本或mod ClassLoader冲突未实测。

### fixed worker 的路径策略

- 下载入口显式，UI展示平台、bytes与来源。先临时下载、完整size/SHA校验、原子安装到task-owned runtime cache。lazy启动，不block游戏render线程，不安装系统native包、不搜索PATH补DLL。
- **runtime executable artifacts 与model data独立**。core/native URL与SHA由代码owned fixed catalog / packaged manifest决定。模型descriptor只定义family与ONNX/tokens数据，不允许其定义JVM/native角色、helper入口、classpath、执行依赖URL或覆盖pins。
- worker executable取同Java25 `java.home/bin/java[.exe]`；固定`--enable-native-access=ALL-UNNAMED`、固定`-cp` coreJAR+当前nativeJAR+本体helperJAR、固定mainclass。路径各自是一项参数，不是shell文本。
- 最简用官方resource loader，worker可固定`-Djava.io.tmpdir=<owned dir>`。这样避免宿主手动System.load的classloader绑定风险。
- 若持久解包pair，只取exact两entry到受信cache。固定`-Dsherpa_onnx.native.path=<verified dir>`；先确认pair存在且哈希匹配，不能让不存在目录触发system fallback。该property初始化一次，不随model切换换ORT。
- official `LibraryLoader.setAutoLoadEnabled(false)` 可由宿主控制显式load，但其classloader绑定需要真实验证；这里更简单的fixedworker使用官方loader。
- 一个worker保持一个recognizer。每个完整utterance新建stream；stream.release放finally。decode结束后才recognizer.release；官方finalize只是兜底。不得由另一线程在decode中free pointer。
- 官方decode同步且无interrupt/abort API。in-process `Future.cancel(true)`不是终止native；只能丢弃late结果。fixedworker取消可kill并重建，代价是下一次coldstart+modelload。client关闭、模型切换、失败要回收worker。
- stdout只作内部识别协议，stderr做bounded diagnostics；`setDebug(false)`。内部Latest Only，不加schema版本与迁移。

## 6. Java 25、Mac library签名与麦克风授权

[Java25 launcher manual](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html) 与[JEP472](https://openjdk.org/jeps/472)：JNI加载/native方法是restricted access。Java25 default `--illegal-native-access=warn`，launcher若选deny会拒；classpath需`--enable-native-access=ALL-UNNAMED`。mod manifest不能给已启动Minecraft retroactively加flag；manifest选项针对`java -jar`启动的executableJAR。fixedworker自行带flag，不改用户launcher/环境变量。JNA/FFM也不是绕过native access的办法。

Java25可`--finalization=disabled`，不能靠finalize释放recognizer。加载失败投射typed `NATIVE_UNAVAILABLE`，区分unsupportedplatform、integrity、linker、nativeaccess、signing。不能阻止游戏启动或卡render，也不能用永久fakeASR宣称首版native完成。

Mac静态结果：

- AppleSilicon JNI+ORT minOS11.0，两个ad-hoc签名，无TeamIdentifier。
- Intel JNI minOS10.14、unsigned；ORT minOS10.15、ad-hoc/noTeamID。
- 两组`@loader_path`/`@rpath`相对加载；没有DeveloperID/notarization证明。
- [Hardened Runtime](https://developer.apple.com/documentation/security/hardened-runtime) libraryvalidation可能要求host entitlement或同team库。mod不能给launcher加entitlement。不建议关闭系统安全或重签用户launcher；需目标launcher真实测试并诚实报错。未测≠所有launcher不可用。

**麦克风是另一类权限**：[Apple capture authorization](https://developer.apple.com/documentation/avfoundation/requesting-authorization-to-capture-and-save-media) / [Audio Input entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.security.device.audio-input)属于capture host身份与OS授权。mod不能控制TCC归属/launcher Info.plist或代用户授权。parent采集、child只PCM，不制造第二个mic主体。native签名与micpermission错误分别显示；Windows/Linux设备选择和隐私开关同样不等于engine load。

## 7. Exact Java API与最小可测试port

官方 v1.13.8 examples：[SenseVoice](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/java-api-examples/NonStreamingDecodeFileSenseVoice.java)、[Paraformer](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/java-api-examples/NonStreamingDecodeFileParaformer.java)、[Whisper](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/java-api-examples/NonStreamingDecodeFileWhisper.java)。

```java
// Documentation sketch, not a product source change.
var b = OfflineModelConfig.builder().setTokens(tokens.toString())
        .setNumThreads(cpuThreads).setProvider("cpu").setDebug(false);
// Select exactly one family:
b.setSenseVoice(OfflineSenseVoiceModelConfig.builder()
        .setModel(model.toString()).setLanguage("")
        .setInverseTextNormalization(true).build());
// or b.setParaformer(OfflineParaformerModelConfig.builder()
//         .setModel(model.toString()).build());
// or b.setWhisper(OfflineWhisperModelConfig.builder()
//         .setEncoder(encoder.toString()).setDecoder(decoder.toString())
//         .setLanguage("").setTask("transcribe").build());
var cfg = OfflineRecognizerConfig.builder()
        .setOfflineModelConfig(b.build()).setDecodingMethod("greedy_search").build();
var recognizer = new OfflineRecognizer(cfg);
var stream = recognizer.createStream();
try {
    stream.acceptWaveform(monoFloatSamples, actualSampleRate);
    recognizer.decode(stream);
    var result = recognizer.getResult(stream); // getText(), getLang()
} finally {
    stream.release();
}
// recognizer.release() only when its last decode has ended.
```

Whisper Java默认language=`en`；中文显式`zh`或空string自动LID。[CPUdecoder source](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-whisper-greedy-search-decoder.cc)按language.empty()自动detect，不能传UI字面`"auto"`。`.en`model只英语，不作为中英混说候选。

输入float mono、finite、归一化，保留实际sampleRate。[官方offline-stream.cc](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/csrc/offline-stream.cc) sampleRate不等模型特征率时用现成LinearResample。不另写resampler。一次完整utterance accept最清楚；不要把任意chunk当多个独立finished离线流。

建议最小hostport（名字不强制实现）：

```java
interface NativeSpeechEngine extends AutoCloseable {
    CompletionStage<Transcript> transcribe(
            Utterance audio, RecognitionOptions options, Cancellation cancel);
    EngineStatus status();
    void close();
}
record Utterance(long id, float[] mono, int sampleRate) {}
record RecognitionOptions(ModelSelection model, int cpuThreads,
        LanguageHint language, boolean inverseTextNormalization) {}
record Transcript(long utteranceId, String text, String detectedLanguage) {}
```

契约：不开mic；不在render线程识别；typedmodel、有限CPU线程数；audio ownership清楚；cancel不产生提交；late结果不覆盖新utterance；worker死亡为typed失败；close回收process。confidence、timestamps、streamingpartial不是所有family共有，不发明通用confidence。

fakeport注入同接口，可受控成功/失败/挂起/取消，记录PCM与options。用它测capture→recognize→review→submit生命周期，不伪造native支持与正确率。HUDruntime状态可为disabled/modelmissing/downloading/starting/ready/capturing/recognizing/failed。设备、TCC、AEC不是recognizer职责。

## 8. In-process vs fixedworker vs nativeCLI

| 形态 | 安装/延迟 | 取消/故障 | 首版取舍 |
|---|---|---|---|
| 游戏内官方JNI | 少IPC，直接resource加载 | nativeexit/crash影响游戏；不能interruptdecode；ClassLoader/ORT冲突 | 可用候选，但不是当前默认 |
| **fixedJavaworker+官方JNI** | core+当前native+本体helper；同Java25，可warmmodel | kill硬取消；重建冷启动/模型load代价 | 当前推荐，不免ABI/签名验证 |
| fixed官方nativeCLI | 平台tar保留bin/lib相对layout | kill可取消；逐次启动/load慢，输出需严格适配 | 可选，不任意userexe，不要求Python |

官方1.13.8 release有以下现成CLI/shared组，**不是JNIJar**。本报告只核名字与metadata，没有tar内容load验证：

- Linuxx64 [shared-no-tts](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-linux-x64-shared-no-tts.tar.bz2)，23.7MB。
- LinuxARM64 [shared-cpu](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-linux-aarch64-shared-cpu.tar.bz2)，26.8MB；名字没有no-tts，**不能声称ASR-only**。
- MacARM64 [shared-no-tts](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-osx-arm64-shared-no-tts.tar.bz2)，17.4MB；Intel可选[universal2-shared-no-tts](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-osx-universal2-shared-no-tts.tar.bz2)，37.5MB。
- Winx64 [shared-MT-Release-no-tts](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-win-x64-shared-MT-Release-no-tts.tar.bz2)，22.2MB。
- WinARM64 [shared-MT-Release-no-tts](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-v1.13.8-win-arm64-shared-MT-Release-no-tts.tar.bz2)，20.9MB。

名字存在不能证明六CLI已通过；不能改后缀冒充JNI。CLI也有Mac签名/Quarantine/executable权限问题。首版不要求玩家本地现场sourcecompile。

## 9. Engine许可、artifact依赖义务、model许可

- [sherpa main LICENSE](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE)：Apache-2.0。ORT：MIT，需matchingrelease实际license/third-party notices。
- whisper.cpp+官方Java binding：MIT；GiviMAD wrapper POM：Apache-2.0，仍须包内whisper/ggml的MIT归属。
- **六stockJNI包含fullTTS**：tag[CMake默认TTS](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/CMakeLists.txt) → [piper-phonemize静态链接](https://github.com/csukuangfj/piper-phonemize/blob/f3ff95afc03640bc1399e113e83361192a2fafb4/CMakeLists.txt) → [eSpeak NG COPYING GPL3](https://github.com/csukuangfj/espeak-ng/blob/ed530aa113046142eb5115cf2fc9157854d0ffe1/COPYING)。六binary均实测espeak_Initialize/espeak-ng/OfflineTts字符串；不是只从可选功能文档猜。
- [eSpeak cmake](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/espeak-ng-for-piper.cmake) exactsourcezip commit `ed530aa113046142eb5115cf2fc9157854d0ffe1`，SHA `e4e262cbe34f7fe21f91f1ba3397f2728e1f30eafbae7853f2b753a9ed13f0dd`。[piper cmake](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/piper-phonemize.cmake) commit`f3ff95afc03640bc1399e113e83361192a2fafb4`，SHA `d9cca4e2bdc7d6dd8dffb96a4668283dbd3f77a9c194a3e530c1e8eba9406a5d`，piper本身MIT。
- stockJAR无license文本，分发物/下载入口应补完整归属、GPL文本与CorrespondingSource取得方式。**只贴上游URL、用户点击下载、子进程隔离都不自动证明分发义务已完成。** GPL网络objectcode传递可用§6(d)等source机制，但须matching完整source、构建/依赖脚本与等价获取；不能泛称一条github链接就是合法sourceoffer。应保存tag、artifactSHA、依赖source与patch/recipe。此报告不作法律相容性保证，不擅自把mod MIT license改成GPL。
- 这是发布依赖事实，不是内容审批。ASR-only去掉不需要的GPL依赖是工程收敛；stockfull独立worker可作为明确许可说明的运行路线，但实际分发义务仍需履行。
- **模型不继承engine许可**。SenseVoiceSmall当前[modelcard](https://huggingface.co/FunAudioLLM/SenseVoiceSmall/raw/main/README.md)标other/model-license，指FunASR [MODEL_LICENSE](https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE)。2024 ONNX包LICENSE是FunASR #license短链接，不是Apache声明。另行模型研究会核exact条款。Paraformer不同source/export同样独立核；Whisperweights也与engine分别核。

可真实Java/JNI部署的小候选组（只给能力/资源取件，不取代质量benchmark）：

1. SenseVoiceSmall INT8：[official228MiB模型包](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2)，[说明](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html)，model.int8.onnx+tokens；中英日韩粤。官方70ms营销数据不是玩家Java25延迟保证。
2. ParaformerSmall INT8：[official79MiB模型包](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2)，[说明](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-paraformer/paraformer-models.html)，model.int8.onnx+tokens；中英。小不自动等于同指令正确率。
3. Whisper multilingual ONNX：[官方index](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/index.html)，encoder+decoder+tokens；不混GGML/GGUF。模型档位/CPU/RSS由同条件benchmark另定。

模型download显式；runtimecatalog拥有可执行依赖，modelcatalog只有family/data。Pythonmacwheel识别结果不能转换为JNI六平台RTF/RSS或兼容性结论。

## 10. 真实 ASR-only 官方 JNI 构建与本地打包结果

经 Root 后续明确要求，本任务在 `/tmp/openallay-native-asr-only-build-20261002` 构建既有官方 JNI；没有改产品代码、没有新识别器。

来源：

- [v1.13.8 source zip](https://codeload.github.com/k2-fsa/sherpa-onnx/zip/refs/tags/v1.13.8)，15,384,893 bytes，SHA `b63b7613812346f2d1396a3a7f94accd47539e3dadc3ea385ba6474c70ab9897`。
- 实际工具：CMake4.3.3、AppleClang21.0.0、ZuluJDK25.0.2 ARM64。upstream JNI CMake读取已有`JAVA_HOME`环境值；没有本任务修改环境。首轮`-D JAVA_HOME`参数无效被warning，实际flags.make证明includes来自Zulu25路径，报告不把无效参数当成功配置。
- ORT下载来自tag内 [onnxruntime-osx-arm64.cmake](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/cmake/onnxruntime-osx-arm64.cmake)：[ORT1.28.2 vendor zip](https://github.com/csukuangfj/onnxruntime-libs/releases/download/v1.28.2/onnxruntime-osx-arm64-1.28.2.zip)，upstreamSHA `d9e5c0c79929e201f5b8eb095e6809a91ce78be9866a1bca0dfab7e20b40ae40`。CMake实际下载并通过该hash检查。

### 选项与构建失败根因

```text
CMAKE_BUILD_TYPE=Release
BUILD_SHARED_LIBS=ON
SHERPA_ONNX_ENABLE_JNI=ON
SHERPA_ONNX_ENABLE_TTS=OFF
SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF
SHERPA_ONNX_ENABLE_PORTAUDIO=OFF
SHERPA_ONNX_ENABLE_BINARY=OFF
SHERPA_ONNX_ENABLE_C_API=OFF
SHERPA_ONNX_BUILD_C_API_EXAMPLES=OFF
SHERPA_ONNX_ENABLE_WEBSOCKET=OFF
SHERPA_ONNX_ENABLE_PYTHON=OFF
SHERPA_ONNX_ENABLE_TESTS=OFF
SHERPA_ONNX_ENABLE_GPU=OFF
SHERPA_ONNX_ENABLE_DIRECTML=OFF
SHERPA_ONNX_USE_PRE_INSTALLED_ONNXRUNTIME_IF_AVAILABLE=OFF
CMAKE_OSX_ARCHITECTURES=arm64
CMAKE_OSX_DEPLOYMENT_TARGET=11.0
```

`cmake --build <build> --target sherpa-onnx-jni --parallel 2`，固定两编译worker。

第一次configure成功，100%编译后link失败：Apple exported_symbols_list仍列16个已由OFF选项排除的TTS/diarization函数，linker报initial-undefines。不是ASR实现缺失，也不是model错误。Root批准仅task-owned源码最小patch，删exact `GeneratedAudio_saveImpl`、8个OfflineTts、7个OfflineSpeakerDiarization exports，保留所有ASRexports。没有改recognizer实现或Java API。

- patch文件：`macos-asr-only-jni-exports.patch`，SHA `8eaa4af645a96b98745ffe40cd1eda386d054033adb86adaaf9d86452ea91eb5`。
- 修补后重link **exit0**；`build-retry.log`到100%。首轮失败日志`configure.log`/`build.log`也保留。
- upstream [JNI CMake](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/jni/CMakeLists.txt)与[export名单](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/jni/sherpa-onnx-symbols.exp)提供可复核根因。对TTS/diarization ON构建不能用这个过滤名单。

### 真实产物与静态验证

| 文件 | bytes | SHA-256 |
|---|---:|---|
| local ASR-only `libsherpa-onnx-jni.dylib` | 2,972,600 | `2c07dd43ae948d0bfa5d35ff749dac90d9b69e08041d497849672069404fd9f6` |
| matching vendor `libonnxruntime.dylib` | 29,006,384 | `b0613d0ae53199a83b05fa48e169211498e9d40d54beaa372068ebe5ec5b0929` |
| local eval native JAR | 9,014,581 | `f37e2368369855b0d60dbc4ce9f245603c39c664381d19f832b601b802b9d703` |

位置：

```text
/tmp/openallay-native-asr-only-build-20261002/
  build-osx-aarch64-asr-only/lib/libsherpa-onnx-jni.dylib
  build-osx-aarch64-asr-only/_deps/onnxruntime-src/lib/libonnxruntime.dylib
  sherpa-onnx-native-lib-osx-aarch64-asr-only-1.13.8-eval.jar
  asr-only-local-artifact.json
  asr-only-eval-package.json
  asr-only-jni-static-inspection.txt
  macos-asr-only-jni-exports.patch
  BUILD-RECIPE.txt
```

实测JNI bytes中`espeak_Initialize`、`espeak-ng`、`OfflineTts`出现数均**0**；构建`_deps`无espeak/piper下载目录。`nm -gU`保留OfflineRecognizer new/delete/create/decode/getResult/setConfig等与OfflineStream accept/release/option全部官方exports。`otool -L`仅ORT和系统libc++/libSystem；Mach-O minOS11.0；仍ad-hoc签名而非notarized。compiled JNI没有声称只剩ASR算法；其他上游非TTS功能仍存在，重点是不用的TTS/diarization及espeak依赖被排除。

本地evalJAR是单一`osx-aarch64` exactresource pair，加`META-INF/openallay-native-notices/`的官方/实际下载dependency licenses、ORT ThirdPartyNotices、patch与recipe。没有Java绑定重写；可配官coreJAR。**新名字与新SHA**，不可把里面dylib替换进stockJAR仍声称stockSHA。未发布、未进行本任务native load。Root可用预录publicPCM做独立真实Java25smoke，不开mic。

ASR-only仍有正常第三方义务：Eigen主要MPL-2.0（含部分BSD/Apache/Minpack文件）、KissFFT BSD、Kaldi/OpenFST/sentencepiece相关Apache/MIT等，ORT自己的ThirdPartyNotices。去掉espeak不等于“native只有Apache/MIT”。本地notice inventory是实下载license收集，不是完整法律履行认证。

### 其他五平台与发布路线

**v1.13.8沒有`native-lib-*-no-tts.jar`。今天真实existing的是六platform fullstockJNI；本任务只成功编译MacARM ASR-only。** CLI/shared-no-tts不保证JNI启用。不存在的5个ASR-only native无法由Mac产物证明。

推荐releaseCI用相同tag/依赖pins/选项与当前官方JNI source：

| CI目标 | upstream现成构建依据 | 对应releasecheck |
|---|---|---|
| Linuxx64 | [.github/workflows/linux-jni.yaml](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/.github/workflows/linux-jni.yaml)，manylinux2014_x86_64 | ELF64 x64，GLIBC/GLIBCXX界，exactpair，noespeak |
| LinuxARM64 | [linux-jni-aarch64.yaml](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/.github/workflows/linux-jni-aarch64.yaml)，manylinux2014_aarch64 | ELF64ARM64，libstdc++/GLIBC，exactpair |
| MacIntel | [macos-jni.yaml](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/.github/workflows/macos-jni.yaml)，x86_64 | Apple exportlist按OFF过滤，load/RPATH，codesign |
| MacApple | 同macos-jni，arm64；本地已真构建 | 同上，nativeartifact已有但Javafixture另验 |
| Winx64 | [windows-x64-jni.yaml](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/.github/workflows/windows-x64-jni.yaml)，MSVC x64 | PE机器/CRTimports，MTRelease pair，Java25load |
| WinARM64 | [windows-arm64-jni.yaml](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/.github/workflows/windows-arm64-jni.yaml)，MSVCARM64 | 非x64仿真，真实ARM64JVM fixture |

CI每条应：fixedsource/hash→原构建OFF选项→matchingORT→exportaudit/noespeak→含license与recipe的独立artifact→记录新SHA→该ABI Java25publicPCM smoke→发布后才入代码catalog。Linuxnative在manylinux里构建时JNI头版本/宿主JDK25运行条件分清，不把不能运行JDK25的老buildcontainer拿来假smoke。

这份matrix/recipe**不是CI已运行的六产物**；本任务未安装crosscompile环境或在玩家机编译。若首版今天用stock六platform下载路线，必须保留§9的GPLfullartifact事实和source/notice义务，而不是改UI让用户点“同意”便宣称license自动解决。

## 11. 必需验证与明确范围

- 六platform分别Java25fixedworker load+预录PCMfixture；invalidmodel、artifactmissing/corrupt、late结果、kill取消/close。不用mic即可验证engine packaging。
- Mac真实目标launcher/JDK的libraryvalidation；WinARM与x64分开实际JVMABI；Linux目标系统runtime。静态header不是通过。
- releaseASR-only产物依赖/符号/notice核验与新SHA；stockfull独立列许可，不能混hash/角色。
- 同条件modelCPU benchmark决定default，不把PythonmacwheelRFT/RSS复制成Java平台性能保证。
- parentcapture/TCC/设备/HUD另做。不增加AEC/自研音频算法、installer、任意shell、用户launcher修改或企业审批。

本轮GitHubAPI403，但releaseHTML/expandedassets与下载均可用；已换可验证公开接口。不会因API403无限猜版本或把下载成功冒充native运行成功。

### 后续补充：stock runtime 对应 source / license 事实（14:43 UTC）

已提供实现任务机器可读 `/tmp/openallay-native-speech-platform-eval-20261002/stock-license-exact-refs.json`：engine、eSpeak、piper exactsource/sha/fulllicense缓存；配套ORT六vendorarchive URL/hash；ORT固定license/notices URL/hash。另有`stock-upstream-dependency-source-declarations.json`记录原tag所有cmake literaldownload/hash声明。它明确是**声明清单**，不是证明全部可选依赖都被linked，也不是GPL履行认证。

matchingMacARM vendor ORT archive根目录实际有`VERSION_NUMBER=1.28.2`、`GIT_COMMIT_ID=33ca9628233dc8f002435e868d4c2e9f82766ca1`。上游source：[Microsoft exactcommit](https://github.com/microsoft/onnxruntime/tree/33ca9628233dc8f002435e868d4c2e9f82766ca1)。其他五platform未读取vendorarchive里的commit，不能假称全相同。

实际Macvendorbundle `LICENSE`与`ThirdPartyNotices.txt`和Microsoft v1.28.2 raw文本**完整bytes/hash相同**：

- [LICENSE](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.28.2/LICENSE)，SHA `2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c`。
- [ThirdPartyNotices.txt](https://raw.githubusercontent.com/microsoft/onnxruntime/v1.28.2/ThirdPartyNotices.txt)，SHA `0e07b95f3a8d6230037707c5c4a2b554d12c4cb67369669ac255635528ffcee2`。

[vendor source recipes exacttag](https://github.com/csukuangfj/onnxruntime-libs/tree/v1.28.2/.github/workflows)的macOS-shared checkout Microsoft v${version}，实际另修改SOVERSION/VERSION行、strip并ad-hoc重签；因此保存vendorrecipe而非仅给Microsoft泛tag。stockartifact当前可运行路线与许可说明由实施任务维护，不能把“打开notices目录”按钮当成source义务已证明。
