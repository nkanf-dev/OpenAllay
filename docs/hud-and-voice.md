# HUD, notifications and voice input

OpenAllay keeps one connection-scoped conversation service. The K screen, optional HUD and compact input view share the same selected session and draft.

## UI settings

Open **Settings → UI**. The Fullscreen, HUD and Notifications groups have independent controls.

- The HUD is off by default. Enable it to see task state and a short latest-result preview during play.
- Edit HUD layout to drag the panel or resize it. Position, anchor, size, scale and background opacity are saved when you apply. Background opacity does not dim the text.
- Normal gameplay HUD does not capture the mouse. Explicit layout editing or compact input opens a non-pausing Screen and gives the cursor to that view. Escape returns to gameplay.
- HUD toggle, editor, compact input and push-to-talk keys are rebindable under Minecraft controls. New actions default to unbound to avoid modpack conflicts; K remains the default main conversation key.
- Notifications can be enabled without the HUD. They report accepted live replies/cards/task results, not restored history or each streamed token. Default suppression applies while the matching result is visible in the main conversation; the Always choice permits notifications while that view is open. Minecraft F1 hiding still applies to native toasts.

## Local Native voice input

Open **Settings → Voice**, choose Native, then explicitly download the default model/runtime or import verified files. Nothing is downloaded and no microphone is opened merely by starting Minecraft.

The default recognizer is **SenseVoiceSmall INT8 (2024)** using **sherpa-onnx 1.13.8** in an isolated Java worker. The model is about **239 MB**, plus the current platform's small runtime JARs. Runtime and model files are checked against fixed hashes. Model data cannot choose an executable runtime.

This default was selected from a small reproducible offline comparison of 32 public Chinese, English, mixed-language and derived-noise clips. It was more balanced on that subset than the tested Paraformer and Whisper-small INT8 exports. This is not a universal accuracy ranking or a game-performance guarantee. A worker peak around 866 MiB was observed in the benchmark; model file size is not the RAM budget.

Select or refresh the microphone device explicitly, enable Voice, and bind push-to-talk. Hold the key to record, then release it to transcribe. The microphone button is an alternative start/finish action. The final text enters an **editable draft**, not an automatically sent task. Existing text/images and pending-edit intent are preserved. If the draft changed while recognition was running, the result becomes a pending insertion instead of overwriting it.

- Voice recording/transcribing feedback is independent of HUD/notification enable switches.
- Losing window focus, disconnecting, hiding available feedback with F1, cancelling or losing the held key revokes the operation. This does not cancel an Agent task already in progress.
- If a platform audio provider hangs during device opening, the caller has a bounded wait; canceled late openings are closed/discarded. A still-hung provider returns an actionable busy/open error rather than allowing unbounded new opening jobs.
- Native recognition does not upload audio to a cloud provider. It uses CPU by default, with a configurable thread count.
- HTTP transcription is a separate optional backend. Choose its endpoint/model/key explicitly. Local recognition does not silently fall back to HTTP. Voice credentials have a separate owned store.

## Platform and permissions

Official CPU runtime artifacts are available for Windows x64/ARM64, macOS x64/ARM64 and Linux x64/ARM64. The correct artifact is selected by the JVM architecture. The final Java-to-JNI public-audio check was executed on macOS ARM64 with Java 25. Other platforms' artifacts and CPU dependencies were inspected, but they were not executed in this task.

Actual microphone devices and OS first-grant behaviour were not tested in this delivery. On macOS, the launcher/JRE app identity must contain the required microphone usage declaration. Missing declarations and denied access produce distinct actionable errors; the mod does not rewrite or resign the launcher. Windows/Linux devices and sandbox permissions can also differ.

The stock official JNI contains TTS/eSpeak dependencies even though this feature uses only ASR. **Runtime licenses and source** opens the managed runtime information folder. Full license texts and pinned source/build references are included; these references are not a legal certification that every downstream distribution has satisfied every corresponding-source obligation. The SenseVoice model's custom license is separate from the engine license.

TTS, wake-word listening and realtime duplex voice are not implemented in this feature. Automatic game screenshots are not added.

## Verification boundary

The source was tested with fake capture devices and deterministic ownership/lifecycle tests, native common/loader builds, and real offline public-audio recognition. A real game/visual/IME acceptance pass, actual microphone permissions and other-platform execution remain separate verification steps. No paid provider call or real microphone/clipboard capture was used during development tests.
