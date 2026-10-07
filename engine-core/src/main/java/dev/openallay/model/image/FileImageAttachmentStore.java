package dev.openallay.model.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import dev.openallay.util.Java8Hex;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Actor-scoped, content-addressed images and durable retention in one managed directory.
 * Does not accept user file paths, URLs, or exported files. Atomic manifests and a per-actor
 * file lock serialize retention and collection across store instances and processes.
 */
public final class FileImageAttachmentStore implements ImageAttachmentStore {
    private static final String OWNERS_FILE = ".owners";
    private static final String LOCK_FILE = ".lock";
    // Stripes avoid retaining an unbounded static map of every actor ever encountered.
    private static final ReentrantLock[] LOCKS = new ReentrantLock[64];
    static {
        for (int index = 0; index < LOCKS.length; index++) LOCKS[index] = new ReentrantLock();
    }

    private final Path root;
    private final ImageInputLimits limits;
    private Path realRoot;

    public FileImageAttachmentStore(Path managedDirectory) {
        this(managedDirectory, ImageInputLimits.defaults());
    }

    public FileImageAttachmentStore(Path managedDirectory, ImageInputLimits limits) {
        this.root = Objects.requireNonNull(managedDirectory, "managedDirectory")
                .toAbsolutePath().normalize();
        if (root.getParent() == null) {
            throw new IllegalArgumentException("image store must be a managed child directory");
        }
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public ImageInputLimits limits() {
        return limits;
    }

    @Override
    public ImageReference importImage(UUID actor, byte[] encodedImage) throws IOException {
        return importCaptured(actor, null, encodedImage);
    }

    @Override
    public ImageReference importImage(UUID actor, String owner, byte[] encodedImage) throws IOException {
        return importCaptured(actor, directKey(owner), encodedImage);
    }

    private ImageReference importCaptured(UUID actor, String ownerKey, byte[] encodedImage) throws IOException {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(encodedImage, "encodedImage");
        limits.checkByteSize(encodedImage.length);
        byte[] captured = encodedImage.clone();
        ImageReference reference = inspect(captured);
        return withActor(actor, directory -> {
            Map<String, Set<String>> retained = ownerKey == null ? null : readOwners(directory);
            Path target = directory.resolve(reference.sha256());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                readVerified(directory, reference);
            } else {
                replaceAtomic(directory, target, captured);
            }
            if (retained != null
                    && retained.computeIfAbsent(ownerKey, ignored -> new TreeSet<>()).add(reference.sha256())) {
                writeOwners(directory, retained);
            }
            return reference;
        });
    }

    @Override
    public byte[] read(UUID actor, ImageReference reference) throws IOException {
        Objects.requireNonNull(reference, "reference");
        limits.validate(reference);
        return withActor(actor, directory -> readVerified(directory, reference));
    }

    @Override
    public void retain(UUID actor, String owner, List<ImageReference> references) throws IOException {
        String key = directKey(owner);
        List<ImageReference> captured = dev.openallay.util.Java8Collections.listCopyOf(Objects.requireNonNull(references, "references"));
        withActor(actor, directory -> {
            Map<String, Set<String>> retained = readOwners(directory);
            Set<String> hashes = validateReferences(directory, captured);
            if (hashes.isEmpty()) retained.remove(key);
            else retained.put(key, hashes);
            writeOwners(directory, retained);
            return null;
        });
    }

    @Override
    public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
            throws IOException {
        String prefix = namespacePrefix(namespace);
        Map<String, List<ImageReference>> captured = new TreeMap<>();
        Objects.requireNonNull(owners, "owners").forEach((owner, references) ->
                captured.put(prefix + identifierHash(owner), dev.openallay.util.Java8Collections.listCopyOf(references)));
        withActor(actor, directory -> {
            Map<String, Set<String>> retained = readOwners(directory);
            Map<String, Set<String>> replacement = new TreeMap<>();
            for (Map.Entry<String, List<ImageReference>> owner : captured.entrySet()) {
                Set<String> hashes = validateReferences(directory, owner.getValue());
                if (!hashes.isEmpty()) replacement.put(owner.getKey(), hashes);
            }
            retained.keySet().removeIf(key -> key.startsWith(prefix));
            retained.putAll(replacement);
            writeOwners(directory, retained);
            return null;
        });
    }

    @Override
    public void release(UUID actor, String owner) throws IOException {
        String key = directKey(owner);
        withActor(actor, directory -> {
            Map<String, Set<String>> retained = readOwners(directory);
            if (retained.remove(key) != null) writeOwners(directory, retained);
            return null;
        });
    }

    @Override
    public int collect(UUID actor) throws IOException {
        return withActor(actor, directory -> {
            Set<String> retained = new TreeSet<>();
            readOwners(directory).values().forEach(retained::addAll);
            List<Path> unretained = new ArrayList<>();
            try (java.util.stream.Stream<Path> entries = Files.list(directory)) {
                for (Path entry : dev.openallay.util.Java8Collections.toList(entries)) {
                    String name = entry.getFileName().toString();
                    if (!isHash(name)) continue; // Never traverse or remove export/foreign files.
                    requireRegularFile(entry);
                    if (!retained.contains(name)) unretained.add(entry);
                }
            }
            int removed = 0;
            for (Path path : unretained) {
                requireActorDirectory(directory);
                requireRegularFile(path);
                if (Files.deleteIfExists(path)) removed++;
            }
            return removed;
        });
    }

    private Set<String> validateReferences(Path directory, List<ImageReference> references)
            throws IOException {
        Set<String> hashes = new TreeSet<>();
        for (ImageReference reference : references) {
            limits.validate(reference);
            // Validate every metadata claim, including two unequal references to the same hash.
            readVerified(directory, reference);
            hashes.add(reference.sha256());
        }
        return hashes;
    }

    private byte[] readVerified(Path directory, ImageReference reference) throws IOException {
        limits.validate(reference);
        requireActorDirectory(directory);
        Path file = directory.resolve(reference.sha256());
        requireRegularFile(file);
        byte[] bytes;
        try (FileChannel channel = FileChannel.open(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            limits.checkByteSize(size);
            if (size != reference.byteSize()) throw new IOException("managed image byte size mismatch");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new IOException("managed image was truncated");
            }
            if (channel.read(ByteBuffer.allocate(1)) >= 0) {
                throw new IOException("managed image grew during read");
            }
            bytes = buffer.array();
        }
        if (!digest(bytes).equals(reference.sha256())) {
            throw new IOException("managed image content hash mismatch");
        }
        if (!inspect(bytes).equals(reference)) {
            throw new IOException("managed image metadata mismatch");
        }
        return bytes;
    }

    private ImageReference inspect(byte[] bytes) throws IOException {
        limits.checkByteSize(bytes.length);
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("image is not a decodable PNG or JPEG");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String mimeType;
                switch (format) {
                    case "png": mimeType = "image/png"; break;
                    case "jpeg": case "jpg": mimeType = "image/jpeg"; break;
                    default: throw new IOException("only PNG and JPEG image inputs are supported");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Header dimensions are checked BEFORE allocating the decoded raster.
                limits.checkDimensions(width, height);
                boolean[] warned = {false};
                reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
                BufferedImage decoded = reader.read(0);
                try {
                    if (decoded == null || warned[0] || decoded.getWidth() != width
                            || decoded.getHeight() != height) {
                        throw new IOException("image could not be completely decoded");
                    }
                } finally {
                    if (decoded != null) decoded.flush();
                }
                return new ImageReference(digest(bytes), mimeType, width, height, bytes.length);
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException malformed) {
            throw new IOException("image could not be decoded", malformed);
        }
    }

    private Map<String, Set<String>> readOwners(Path directory) throws IOException {
        Path file = directory.resolve(OWNERS_FILE);
        Map<String, Set<String>> owners = new TreeMap<>();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return owners;
        requireRegularFile(file);
        try (FileChannel channel = FileChannel.open(
                    file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                BufferedReader reader = new BufferedReader(
                    Channels.newReader(channel, StandardCharsets.US_ASCII.newDecoder(), -1))) {
            StringBuilder line = new StringBuilder(196);
            int next;
            while ((next = reader.read()) != -1) {
                if (next == '\n') {
                    addOwnerLine(owners, line.toString());
                    line.setLength(0);
                } else {
                    if (line.length() >= 196) throw new IOException("invalid managed image owner manifest");
                    line.append((char) next);
                }
            }
            if (line.length() != 0) throw new IOException("incomplete managed image owner manifest");
        }
        return owners;
    }

    private static void addOwnerLine(Map<String, Set<String>> owners, String line) throws IOException {
        String[] parts = line.split("\t", -1);
        String key;
        String imageHash;
        if (parts.length == 3 && parts[0].equals("d") && isHash(parts[1]) && isHash(parts[2])) {
            key = "d\t" + parts[1];
            imageHash = parts[2];
        } else if (parts.length == 4 && parts[0].equals("n") && isHash(parts[1])
                && isHash(parts[2]) && isHash(parts[3])) {
            key = "n\t" + parts[1] + "\t" + parts[2];
            imageHash = parts[3];
        } else {
            throw new IOException("invalid managed image owner manifest");
        }
        if (!owners.computeIfAbsent(key, ignored -> new TreeSet<>()).add(imageHash)) {
            throw new IOException("duplicate managed image owner manifest entry");
        }
    }

    private void writeOwners(Path directory, Map<String, Set<String>> owners) throws IOException {
        StringBuilder contents = new StringBuilder();
        owners.forEach((owner, hashes) -> hashes.forEach(hash ->
                contents.append(owner).append('\t').append(hash).append('\n')));
        replaceAtomic(directory, directory.resolve(OWNERS_FILE),
                contents.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private void replaceAtomic(Path directory, Path target, byte[] bytes) throws IOException {
        requireActorDirectory(directory);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) requireRegularFile(target);
        Path temporary = Files.createTempFile(directory, ".artifact-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            requireActorDirectory(directory);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) requireRegularFile(target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    private interface ActorOperation<T> {
        T run(Path directory) throws IOException;
    }

    private <T> T withActor(UUID actor, ActorOperation<T> operation) throws IOException {
        Objects.requireNonNull(actor, "actor");
        Path directory = prepareActor(actor);
        ReentrantLock lock = LOCKS[Math.floorMod(directory.hashCode(), LOCKS.length)];
        lock.lock();
        try {
            requireActorDirectory(directory);
            Path lockFile = directory.resolve(LOCK_FILE);
            if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) requireRegularFile(lockFile);
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    FileLock ignored = channel.lock()) {
                requireActorDirectory(directory);
                return operation.run(directory);
            }
        } finally {
            lock.unlock();
        }
    }

    private synchronized Path prepareActor(UUID actor) throws IOException {
        // Existing ancestors may include platform aliases (/tmp on macOS). Pin their resolved
        // managed root once, reject symlinks inside that root, and never accept a caller path.
        if (realRoot == null) {
            rejectSymlink(root);
            Files.createDirectories(root);
            rejectSymlink(root);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("managed image root is not a directory");
            }
            realRoot = root.toRealPath();
        }
        requireRoot();
        Path directory = realRoot.resolve(actor.toString());
        rejectSymlink(directory);
        try {
            Files.createDirectory(directory);
        } catch (java.nio.file.FileAlreadyExistsException exists) {
            // Reopening an existing actor directory is expected.
        }
        requireActorDirectory(directory);
        return directory;
    }

    private void requireRoot() throws IOException {
        rejectSymlink(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(realRoot)) {
            throw new IOException("managed image root changed or is unavailable");
        }
    }

    private void requireActorDirectory(Path directory) throws IOException {
        requireRoot();
        rejectSymlink(directory);
        if (!directory.getParent().equals(realRoot)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !directory.toRealPath().equals(directory)) {
            throw new IOException("managed actor image directory changed or escaped its root");
        }
    }

    private static void requireRegularFile(Path file) throws IOException {
        rejectSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(file.toString());
            throw new IOException("managed image entry is not a regular file");
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("managed image entry cannot be a symbolic link");
    }

    private static String directKey(String owner) {
        return "d\t" + identifierHash(owner);
    }

    private static String namespacePrefix(String namespace) {
        return "n\t" + identifierHash(namespace) + "\t";
    }

    private static String identifierHash(String identifier) {
        if (identifier == null || dev.openallay.util.Java8Strings.isBlank(identifier)) {
            throw new IllegalArgumentException("image retention identifier cannot be blank");
        }
        return digest(identifier.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isHash(String value) {
        return value.matches("[0-9a-f]{64}");
    }

    private static String digest(byte[] bytes) {
        try {
            return Java8Hex.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
