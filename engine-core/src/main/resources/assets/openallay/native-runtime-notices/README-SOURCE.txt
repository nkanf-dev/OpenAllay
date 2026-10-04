Source and build provenance for sherpa-onnx 1.13.8 official stock JNI

Engine source archive:
https://codeload.github.com/k2-fsa/sherpa-onnx/zip/refs/tags/v1.13.8
SHA-256 b63b7613812346f2d1396a3a7f94accd47539e3dadc3ea385ba6474c70ab9897
15384893 bytes

The archive contains the upstream build recipe and dependency declarations:
.github/workflows/run-java-test.yaml
sherpa-onnx/java-api/pom.xml
cmake/espeak-ng-for-piper.cmake
cmake/piper-phonemize.cmake
cmake/onnxruntime-*.cmake

The official Java workflow builds the JVM jar with Maven and JNI with CMake:
SHERPA_ONNX_ENABLE_PYTHON=OFF; SHERPA_ONNX_ENABLE_TESTS=OFF;
SHERPA_ONNX_ENABLE_CHECK=OFF; BUILD_SHARED_LIBS=ON;
SHERPA_ONNX_ENABLE_PORTAUDIO=OFF; SHERPA_ONNX_ENABLE_BINARY=OFF;
BUILD_ESPEAK_NG_EXE=OFF; SHERPA_ONNX_ENABLE_JNI=ON.
These are matching upstream declarations, not a claim of reproduced official
binaries. The source archive includes build instructions; this notice does not
promise or certify complete corresponding source for every vendor dependency.

Exact eSpeak NG dependency source:
https://github.com/csukuangfj/espeak-ng/archive/ed530aa113046142eb5115cf2fc9157854d0ffe1.zip
SHA-256 e4e262cbe34f7fe21f91f1ba3397f2728e1f30eafbae7853f2b753a9ed13f0dd

Exact Piper phonemize dependency source:
https://github.com/csukuangfj/piper-phonemize/archive/f3ff95afc03640bc1399e113e83361192a2fafb4.zip
SHA-256 d9cca4e2bdc7d6dd8dffb96a4668283dbd3f77a9c194a3e530c1e8eba9406a5d

The platform ORT vendor archives and their hashes are in SOURCES.json.
All six declared CPU bundles identify ONNX Runtime 1.28.2. The inspected macOS
ARM64 vendor bundle records GIT_COMMIT_ID 33ca9628233dc8f002435e868d4c2e9f82766ca1.
The full MIT license and ThirdPartyNotices here were read from that vendor bundle.
The precise build recipes and complete source mapping of every platform's ORT
vendor binary have not been independently established. Reference upstream tag:
https://github.com/microsoft/onnxruntime/tree/v1.28.2

No source offer is made by this file. GPL distribution requirements must be
addressed using the actual applicable distribution arrangement. The installer
downloads immutable third-party release artifacts only after explicit user action.
