import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import dev.openallay.guide.semantic.SemanticDocument;
import dev.openallay.guide.semantic.SemanticMessageParser;

/** Invokes the actual canonical engine class, compiled from its existing source. */
public final class SemanticParserGolden {
    private static String encoded(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        SemanticMessageParser parser = new SemanticMessageParser();
        int index = 0;
        for (String vector : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String source = new String(Base64.getDecoder().decode(vector), StandardCharsets.UTF_8);
            SemanticDocument document = parser.parse(source);
            System.out.println("VECTOR " + index++);
            System.out.println("AST " + encoded(document.blocks().toString()));
            System.out.println("FALLBACK " + encoded(document.fallbackText()));
            System.out.println("DIAGNOSTICS " + encoded(document.diagnostics().toString()));
            if (!document.equals(parser.parse(source))) throw new AssertionError("unstable semantic parse");
        }
        if (!parser.parse((String) null).equals(parser.parse(""))) throw new AssertionError("null input differs");
    }
}
