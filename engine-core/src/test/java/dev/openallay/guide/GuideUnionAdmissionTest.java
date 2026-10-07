package dev.openallay.guide;
import org.junit.jupiter.api.Test;
final class GuideUnionAdmissionTest {
    @Test void rejectsUnknownAtEveryActualPublishedCapture(){GuideUnionAdmissionFixture.main(new String[0]);}
    @Test void knownValuesKeepCanonicalSemantics(){GuideUnionKnownFixture.main(new String[0]);}
}
