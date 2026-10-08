package dev.openallay.model.metadata;

import org.junit.jupiter.api.Test;
public final class ModelCatalogJava8Test {
    @Test void strictActualCatalog() { ModelCatalogJava8Fixture.catalog(); }
    @Test void actualMatcher() { ModelCatalogJava8Fixture.matcher(); }
    @Test void precedenceAndUnknownEvidence() { ModelCatalogJava8Fixture.precedence(); }
    @Test void actualResolverAndLoopbackTransport() throws Exception { ModelCatalogJava8Fixture.resolver(); }
    @Test void eventValuesAndCancellation() throws Exception { ModelCatalogJava8Fixture.eventsAndCancellation(); }
}
