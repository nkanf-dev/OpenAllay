package dev.openallay.settings.requirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.tool.ToolResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RefreshingPreparedPackageInstallTest {
    @Test
    void publishedPackageIsNotReportedAsInstallFailureWhenViewRefreshFails() {
        AtomicInteger publications = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        PreparedPackageInstall candidate = new PreparedPackageInstall() {
            private boolean consumed;
            @Override public RequirementKind kind() { return RequirementKind.SKILL; }
            @Override public String id() { return "demo"; }
            @Override public String version() { return "1.0.0"; }
            @Override public String sha256() { return "a".repeat(64); }
            @Override public RequirementSet requirements() { return RequirementSet.EMPTY; }
            @Override public ToolResult<Boolean> commit() {
                if (consumed) {
                    return new ToolResult.Failure<>("prepared_install_consumed", "Consumed");
                }
                consumed = true;
                publications.incrementAndGet();
                return new ToolResult.Success<>(true);
            }
            @Override public void close() { consumed = true; }
        };
        PreparedPackageInstall wrapped = new RefreshingPreparedPackageInstall(candidate, this, () -> {
            refreshes.incrementAndGet();
            throw new IllegalStateException("projection unavailable");
        });

        ToolResult.Failure<?> failure = assertInstanceOf(ToolResult.Failure.class, wrapped.commit());
        assertEquals("package_projection_failed", failure.code());
        assertEquals("Package published, but settings view could not be refreshed", failure.message());
        assertEquals(1, publications.get());
        assertEquals(1, refreshes.get());
        assertEquals("prepared_install_consumed",
                assertInstanceOf(ToolResult.Failure.class, wrapped.commit()).code());
        assertEquals(1, publications.get());
        assertEquals(1, refreshes.get());
        wrapped.close();
    }
}
