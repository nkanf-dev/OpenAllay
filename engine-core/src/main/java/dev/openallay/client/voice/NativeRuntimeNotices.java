package dev.openallay.client.voice;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.List;

/** Fixed runtime notices. Model manifests cannot supply notices or executable selections. */
public final class NativeRuntimeNotices {
    private static final String RESOURCE_ROOT = "/assets/openallay/native-runtime-notices/";
    record Notice(String name, int bytes, String sha256) {}
    private static final List<Notice> NOTICES = List.of(
            new Notice("ASR-DEPENDENCY-eigen-src-LICENSE.txt", 16725, "1f256ecad192880510e84ad60474eab7589218784b9a50bc7ceee34c2b91f1d5"),
            new Notice("ASR-DEPENDENCY-json-src-LICENSE.MIT.txt", 1076, "46a65cffd1ea955132d95a8dd921640714a8d6b537d2e4e482d31145ae95b603"),
            new Notice("ASR-DEPENDENCY-kaldi_decoder-src-LICENSE.txt", 11358, "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"),
            new Notice("ASR-DEPENDENCY-kaldi_native_fbank-src-LICENSE.txt", 11358, "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"),
            new Notice("ASR-DEPENDENCY-kaldifst-src-LICENSE.txt", 11728, "a682d6efd1ee5dee08a8e405c233c2c198ea70ae0718129daa83ab58cfe31c5d"),
            new Notice("ASR-DEPENDENCY-kissfft-Unlicense.txt", 1521, "c543dab3c100d8741a83b02f397746b1e10fc0046f30b942f308191a68e99407"),
            new Notice("ASR-DEPENDENCY-onnxruntime-src-LICENSE.txt", 1073, "2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c"),
            new Notice("ASR-DEPENDENCY-onnxruntime-src-ThirdPartyNotices.txt.txt", 325054, "0e07b95f3a8d6230037707c5c4a2b554d12c4cb67369669ac255635528ffcee2"),
            new Notice("ASR-DEPENDENCY-sherpa-onnx-LICENSE.txt.txt", 11358, "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"),
            new Notice("ASR-DEPENDENCY-simple-sentencepiece-src-LICENSE.txt", 11357, "c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4"),
            new Notice("CORRESPONDING-SOURCE.txt", 1638, "7f992eac8ad6b7da5f3c30366a2b8e7f0d0f36e65c5a47e035bb37d345d16b12"),
            new Notice("GPL3.txt", 35147, "8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903"),
            new Notice("ONNXRUNTIME-MIT.txt", 1073, "2f07c72751aed99790b8a4869cf2311df85a860b22ded05fa22803587a48922c"),
            new Notice("ONNXRUNTIME-ThirdPartyNotices.txt", 325054, "0e07b95f3a8d6230037707c5c4a2b554d12c4cb67369669ac255635528ffcee2"),
            new Notice("PIPER-MIT.txt", 1071, "13746d509d74e55ea2265fbef204bb7cdbf84a8315b0207e988326cb54387028"),
            new Notice("README-SOURCE.txt", 2205, "d647ec36e8cc56b355972455c705989c744da9e5924eb1d94319f7aaaef41a1e"),
            new Notice("SHERPA-APACHE-2.0.txt", 11358, "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"),
            new Notice("SOURCES.json", 7430, "80eb29c9adf093381ffd51e501a0c2c57a383b29a5c586ae3b9b3db1a8a14a2e"),
            new Notice("THIRD_PARTY_NOTICES.txt", 1380, "3d9156d6c473ab39342c8f4507aa3257c9d2c2fb4968e84b896f4b9c4639d406"),
            new Notice("UPSTREAM-DEPENDENCY-SOURCE-DECLARATIONS.json", 30622, "5d067f3d94a5fa9d4813451d9c9039f13fd45198317225e401c18d03a9f43178"));
    private NativeRuntimeNotices() {}

    /** Call only after validating product-owned runtime jars, and before promoting new installs. */
    public static void write(Path runtimeDirectory) throws IOException {
        if (!Files.isDirectory(runtimeDirectory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid runtime notice directory");
        for (Notice notice : NOTICES) {
            byte[] bytes = resource(notice);
            Path destination = runtimeDirectory.resolve(notice.name());
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                verify(destination, notice); continue;
            }
            Path staging = Files.createTempFile(runtimeDirectory, ".notice-", ".tmp");
            try {
                Files.write(staging, bytes);
                try { Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE); }
                catch (FileAlreadyExistsException raced) { verify(destination, notice); }
                verify(destination, notice);
            } finally { Files.deleteIfExists(staging); }
        }
    }
    static List<Notice> notices() { return NOTICES; }
    static byte[] resource(Notice notice) throws IOException {
        try (InputStream input = NativeRuntimeNotices.class.getResourceAsStream(RESOURCE_ROOT + notice.name())) {
            if (input == null) throw new IOException("Missing runtime notice resource");
            byte[] bytes = input.readNBytes(notice.bytes() + 1);
            if (bytes.length != notice.bytes() || !hash(bytes).equals(notice.sha256())) throw new IOException("Invalid runtime notice resource");
            return bytes;
        }
    }
    private static void verify(Path file, Notice notice) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != notice.bytes()) throw new IOException("Invalid installed runtime notice");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(notice.bytes() + 1);
            if (bytes.length != notice.bytes() || !hash(bytes).equals(notice.sha256())) throw new IOException("Invalid installed runtime notice");
        }
    }
    private static String hash(byte[] bytes) { return HexFormat.of().formatHex(NativeModelFiles.digest().digest(bytes)); }
}
