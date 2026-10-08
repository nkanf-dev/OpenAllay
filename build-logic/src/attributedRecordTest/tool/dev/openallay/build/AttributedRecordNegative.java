package dev.openallay.build;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
public final class AttributedRecordNegative {
    public static void main(String[] args)throws Exception {
        String source="record Value(String value) {}";
        try { RecordValueSourceConverter.convert(Paths.get("Value.java").toAbsolutePath(),source,Set.of("Value"),new RecordValueSourceConverter.AttributedProof("bad",Map.of(),Map.of(),Map.of())); }
        catch(IllegalArgumentException expected) { System.out.println("PASS attributed proof raw preimage rejection");return; }
        throw new AssertionError("Wrong attributed proof accepted");
    }
}
