package dev.openallay.client.voice;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Explicit downloads only. Verified immutable installs never replace a last-valid model. */
public final class NativeModelInstaller {
    @FunctionalInterface public interface Progress { void update(long bytes, long total); }
    record Download(String name, URI uri, long bytes, String sha256) {}
    @FunctionalInterface interface Downloader {
        void download(Download download, Path file, VoiceCancellation cancellation, Progress progress) throws Exception;
    }
    private static final String MODEL_BASE = "https://huggingface.co/csukuangfj/"
            + "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/2365baeacb507f821a0c8120fcee3d484dba7a07/";
    private static final NativeModelFiles.Model DEFAULT_MODEL = new NativeModelFiles.Model("SenseVoice Small INT8",
            NativeModelFiles.ModelFamily.SENSE_VOICE, List.of(
                    new NativeModelFiles.ModelFile(NativeModelFiles.Role.SENSE_VOICE_MODEL, "model.int8.onnx", 239233841,
                            "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"),
                    new NativeModelFiles.ModelFile(NativeModelFiles.Role.TOKENS, "tokens.txt", 315894,
                            "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc")));
    private static final String MODEL_LICENSE = """
            FunASR Model Open Source License Agreement
            
            Version: 1.1
            
            Copyright (C) [2023-2028] [Alibaba Group]. All rights reserved.
            
            Thank you for choosing the FunASR open-source model. The FunASR open-source model includes a range of free and open industrial models for you to use, modify, share, and learn from.
            
            To ensure better community collaboration, we have established the following agreement, and we hope you will read and comply with its terms.
            Definitions
            
            In this agreement, [FunASR Software] refers to FunASR open-source model weights and their derivatives, including finetuned models; [You] refers to individuals or organizations using, modifying, sharing, and learning from [FunASR Software].
            
            2 License and Restrictions
            
            2.1 License
            
            You are free to use, copy, modify, and share [FunASR Software] under the terms of this agreement.
            
            2.2 Restrictions
            
            When using, copying, modifying, and sharing [FunASR Software], you must attribute the source and author information and retain relevant model names in [FunASR Software].
            
            3 Responsibility and Risk
            
            [FunASR Software] is provided for reference and learning purposes only, and Alibaba Group assumes no responsibility for any direct or indirect losses resulting from your use or modification of [FunASR Software]. You should assume all risks associated with using and modifying [FunASR Software].
            
            4 Community Conduct Guidelines
            
            4.1 Encouraged Behavior
            
            The community welcomes developers and users to engage in discussions about [FunASR Software]. Participants are encouraged to interact in a friendly, polite, and respectful manner to foster constructive discussion and collaboration.
            
            4.2 Prohibited Behavior
            
            Individual or organizational users shall not engage in unjustified denigration, malicious smearing, or baseless insults against [FunASR Software]. Such behavior is considered a violation of the spirit of community cooperation. If a user is found to be engaging in the prohibited behavior mentioned above, it will be considered an automatic forfeiture of all licenses under this agreement.
            
            5 Termination
            
            If you violate any terms of this agreement, your license will automatically terminate, and you must cease using, copying, modifying, and sharing [FunASR Software].
            
            6 Revisions
            
            This agreement may be updated and revised occasionally. The revised agreement will be published in the official repository of [FunASR Software] and will take effect automatically. Continuing to use, copy, modify, and share [FunASR Software] indicates your acceptance of the revised agreement.
            
            7 Miscellaneous
            
            This agreement is governed by the laws of [Country/Region]. If any provision is deemed illegal, invalid, or unenforceable, that provision shall be considered severed from this agreement, and the remaining provisions shall continue to be valid and binding.
            
            If you have any questions or comments regarding this agreement, please contact us.
            
            Copyright © [2023-2028] [Alibaba Group]. All rights reserved.
            
            
            FunASR 模型开源协议
            
            版本号：1.1
            
            版权所有 (C) [2023-2028] [阿里巴巴集团]。保留所有权利。
            
            感谢您选择 FunASR 开源模型。FunASR 开源模型包含一系列免费且开源的工业模型，让大家可以使用、修改、分享和学习该模型。
            
            为了保证更好的社区合作，我们制定了以下协议，希望您仔细阅读并遵守本协议。
            
            1 定义
            
            本协议中，[FunASR 软件]指 FunASR 开源模型权重及其衍生品，包括 Finetune 后的模型；[您]指使用、修改、分享和学习[FunASR 软件]的个人或组织。
            
            2 许可和限制
            
            2.1 许可
            
            您可以在遵守本协议的前提下，自由地使用、复制、修改和分享[FunASR 软件]。
            
            2.2 限制
            
            您在使用、复制、修改和分享[FunASR 软件]时，必须注明出处以及作者信息，并保留[FunASR 软件]中相关模型名称。
            
            3 责任和风险承担
            
            [FunASR 软件]仅作为参考和学习使用，不对您使用或修改[FunASR 软件]造成的任何直接或间接损失承担任何责任。您对[FunASR 软件]的使用和修改应该自行承担风险。
            
            4 社区行为准则
            
            4.1 欢迎交流
            
            社区欢迎开发者与用户对[FunASR 软件]进行交流讨论。交流中请注意保持友好、礼貌和文明，以促进建设性的讨论和合作。
            
            4.2 禁止行为
            
            个人或组织用户不得对[FunASR 软件]进行无端诋毁、恶意抹黑或凭空谩骂。此类行为被视为违反社区合作精神。如被认定从事上述禁止行为，将视为自动放弃本协议下的所有许可。
            
            5 终止
            
            如果您违反本协议的任何条款，您的许可将自动终止，您必须停止使用、复制、修改和分享[FunASR 软件]。
            
            6 修订
            
            本协议可能会不时更新和修订。修订后的协议将在[FunASR 软件]官方仓库发布，并自动生效。如果您继续使用、复制、修改和分享[FunASR 软件]，即表示您同意修订后的协议。
            
            7 其他规定
            
            本协议受到[国家/地区] 的法律管辖。如果任何条款被裁定为不合法、无效或无法执行，则该条款应被视为从本协议中删除，而其余条款应继续有效并具有约束力。
            
            如果您对本协议有任何问题或意见，请联系我们。
            
            版权所有© [2023-2028] [阿里巴巴集团]。保留所有权利。
            """;
    private static final String ATTRIBUTION = """
            SenseVoice Small model: Alibaba / FunAudioLLM; ONNX export: csukuangfj / sherpa-onnx.
            Model source: https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17
            Model revision: 2365baeacb507f821a0c8120fcee3d484dba7a07
            Model license: FunASR Model Open Source License Agreement v1.1 (see MODEL_LICENSE).
            Runtime: sherpa-onnx 1.13.8 with ONNX Runtime. Official stock JNI also links eSpeak NG.
            Stock JNI is not an Apache-only binary. Distribution must meet applicable GPL obligations.
            Runtime source: https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8
            eSpeak NG source: https://github.com/espeak-ng/espeak-ng
            """;
    private final Path installRoot;
    private final NativeModelFiles.Model model;
    private final List<Download> modelDownloads;
    private final Downloader downloader;
    private final boolean installRuntime;

    public NativeModelInstaller(Path installRoot) {
        this(installRoot, DEFAULT_MODEL, DEFAULT_MODEL.files().stream().map(file ->
                new Download(file.path(), URI.create(MODEL_BASE + file.path()), file.bytes(), file.sha256())).toList(),
                NativeModelInstaller::download, true);
    }
    NativeModelInstaller(Path installRoot, NativeModelFiles.Model model, List<Download> downloads,
            Downloader downloader, boolean installRuntime) {
        this.installRoot = Objects.requireNonNull(installRoot).toAbsolutePath().normalize();
        this.model = Objects.requireNonNull(model);
        this.modelDownloads = List.copyOf(downloads);
        this.downloader = Objects.requireNonNull(downloader);
        this.installRuntime = installRuntime;
    }
    public String modelName() { return model.name(); }
    /** Offline runtime import. Only product-pinned filenames and bytes become executable. */
    public void importRuntime(Path sourceDirectory, VoiceCancellation cancellation) throws Exception {
        Objects.requireNonNull(sourceDirectory); Objects.requireNonNull(cancellation); cancellation.check();
        List<NativeRuntimeCatalog.Artifact> artifacts = NativeRuntimeCatalog.artifacts();
        if (!Files.isDirectory(sourceDirectory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid runtime source");
        for (dev.openallay.client.voice.NativeRuntimeCatalog.Artifact artifact : artifacts) NativeModelFiles.verifyFile(sourceDirectory, artifact.name(), artifact.bytes(), artifact.sha256(), cancellation);
        Path runtimeRoot = installRoot.resolve("runtime");
        Path destination = NativeRuntimeCatalog.directory(runtimeRoot);
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            NativeRuntimeCatalog.validate(runtimeRoot, cancellation);
            NativeRuntimeNotices.write(destination); return;
        }
        Path staging = Files.createTempDirectory(destination.getParent(), ".runtime-import-");
        try {
            for (dev.openallay.client.voice.NativeRuntimeCatalog.Artifact artifact : artifacts) {
                cancellation.check();
                try (InputStream input = Files.newInputStream(sourceDirectory.resolve(artifact.name()));
                        AutoCloseable hook = cancellation.onCancel(() -> close(input));
                        java.io.OutputStream output = Files.newOutputStream(staging.resolve(artifact.name()), StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[64 * 1024]; long bytes = 0; int count;
                    while ((count = input.read(buffer)) != -1) {
                        cancellation.check(); bytes += count;
                        if (bytes > artifact.bytes()) throw new NativeSpeechToText.Failure("model_integrity");
                        output.write(buffer, 0, count);
                    }
                } catch (IOException failure) { cancellation.check(); throw failure; }
                NativeModelFiles.verifyFile(staging, artifact.name(), artifact.bytes(), artifact.sha256(), cancellation);
            }
            cancellation.check();
            NativeRuntimeNotices.write(staging);
            try { Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (FileAlreadyExistsException raced) { NativeRuntimeCatalog.validate(runtimeRoot, cancellation); }
            NativeRuntimeCatalog.validate(runtimeRoot, cancellation);
            NativeRuntimeNotices.write(destination);
        } finally { deleteStaging(staging); }
    }
    public Path install(VoiceCancellation cancellation, Progress progress) throws Exception {
        Objects.requireNonNull(cancellation); Objects.requireNonNull(progress); cancellation.check();
        if (installRuntime) NativeRuntimeCatalog.platform();
        Files.createDirectories(installRoot);
        if (!Files.isDirectory(installRoot, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid install directory");
        byte[] manifest = NativeModelFiles.json(model).getBytes(StandardCharsets.UTF_8);
        String identity = HexFormat.of().formatHex(NativeModelFiles.digest().digest(manifest));
        Path destination = installRoot.resolve("model-" + identity);
        List<Download> runtimeDownloads = installRuntime ? NativeRuntimeCatalog.artifacts().stream().map(artifact ->
                new Download(artifact.name(), artifact.uri(), artifact.bytes(), artifact.sha256())).toList() : List.of();
        long total = modelDownloads.stream().mapToLong(Download::bytes).sum()
                + runtimeDownloads.stream().mapToLong(Download::bytes).sum();
        long[] completed = {0};
        progress.update(0, total);
        if (installRuntime) {
            Path runtimeRoot = installRoot.resolve("runtime");
            Path runtime = NativeRuntimeCatalog.directory(runtimeRoot);
            if (Files.exists(runtime, LinkOption.NOFOLLOW_LINKS)) {
                NativeRuntimeCatalog.validate(runtimeRoot, cancellation);
                NativeRuntimeNotices.write(runtime);
                completed[0] += runtimeDownloads.stream().mapToLong(Download::bytes).sum();
                progress.update(completed[0], total);
            } else {
                Files.createDirectories(runtime.getParent());
                Path staging = Files.createTempDirectory(runtime.getParent(), ".download-");
                try {
                    fetchAll(runtimeDownloads, staging, cancellation, progress, completed, total);
                    cancellation.check();
                    NativeRuntimeNotices.write(staging);
                    try { Files.move(staging, runtime, StandardCopyOption.ATOMIC_MOVE); }
                    catch (FileAlreadyExistsException raced) { NativeRuntimeCatalog.validate(runtimeRoot, cancellation); }
                    NativeRuntimeCatalog.validate(runtimeRoot, cancellation);
                    NativeRuntimeNotices.write(runtime);
                } finally { deleteStaging(staging); }
            }
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            NativeModelFiles.readValidated(destination, cancellation);
            progress.update(total, total); cancellation.check(); return destination;
        }
        Path staging = Files.createTempDirectory(installRoot, ".model-download-");
        try {
            fetchAll(modelDownloads, staging, cancellation, progress, completed, total);
            Files.write(staging.resolve(NativeModelFiles.MANIFEST), manifest, StandardOpenOption.CREATE_NEW);
            Files.writeString(staging.resolve("MODEL_LICENSE"), MODEL_LICENSE, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            Files.writeString(staging.resolve("ATTRIBUTION.txt"), ATTRIBUTION, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            NativeModelFiles.readValidated(staging, cancellation);
            cancellation.check();
            try { Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (FileAlreadyExistsException raced) { NativeModelFiles.readValidated(destination, cancellation); }
            cancellation.check(); progress.update(total, total); return destination;
        } finally { deleteStaging(staging); }
    }
    private void fetchAll(List<Download> downloads, Path staging, VoiceCancellation cancellation, Progress progress,
            long[] completed, long total) throws Exception {
        for (Download download : downloads) {
            cancellation.check(); long baseline = completed[0];
            Path file = staging.resolve(download.name());
            downloader.download(download, file, cancellation, (bytes, ignored) -> progress.update(baseline + bytes, total));
            NativeModelFiles.verifyFile(staging, download.name(), download.bytes(), download.sha256(), cancellation);
            completed[0] += download.bytes(); progress.update(completed[0], total);
        }
    }
    static void download(Download download, Path file, VoiceCancellation cancellation, Progress progress) throws Exception {
        try {
            VoiceModelDownload.download(download.uri(), download.bytes(), download.sha256(), file,
                    cancellation, progress::update);
        } catch (VoiceModelDownload.IntegrityFailure invalid) {
            throw new NativeSpeechToText.Failure("model_integrity");
        }
    }
    private static void close(InputStream input) { try { input.close(); } catch (IOException ignored) {} }
    private static void deleteStaging(Path staging) {
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) return;
        try (java.util.stream.Stream<java.nio.file.Path> entries = Files.walk(staging)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
        } catch (IOException ignored) { /* Never remove an installed model or a world save. */ }
    }
}
