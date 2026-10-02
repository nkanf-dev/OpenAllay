# Native speech: reproducible offline CPU benchmark

**Date:** 2026-10-02. **Status:** three actual native ASR models completed.  
**Task-owned environment:** `/tmp/openallay-native-speech-benchmark-20261002`. No OpenAllay source changes, Gradle, game,
microphone, clipboard, paid provider, network inference, or GPU use.

## Decision supported by this experiment

**SenseVoiceSmall INT8 is the best-balanced first-release candidate among these
three exact configurations on this tiny local subset.** It preserves near-Paraformer
Mandarin accuracy while doing better on this English and natural code-switch sample.
The same-engine latency is low enough for offline per-clip PTT. This supports the
parent's provisional choice, not a global model ranking or a real-time capture claim.

- **Paraformer-large zh/en INT8** is a useful explicit alternative. It has slightly
  lower Mandarin CER and warm RTF here. English-only output is less accurate here.
- **Whisper small multilingual INT8** is a tested fallback, not a download-only
  candidate. It matches SenseVoice's English WER here but is slower and worse on
  this Mandarin/mixed subset. This does **not** rank Whisper large-v3, turbo,
  whisper.cpp quantization, beam decoding, or another export/backend.
- Engine maturity and model permission are separate. SenseVoice's custom source
  license needs exact-artifact review. This document is not redistribution approval.

## What actually ran

- Apple M4, arm64, macOS 26.3; 16 GiB RAM; ten physical CPU cores available.
- CPU execution provider, four native inference threads. Native models ran
  **sequentially**, one fresh process per model, not against one another in parallel.
- CPython 3.12.13 in the task-owned uv `.venv`. Official `sherpa-onnx==1.13.8`
  plus `sherpa-onnx-core==1.13.8`. The actual loaded runtime reports ONNX Runtime
  **1.28.2**. NumPy 2.5.3, SoundFile 0.14.0, jiwer 4.0.0, RapidFuzz 3.14.6,
  OpenCC 0.1.7 are pinned in `uv.lock`.
- Mature `sherpa_onnx.OfflineRecognizer.from_sense_voice`, `.from_paraformer`,
  and `.from_whisper` APIs do the ASR. The scripts do not implement a recognizer,
  tokenizer decoder, VAD, or an ASR substitute.
- SenseVoice: automatic language, `use_itn=False`; Paraformer: zh/en checkpoint;
  Whisper: multilingual small, INT8 encoder+decoder, automatic language,
  `task=transcribe`. All use greedy search. No hotwords, prompts, post-hoc repairs,
  custom lexicons, clipping, denoiser, or VAD trimming.
- All input is mono, 16 kHz. Original FLEURS floats and ASCEND PCM16 are read
  by SoundFile. The only audio modification is the explicitly labeled noise subset.

### Deployment boundary

These are actual CPU native-model calls through the official Python binding on
**macOS arm64 only**. They are **not** an actual Java/JNI integration pass, a
six-platform test, Java 25 proof, or product packaging approval. The same ONNX
artifacts can feed the independent Java/JNI fixture smoke test, but that test must
run and report its own result. See `native-speech-platform-engine.md` for JNI
artifact provenance, dependent-library and ASR-only packaging/license gates.
The full official native bundle is not asserted to be Apache-only.

## Dataset and coverage

32 clips, **208.3299375 seconds**, from 28 original recordings plus four derived
noise clips. Each exact clip is decoded twice in the same loaded model process:
pass 0 includes first-use inference; pass 1 is the warm pass. There are 64 outputs
per model and 192 total compared outputs. All 32 hypotheses were identical across
pass 0/pass 1 for each model; accuracy is reported once, not double-counted.

| Group | Clips / seconds | License and source | Coverage limit |
|---|---:|---|---|
| Mandarin read speech | 16 / 111.96 | Google FLEURS `cmn_hans_cn`, dev; CC BY 4.0 | Short 3–9 s utterances; eight male/eight female labels; distinct sentence IDs. Some labels contain English glosses, names, WiFi/AI or numerals. Not pure-only Mandarin. |
| English read speech | 4 / 29.82 | Google FLEURS `en_us`, dev; CC BY 4.0 | Two male/two female labels; four short sentences only. |
| Natural zh/en code-switch | 8 / 39.2499375 | CAiRE ASCEND test; CC BY SA 4.0 | Four clips each from **two speakers**. Require published `mixed` label, both scripts, 3–8 s, no `[UNK]`. Conversational labels can be ambiguous. |
| Noise stress | 4 / 27.30 | Derived from first four selected FLEURS zh clips; CC BY 4.0 | Deterministic Gaussian noise, 10 dB full-clip RMS SNR; not real game, keyboard, room or headset noise. |

Selection is deterministic: SHA-256 filename/ID order, duration range, distinct
FLEURS sentence IDs, and the declared gender/speaker caps. It never uses ASR output
or a preferred model's success. `prepare_fixtures.py` and `prepare_ascend.py` expose
all criteria. Regeneration was run and reproduced all 32 audio SHA-256 hashes.

**No synthetic speech is used as quality evidence. No actual microphone or game
was tested. Minecraft/mod-specific names, command vocabulary, long recording,
far-field/reverberation, silence/hallucination, cancellation, online streaming,
or accent-wide coverage were not tested.** Natural mixed terms include
`department`, `computer language`, `major`, `uniforms`, `entry level`, `smart phone`
and `project`; this is not Minecraft terminology validation. Model training
contamination/overlap with these public datasets is unknown.

Pinned dataset revisions:

- FLEURS: `70bb2e84b976b7e960aa89f1c648e09c59f894dd`.
- ASCEND: `737e9800ae31be9932ba8464c80366559bd28424`.

`native-speech-benchmark/fixtures/manifest.json` contains unchanged source labels,
source IDs, source URLs/revisions, license, waveform path, sample count, duration,
and SHA-256. Attribution and modification notices are in `ATTRIBUTION.md`.

## Scoring method

The mature **jiwer 4.0.0** library computes substitutions, deletions and insertions.
CER/WER are corpus edit counts divided by corpus reference units, **not** the
average of per-clip percentages. RTF is total inference seconds divided by total
audio seconds, **not** the unweighted average of per-clip RTF.

Preprocessing is fixed for both labels and hypotheses: Unicode NFKC, OpenCC `t2s`,
lowercase, replace Unicode punctuation/symbols with spaces, collapse whitespace.
CER then removes whitespace and measures characters. English WER measures
whitespace words after that normalization. Hyphens/apostrophes become boundaries;
there is no spelling, semantic, or numeric-equivalence correction.

For mixed speech, **TER** uses each Han character as one unit and each contiguous
Latin/number word as one unit, then calls jiwer's word-edit computation. It is
explicitly named mixed TER, **not universal Chinese WER**. It prevents one long
English word being treated as many character units in the mixed primary score.
Raw mixed CER is also shown for visibility.

Important label/format limits:

- FLEURS raw labels include `(AI)`, `(Martelly)`, `(CEP)`, and numeric digits.
  Some outputs omit those glosses or spell numbers as Chinese words. The exact-label
  CER counts those differences. We did not manually relabel based on a model's
  output or claim every edit is an acoustic recognition error.
- ASCEND raw label `extra申请extra` is emitted as `exchange ...` by both faster
  models, and one label says `problem language` while outputs say `program language`.
  Labels remain unchanged; these are not silently resolved in a preferred model's
  favor. The small conversational corpus does not establish transcript perfection.
- The four noise clips share originals with the clean group. All three models
  perfectly matched the normalized first-four clean labels. A 0% noise score on
  64 reference characters is not evidence of broad noise robustness.

## Accuracy: actual warm-pass results

| Model / exact tested form | FLEURS zh CER (16) | ASCEND mixed CER / TER (8) | FLEURS en CER / WER (4) | Derived 10 dB zh CER (4) |
|---|---:|---:|---:|---:|
| SenseVoiceSmall INT8 (2024-07-17) | 7.96% | 15.48% / 10.06% | 4.01% / 4.94% | 1.56% |
| Paraformer-large zh/en INT8 (2024-03-09) | 7.16% | 19.84% / 11.95% | 13.90% / 29.63% | 0.00% |
| Whisper small multilingual INT8 | 26.53% | 30.95% / 28.93% | 2.94% / 4.94% | 23.44% |


The edit denominators are deliberately shown because the corpus is small:

| Model | zh errors / reference chars | mixed token errors / reference units | en word errors / reference words | noise errors / reference chars |
|---|---:|---:|---:|---:|
| sensevoice | 30 / 377 | 16 / 159 | 4 / 81 | 1 / 64 |
| paraformer | 27 / 377 | 19 / 159 | 24 / 81 | 0 / 64 |
| whisper | 100 / 377 | 46 / 159 | 4 / 81 | 15 / 64 |


Aggregate all-group CER/TER exist in CSV for reproducibility, but a mixed
Chinese/English plus duplicated-noise aggregate is not a fair standalone winner
metric. Use the groups above.

## Speed and memory: actual results

| Model | Warm RTF: zh / mixed / en / noise | All duration-weighted warm RTF | Fresh-process model load | First clip / same clip warm | Peak RSS | Warm RSS max / final |
|---|---:|---:|---:|---:|---:|---:|
| sensevoice | 0.01214 / 0.01189 / 0.01128 / 0.01139 | 0.01187 | 0.667 s | 0.0717 / 0.0777 s | 865.7 MiB | 742.0 / 740.2 MiB |
| paraformer | 0.00994 / 0.01513 / 0.00970 / 0.00936 | 0.01081 | 1.399 s | 0.1106 / 0.0538 s | 837.1 MiB | 785.2 / 386.1 MiB |
| whisper | 0.19901 / 0.20072 / 0.17282 / 0.13440 | 0.18712 | 1.011 s | 1.7379 / 0.8527 s | 1129.8 MiB | 1129.8 / 1098.6 MiB |


- RTF includes stream creation, feature extraction, waveform acceptance, native
  decode, and result retrieval. It excludes audio file read and model construction.
  Per-clip file-read seconds are separately recorded in CSV.
- Fresh-process model load includes session/model construction after imports.
  Import times: SenseVoice 0.118 s,
  Paraformer 0.146 s,
  Whisper 0.155 s.
- **Fresh process does not mean OS-cache-cold.** Files had just been downloaded;
  SenseVoice also had a 24-clip preflight. The filesystem cache was not evicted.
  First-inference and warm timings are separated, not marketed as cold disk latency.
- RSS is the Python/native process RSS, not model file size. Loaded-model RSS:
  SenseVoice 852.8 MiB, Paraformer 825.3 MiB, Whisper 651.2 MiB. Peak uses macOS
  `resource.getrusage().ru_maxrss` in bytes, corroborated by `/usr/bin/time -l`.
  Warm RSS is sampled after clips; transient allocation between samples can be
  higher and is reflected in the process peak.
- Runtime files total 228.45 MiB SenseVoice, 216.87 MiB Paraformer and 358.09 MiB
  Whisper. Model loads may use more RAM than the quantized file size.
- This was a shared development host, one measured full run per model, no CPU
  affinity or controlled thermal state. No confidence interval or laptop/game-load
  latency guarantee is claimed. The numeric RTF is not a JNI performance claim.

## Downloads were measured separately

Public model+audio source downloads total **1,524,151,241 bytes (1.524 GB)**, plus
small raw TSVs, metadata and package wheels. This stays below the intended 2 GB
model/dataset budget. Extraction expands disk use beyond network download size;
it is not a 1.5 GB runtime distribution. Models and network transfers are excluded
from inference timing. Transfers were parallel and share bandwidth, so these
elapsed values are diagnostic provenance, not a model performance ranking.

| Public asset | Download bytes | Observed transfer elapsed | SHA-256 |
|---|---:|---:|---|
| sensevoice | 163,002,883 | 179.51 s | `7d1efa2138a65b0b488df37f8b89e3d91a60676e416f515b952358d83dfd347e` |
| fleurs_zh | 217,347,747 | 52.01 s | `3bc33212d5974eef7feb04bc4792458d6cd7e14ff10a1a24772f3c45ea87a822` |
| fleurs_en | 171,250,900 | 175.45 s | `2658fda72f199e12676ecac9415094667a4e14e149b146e568ea00b2a2f0954c` |
| paraformer_model | 227,330,205 | 237.16 s | `90bc03034ae1bef9575f8cc798cd1519c8be8aa9e8b458a033e32017ff4d584c` |
| paraformer_tokens | 75,354 | 2.47 s | `6c0e3b35cece259829e6cb5b8d90d13db88f61ea3a2953d11898e4b2bfd7a2e2` |
| ascend | 105,756,434 | 29.70 s | `a4c81d2b5ed6124f052089a695972808c16e0ce0c365ec9773c5d1a8fcf043a7` |
| whisper | 639,387,718 | 690.54 s | `486a46afbb7ba798507190ffe02fea2dd726049af212e774537efac6afb210a6` |


Whisper's initial transfer stopped after 600 s with curl 28 and 571,129,280 bytes.
One bounded HTTP Range continuation completed in 90.37 s. The final full archive
is 639,387,718 bytes; its SHA-256 and extracted runtime hashes were verified before
inference. The partial file was never presented as a valid model. A fresh future
reproduction can instead sparse-download the three exact INT8 files from the
pinned official converter repository, 375.49 MB total, matching `model-pins.json`.
That is a bandwidth optimization, not the observed archive transfer measurement.

## Real failures and bounded recovery

- Initial unverified `sherpa-onnx==1.12.15` pin was unpublished. Official PyPI JSON
  showed 1.13.8; the experiment pins that actual current release.
- GitHub releases REST API returned 403; official release `expanded_assets` HTML
  and direct release downloads worked. FLEURS rows API returned 500; official raw
  TSV + audio archives worked. No network ASR fallback was used.
- The initial 1.13.8 Python wheel import failed because `@rpath/libonnxruntime.dylib`
  was absent. Official PyPI metadata specifies `sherpa-onnx-core==1.13.8`, but the
  initial uv wheel resolution did not pull it. Explicitly adding the matching
  **official dependency** restored import and all three actual native runs.
  No guessed ABI libraries or JNI DLLs were copied into the Python package.
- The first ASCEND selection required at most two clips per speaker, but this
  public test parquet contains only speakers 3 and 17. Eight clips require four
  each. The corrected rule and narrow speaker coverage are recorded, not concealed.
- A score command ran before a live result file had finished and failed on an
  empty/incomplete run. Final scoring runs only after completed inference; it now
  requires exactly 64 rows per model. One diagnostic CLI quote error was replaced
  by a standalone inspection script. See `failures.json` for exact statuses.

## License boundaries

The audio fixtures are openly licensed and attributed. Selected ASCEND originals
are redistributed under **CC BY SA 4.0**; derived FLEURS noise carries modification
notices under **CC BY 4.0**. No private player audio or environment material is included.

Model weights are **not** bundled in the small repro archive:

- SenseVoice source card: OTHER / **FunASR Model Open Source License Agreement v1.1**.
  The converted tar's LICENSE links the FunASR license. Do not call it MIT/Apache
  merely because runtime code or another converted repo uses that label.
- Paraformer exact vocab8358 TensorFlow source: official ModelScope metadata/card
  says **Apache License 2.0**. This is not the different vocab8404 PyTorch checkpoint.
- Whisper original repository states **code and model weights MIT**. Conversion
  metadata does not replace the original notice requirement.

See `ATTRIBUTION.md`, `native-speech-model-comparison.md`, and
`native-speech-platform-engine.md` for source-specific evidence and native-bundle
restrictions. This benchmark does not approve a model or native bundle for release.

## Reproduction and handoff

Small archive directory: `/Users/nkanf/docs/OpenAllay/2026-10-02-hud-ui-voice/native-speech-benchmark`. It includes:

- `pyproject.toml`, `uv.lock`, `commands.txt`, standalone extraction/inference/score
  scripts, and `hardware.json` (only necessary public host characteristics).
- Exact source download URLs, sizes, SHA-256 and elapsed in `assets.json`;
  exact runtime ONNX/token pins in `model-pins.json`.
- 32 licensed WAV fixtures plus unchanged references, attribution and clip hashes.
- Actual `*-run1.jsonl`, `*-summary.json`, `per-clip.csv`, `group-summary.csv`;
  selected loader/run/score logs and real failures.

From a fresh task-owned copy, `uv sync --frozen`; `uv run --no-sync python
 download_assets.py`; prepare the two fixture sets; run models **sequentially**
with the commands in `commands.txt`; score after completion. Do not install this
experiment into the agent kernel or the OpenAllay product environment.

For the independent Java/JNI smoke gate, the existing source model is:

- `/tmp/openallay-native-speech-benchmark-20261002/models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/model.int8.onnx`
- `/tmp/openallay-native-speech-benchmark-20261002/models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/tokens.txt`
- Fixture `/tmp/openallay-native-speech-benchmark-20261002/fixtures/selected/zh_01.wav`; raw label:
  `他称，他制作了一个 WiFi 门铃。`

That source FLEURS fixture is FLOAT WAV. An explicit **public** PCM16 adaptation
for the independent Java reader is already available at
`/tmp/openallay-native-speech-benchmark-20261002/fixtures/selected/zh_01_java_pcm16.wav` (mono, 16 kHz, 178,604 bytes), SHA-256
`e3770e5da7461933ab56fee9e57b220e38f781c392d9a00750a42f95ad5e7aa0`. `make_pcm_fixture.py` and
`fixtures/java-pcm-smoke.json` record the format change and CC BY 4.0 provenance.
It is **not included** in the 32-clip quality corpus. The expected normalized
inference here is `他称他制作了一个 wifi 门铃`. Actual Java/native load, PCM
handling and cancellation require their own smoke, not inference from these
Python results.

### Bottom line

Proceed with **SenseVoiceSmall INT8 + the mature sherpa engine** as a provisional
first-release offline PTT candidate, subject to exact license and real JNI gate.
Keep Paraformer as a model-family option. Keep this tested Whisper-small form as
an informed fallback, not a claim about every Whisper model. Add real player
term/capture/game-noise fixtures with permission before claiming game-wide voice
quality or real-time performance.
