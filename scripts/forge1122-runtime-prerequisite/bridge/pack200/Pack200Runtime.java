package dev.openallay.runtime.forge1122.pack200;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/** Consumes genuine JDK8-unpacked Forge entries; no Pack200 emulation or fake java classes. */
public final class Pack200Runtime {
    private static final String PACKED_SHA = "637960a65a320b359561f86016c467e6cfd6d31c0507c1f76effb46e85ea4db6";
    public static void unpack(InputStream packed, JarOutputStream destination) throws Exception {
        if (!PACKED_SHA.equals(sha(read(packed, 16 * 1024 * 1024))))
            throw new IllegalStateException("Forge's real decompressed Pack200 input differs");
        Path jar = Paths.get(System.getProperty("openallay.pack200.jar"));
        String expected = System.getProperty("openallay.pack200.jarSha256");
        byte[] bytes = Files.readAllBytes(jar);
        if (expected == null || !expected.equals(sha(bytes)))
            throw new IllegalStateException("JDK8-unpacked artifact differs");
        int entries = 0;
        try (JarInputStream source = new JarInputStream(new java.io.ByteArrayInputStream(bytes))) {
            JarEntry entry;
            while ((entry = source.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith("binpatch/") || (!entry.isDirectory() && !name.endsWith(".binpatch")) || name.contains("..") || name.contains("\\"))
                    throw new IllegalStateException("Unexpected genuine binpatch entry: " + name);
                byte[] content = read(source, 4 * 1024 * 1024);
                destination.putNextEntry(new JarEntry(name));
                destination.write(content);
                destination.closeEntry();
                entries++;
            }
        }
        if (entries == 0) throw new IllegalStateException("Genuine binary patches cannot be empty");
        String receipt = "{\"genuineJdk8Pack200\":true,\"packedSha256\":\"" + PACKED_SHA
                + "\",\"unpackedJarSha256\":\"" + expected + "\",\"copiedEntries\":" + entries
                + ",\"runtimeJava\":17,\"helperLoader\":\"" + Pack200Runtime.class.getClassLoader().getClass().getName() + "\"}\n";
        Files.write(Paths.get(System.getProperty("openallay.pack200.runtimeReceipt")), receipt.getBytes(StandardCharsets.UTF_8));
        System.err.println("OPENALLAY_PACK200_GENUINE_ENTRIES " + receipt.trim());
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("openallay.pack200.jar", args[1]);
        System.setProperty("openallay.pack200.jarSha256", args[2]);
        System.setProperty("openallay.pack200.runtimeReceipt", args[3]);
        byte[] packed = Files.readAllBytes(Paths.get(args[0]));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream output = new JarOutputStream(bytes)) {
            unpack(new java.io.ByteArrayInputStream(packed), output);
        }
        if (!entries(Files.readAllBytes(Paths.get(args[1]))).equals(entries(bytes.toByteArray())))
            throw new AssertionError("Genuine unpacked entry bytes must remain identical");
        packed[0] ^= 1;
        try {
            unpack(new java.io.ByteArrayInputStream(packed), new JarOutputStream(new ByteArrayOutputStream()));
            throw new AssertionError("Changed packed input must reject");
        } catch (IllegalStateException expected) { }
        System.out.println("PASS genuine JDK8 entries copied identically on Java17; changed packed input rejects");
    }
    private static java.util.Map<String,String> entries(byte[] bytes) throws Exception {
        java.util.Map<String,String> entries = new java.util.TreeMap<String,String>();
        try (JarInputStream input = new JarInputStream(new java.io.ByteArrayInputStream(bytes))) {
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null)
                entries.put(entry.getName(), sha(read(input, 4 * 1024 * 1024)));
        }
        return entries;
    }
    private static byte[] read(InputStream input, int maximum) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > maximum) throw new IllegalStateException("Bounded input exceeded");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
    private static String sha(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }
}
