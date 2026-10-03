package io.luna.companion;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeDiagnosticsTest {
    @Test void recordsBoundedEventsWithoutCredentialFields() {
        for (int i = 0; i < 250; i++) {
            RuntimeDiagnostics.event("fixture", Map.of("count", i, "password", "private", "object", new Object()));
        }
        var events = (List<?>) RuntimeDiagnostics.events().get("events");
        assertEquals(200, events.size());
        assertFalse(events.toString().contains("private"));
        assertFalse(events.toString().contains("object="));
    }

    @Test void tracksTickBudgetAndExposesJvmHealth() {
        RuntimeDiagnostics.tick(700_000_000);
        assertEquals(700.0, RuntimeDiagnostics.ticks().get("lastDurationMs"));
        assertTrue((long) RuntimeDiagnostics.ticks().get("overBudgetCount") >= 1);
        var snapshot = RuntimeDiagnostics.snapshot();
        assertTrue(snapshot.containsKey("heap"));
        assertTrue(snapshot.containsKey("deadlockedThreadIds"));
        assertFalse(snapshot.containsKey("environment"));
        assertFalse(snapshot.containsKey("arguments"));
    }

    @Test void collectorsRejectDuplicatesAndThreadsAreBounded() {
        RuntimeDiagnostics.register("fixture-test", context -> Map.of("value", 42));
        assertEquals(42, RuntimeDiagnostics.collect("fixture-test", null).get("value"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeDiagnostics.register("fixture-test", context -> Map.of()));
        assertTrue(RuntimeDiagnostics.collect("unknown", null).containsKey("error"));
        assertTrue(((List<?>) RuntimeDiagnostics.threads().get("threads")).size() <= 128);
    }
}
