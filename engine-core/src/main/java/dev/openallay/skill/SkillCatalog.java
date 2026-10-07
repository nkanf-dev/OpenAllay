package dev.openallay.skill;

import java.util.List;
import java.util.Optional;

/** Read-only Skill view used by prompts and the load_skill Tool. */
public interface SkillCatalog {
    Optional<SkillDocument> find(String name);

    List<SkillMetadata> metadata();

    default String metadataPrompt() {
        StringBuilder prompt = new StringBuilder();
        for (SkillMetadata metadata : metadata()) {
            prompt.append("  <skill>\n")
                    .append("    <name>").append(SkillCatalogText.xml(metadata.name())).append("</name>\n")
                    .append("    <description>").append(SkillCatalogText.xml(metadata.description()))
                    .append("</description>\n")
                    .append("  </skill>\n");
        }
        return dev.openallay.util.Java8Strings.stripTrailing(prompt.toString());
    }

}

/** Package-private helper keeps the catalog interface Java8 without new public methods. */
final class SkillCatalogText {
    private SkillCatalogText() {}
    static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
