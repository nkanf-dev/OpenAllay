package dev.openallay.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Focused same-real-classpath smoke: strings/members/declarations stay unchanged; absent types fail. */
public final class CuratedNamespaceAcceptanceMain {
    private static String hash(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("classpath.txt acceptance.properties fresh-smoke-root");
        Path root=Path.of(args[2]).toAbsolutePath();
        if (Files.exists(root)) throw new IllegalStateException("Fresh smoke root required");
        Files.createDirectories(root);
        Path map=root.resolve("classes.tsv");
        Files.writeString(map,"net.minecraft.world.item.ItemStack\tnet.minecraft.item.ItemStack\tnative item stack\n");
        Path good=root.resolve("Probe.java");
        String source="package probe; import net.minecraft.world.item.ItemStack; class Probe { ItemStack item; String literal = \"net.minecraft.world.item.ItemStack\"; void member() { item.getCount(); } }";
        Files.writeString(good,source);
        List<Path> cp=Files.readAllLines(Path.of(args[0])).stream().map(Path::of).toList();
        MinecraftClassNamespaceProducer.produce(new MinecraftClassNamespaceProducer.Request(
            List.of(new MinecraftClassNamespaceProducer.Unit("smoke","probe/Probe.java",good,MinecraftClassNamespaceProducer.InputMode.CANONICAL_SEMANTIC)),
            List.of(),null,null,null,cp,"17",false,Path.of(args[1]),List.of(map,good),
            root.resolve("generated"),root.resolve("receipt.json"),new MinecraftClassNamespaceProducer.MappingInput(map,hash(map))));
        String generated=Files.readString(root.resolve("generated/probe/Probe.java"));
        if (!generated.contains("import net.minecraft.item.ItemStack;") || !generated.contains("item.getCount()")
                || !generated.contains("\"net.minecraft.world.item.ItemStack\"")) throw new IllegalStateException("True-token-only transformation failed");
        Path bad=root.resolve("Absent.java");
        Files.writeString(bad,"package probe; import net.minecraft.this_type_must_not_exist.NoAlias; class Absent { NoAlias value; }");
        boolean rejected=false;
        try {
            MinecraftClassNamespaceProducer.produce(new MinecraftClassNamespaceProducer.Request(
                List.of(new MinecraftClassNamespaceProducer.Unit("smoke","probe/Absent.java",bad,MinecraftClassNamespaceProducer.InputMode.CANONICAL_SEMANTIC)),
                List.of(),null,null,null,cp,"17",false,Path.of(args[1]),List.of(map,bad),
                root.resolve("rejected"),root.resolve("rejected-receipt.json"),new MinecraftClassNamespaceProducer.MappingInput(map,hash(map))));
        } catch (IllegalStateException expected) {
            rejected=expected.getMessage().contains("No exact class join") || expected.getMessage().contains("Unresolved");
        }
        if (!rejected || Files.exists(root.resolve("rejected-receipt.json")) || Files.exists(root.resolve("rejected")))
            throw new IllegalStateException("Unsupported canonical native class did not fail closed");
    }
}
