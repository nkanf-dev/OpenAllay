package dev.openallay.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Objects;

/** Strict text file operations; the caller retains path, open-option and publication ownership. */
public final class Java8Files {
    private Java8Files() {}
    public static String readString(Path path) throws IOException { return readString(path, StandardCharsets.UTF_8); }
    public static String readString(Path path, Charset charset) throws IOException {
        Objects.requireNonNull(path); Objects.requireNonNull(charset);
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
    }
    public static Path writeString(Path path, CharSequence text, OpenOption... options) throws IOException {
        return writeString(path, text, StandardCharsets.UTF_8, options);
    }
    public static Path writeString(Path path, CharSequence text, Charset charset, OpenOption... options) throws IOException {
        Objects.requireNonNull(path); Objects.requireNonNull(text); Objects.requireNonNull(charset);
        Objects.requireNonNull(options);
        ByteBuffer encoded;
        try {
            encoded = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        } catch (java.nio.charset.MalformedInputException malformed) {
            // The original public Files API classifies lone surrogates differently only
            // for its builtin UTF8 encoder. Custom charset instances retain their own errors.
            if (charset == StandardCharsets.UTF_8) {
                throw new java.nio.charset.UnmappableCharacterException(malformed.getInputLength());
            }
            throw malformed;
        }
        byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes);
        return Files.write(path, bytes, options);
    }
}
