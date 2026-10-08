package dev.openallay.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Dependency-free build-tool CLI. Paths and source ownership are explicit plan inputs. */
public final class MinecraftClassNamespaceProducerMain {
    private MinecraftClassNamespaceProducerMain() {}
    public static void main(String[] args) throws Exception {
        Map<String, String> flags = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !args[i].startsWith("--") || flags.putIfAbsent(args[i], args[i + 1]) != null) throw new IllegalArgumentException("Expected unique --flag value pairs");
        }
        Set<String> expected = Set.of("--units", "--symbol-units", "--client", "--client-sha256", "--server", "--server-sha256", "--tsrg", "--tsrg-sha256", "--classpath", "--source", "--preview", "--metadata-acceptance", "--output", "--receipt");
        Set<String> curatedExpected = Set.of("--units", "--symbol-units", "--curated-classes", "--curated-classes-sha256",
                "--classpath", "--source", "--preview", "--metadata-acceptance", "--output", "--receipt");
        boolean curated = flags.containsKey("--curated-classes");
        if (!flags.keySet().equals(curated ? curatedExpected : expected))
            throw new IllegalArgumentException("Exact required flags: " + (curated ? curatedExpected : expected));
        Path acceptance = Path.of(flags.get("--metadata-acceptance"));
        List<Path> declaredInputs = new ArrayList<>();
        List<String> inputFlags = new ArrayList<>(List.of("--units", "--symbol-units", "--classpath", "--metadata-acceptance"));
        inputFlags.addAll(curated ? List.of("--curated-classes") : List.of("--client", "--server", "--tsrg"));
        for (String flag : inputFlags) declaredInputs.add(Path.of(flags.get(flag)));
        // A conservative path-only preflight runs before interpreting/validating plans.
        // Existing absolute cells are protected even when the row later fails shape validation.
        declaredInputs.addAll(MinecraftClassNamespaceProducer.planInputPaths(List.of(
                Path.of(flags.get("--units")), Path.of(flags.get("--symbol-units")), Path.of(flags.get("--classpath")))));
        Path output = Path.of(flags.get("--output")), receipt = Path.of(flags.get("--receipt"));
        MinecraftClassNamespaceProducer.preflightPaths(output, receipt, declaredInputs);
        Files.deleteIfExists(receipt);
        List<Path> classpath = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(flags.get("--classpath")), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || !Path.of(line).isAbsolute()) throw new IllegalArgumentException("Classpath plan requires one absolute artifact path per line");
            classpath.add(Path.of(line));
        }
        String preview = flags.get("--preview");
        if (!preview.equals("true") && !preview.equals("false")) throw new IllegalArgumentException("Explicit preview true/false required");
        MinecraftClassNamespaceProducer.produce(new MinecraftClassNamespaceProducer.Request(
                units(Path.of(flags.get("--units"))), units(Path.of(flags.get("--symbol-units"))),
                curated ? null : new MinecraftClassNamespaceProducer.MappingInput(Path.of(flags.get("--client")), flags.get("--client-sha256")),
                curated ? null : new MinecraftClassNamespaceProducer.MappingInput(Path.of(flags.get("--server")), flags.get("--server-sha256")),
                curated ? null : new MinecraftClassNamespaceProducer.MappingInput(Path.of(flags.get("--tsrg")), flags.get("--tsrg-sha256")),
                classpath, flags.get("--source"), Boolean.parseBoolean(preview), acceptance, declaredInputs,
                output, receipt, curated ? new MinecraftClassNamespaceProducer.MappingInput(
                        Path.of(flags.get("--curated-classes")), flags.get("--curated-classes-sha256")) : null));
    }
    private static List<MinecraftClassNamespaceProducer.Unit> units(Path plan) throws Exception {
        List<MinecraftClassNamespaceProducer.Unit> units = new ArrayList<>();
        for (String line : Files.readAllLines(plan, StandardCharsets.UTF_8)) {
            if (line.isEmpty()) throw new IllegalArgumentException("Empty unit-plan row");
            String[] cells = line.split("\t", -1);
            if (cells.length != 4 || !Path.of(cells[3]).isAbsolute()) throw new IllegalArgumentException("Expected owner TAB logicalPath TAB mode TAB absoluteFile");
            units.add(new MinecraftClassNamespaceProducer.Unit(cells[0], cells[1], Path.of(cells[3]), MinecraftClassNamespaceProducer.InputMode.valueOf(cells[2])));
        }
        return units;
    }
}
