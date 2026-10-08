package dev.openallay.platform.minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import net.minecraft.util.ResourceLocation;

/** Enumerates real bundled classpath files. This is not a server data-pack resource manager. */
public final class MinecraftBundledResources implements MinecraftResourceAccess.Source {
    private final ClassLoader loader;
    private final String root;
    private volatile List<ResourceLocation> ids;

    public MinecraftBundledResources(ClassLoader loader, String root) {
        this.loader = java.util.Objects.requireNonNull(loader, "loader");
        if (!root.equals("assets") && !root.equals("data")) throw new IllegalArgumentException("Invalid resource root");
        this.root = root;
    }

    @Override public List<ResourceLocation> listIds(String prefix, Predicate<ResourceLocation> filter) {
        List<ResourceLocation> published = ids;
        if (published == null) {
            synchronized (this) {
                published = ids;
                if (published == null) ids = published = discover();
            }
        }
        return dev.openallay.util.Java8Collections.toList(published.stream().filter(id -> id.getResourcePath().startsWith(prefix))
                .filter(filter));
    }

    @Override public List<MinecraftResourceAccess.TextLayer> textLayers(ResourceLocation id) throws IOException {
        List<MinecraftResourceAccess.TextLayer> layers = new ArrayList<>();
        java.util.Enumeration<java.net.URL> resources = loader.getResources(path(id));
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try (java.io.InputStream input = url.openStream()) {
                layers.add(new MinecraftResourceAccess.TextLayer("classpath:" + url.toExternalForm(),
                        new String(dev.openallay.util.Java8Streams.readAllBytes(input), StandardCharsets.UTF_8)));
            }
        }
        // ClassLoader selects the first match; native client callers select the final layer.
        java.util.Collections.reverse(layers);
        return dev.openallay.util.Java8Collections.listCopyOf(layers);
    }

    @Override public Reader openSelectedReader(ResourceLocation id) throws IOException {
        URL url = loader.getResource(path(id));
        if (url == null) throw new java.io.FileNotFoundException("Bundled resource is unavailable: " + id);
        return new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8));
    }

    private String path(ResourceLocation id) {
        return root + "/" + id.getResourceDomain() + "/" + id.getResourcePath();
    }

    private List<ResourceLocation> discover() {
        Set<String> names = new TreeSet<>();
        Set<Path> locations = new LinkedHashSet<>();
        try {
            for (ClassLoader current = loader; current != null; current = current.getParent()) {
                final class $oaPattern0_Holder { java.lang.ClassLoader value; URLClassLoader bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = current) instanceof java.net.URLClassLoader && (($oaPattern0_holder.bound = (URLClassLoader) $oaPattern0_holder.value) != null))) {
                    for (URL url : $oaPattern0_holder.bound.getURLs()) addLocation(locations, url);
                }
            }
            for (String value : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
                if (!value.isEmpty()) locations.add(java.nio.file.Paths.get(value));
            }
            URL owner = MinecraftBundledResources.class.getProtectionDomain().getCodeSource() == null ? null
                    : MinecraftBundledResources.class.getProtectionDomain().getCodeSource().getLocation();
            if (owner != null) addLocation(locations, owner);
            java.util.Enumeration<java.net.URL> roots = loader.getResources(root);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                if (url.getProtocol().equals("file")) {
                    Path resourceRoot = java.nio.file.Paths.get(url.toURI());
                    scanDirectory(resourceRoot, names);
                } else if (url.getProtocol().equals("jar")) {
                    java.net.JarURLConnection connection = (java.net.JarURLConnection) url.openConnection();
                    addLocation(locations, connection.getJarFileURL());
                }
            }
            for (Path location : locations) {
                if (Files.isDirectory(location)) scanDirectory(location.resolve(root), names);
                else if (Files.isRegularFile(location) && location.toString().endsWith(".jar")) {
                    try (ZipFile zip = new ZipFile(location.toFile())) {
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            java.util.zip.ZipEntry entry = entries.nextElement();
                            if (!entry.isDirectory() && entry.getName().startsWith(root + "/")) {
                                names.add(entry.getName().substring(root.length() + 1));
                            }
                        }
                    }
                }
            }
            List<ResourceLocation> found = new ArrayList<>();
            for (String name : names) {
                int separator = name.indexOf('/');
                if (separator <= 0) continue;
                String namespace = name.substring(0, separator);
                String value = name.substring(separator + 1);
                if (!namespace.matches("[a-z0-9_.-]+") || !value.matches("[a-z0-9_./-]+")) continue;
                ResourceLocation id = new ResourceLocation(namespace, value);
                // Only publish locations actually visible to this loader.
                if (loader.getResource(path(id)) != null) found.add(id);
            }
            return dev.openallay.util.Java8Collections.listCopyOf(found);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot enumerate bundled " + root + " resources", failure);
        } catch (java.net.URISyntaxException failure) {
            throw new IllegalStateException("Invalid bundled resource location", failure);
        }
    }

    private static void addLocation(Set<Path> locations, URL url) throws java.net.URISyntaxException {
        if (url.getProtocol().equals("file")) locations.add(java.nio.file.Paths.get(url.toURI()));
    }

    private static void scanDirectory(Path directory, Set<String> names) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> names.add(
                    directory.relativize(file).toString().replace(java.io.File.separatorChar, '/')));
        }
    }
}
