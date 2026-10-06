package dev.openallay.neoforge;

import dev.openallay.guide.GuideNotice;
import java.util.UUID;

/** Typed actor and feedback facts used by the single guide command grammar. */
public interface GuideCommandSource {
    UUID actor();
    void publish(GuideNotice notice);
}
