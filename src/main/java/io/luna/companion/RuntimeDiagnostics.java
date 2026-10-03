package io.luna.companion;

import io.luna.LunaContext;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Explicit, bounded runtime DTOs. Never serialize environment, arguments or domain objects. */
public final class RuntimeDiagnostics {
    private static final Map<String, Function<LunaContext, Map<String, Object>>> COLLECTORS = new ConcurrentHashMap<>();
    private static final ArrayDeque<Map<String, Object>> EVENTS = new ArrayDeque<>();
    private static long tickCount;
    private static long lastTickNanos;
    private static long maxTickNanos;
    private static long slowTicks;
    private static long eventSequence;

    static {
        register("services", context -> Map.of(
                "game", context.getGame().state().name(),
                "login", context.getWorld().getLoginService().state().name(),
                "logout", context.getWorld().getLogoutService().state().name(),
                "tick", context.getWorld().getCurrentTick()));
    }

    private RuntimeDiagnostics() { }

    /** Collectors execute on the game thread. Return small immutable DTOs, never live game objects. */
    public static void register(String name, Function<LunaContext, Map<String, Object>> collector) {
        if (!name.matches("[a-z][a-z0-9_-]{0,47}") || COLLECTORS.putIfAbsent(name, Objects.requireNonNull(collector)) != null) {
            throw new IllegalArgumentException("Invalid or duplicate collector.");
        }
    }

    public static Map<String, Object> collectors() {
        return Map.of("collectors", COLLECTORS.keySet().stream().sorted().toList(), "schemaVersion", 1);
    }

    public static Map<String, Object> collect(String name, LunaContext context) {
        var collector = COLLECTORS.get(name);
        return collector == null ? Map.of("error", "Unknown collector.") : collector.apply(context);
    }

    public static synchronized void tick(long nanos) {
        tickCount++;
        lastTickNanos = nanos;
        maxTickNanos = Math.max(maxTickNanos, nanos);
        if (nanos > 600_000_000L) {
            slowTicks++;
            event("slow_tick", Map.of("durationMs", nanos / 1_000_000.0));
        }
    }

    /** Instrumentation extension point: only explicitly selected non-secret scalar fields. */
    public static synchronized void event(String type, Map<String, ?> fields) {
        if (!type.matches("[a-z][a-z0-9_]{0,47}") || fields.size() > 16) return;
        var safe = new LinkedHashMap<String, Object>();
        for (var entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,47}") || key.toLowerCase(Locale.ROOT).matches(".*(password|token|secret|credential).*")) continue;
            if (value instanceof Number || value instanceof Boolean) safe.put(key, value);
            else if (value instanceof String text) safe.put(key, text.substring(0, Math.min(256, text.length())));
        }
        if (EVENTS.size() == 200) EVENTS.removeFirst();
        EVENTS.addLast(Map.of("sequence", ++eventSequence, "timeMillis", System.currentTimeMillis(), "type", type, "fields", safe));
    }

    public static synchronized Map<String, Object> events() {
        return Map.of("events", List.copyOf(EVENTS), "lastSequence", eventSequence, "capacity", 200);
    }

    public static synchronized Map<String, Object> ticks() {
        return Map.of("count", tickCount, "lastDurationMs", lastTickNanos / 1_000_000.0,
                "maxDurationMs", maxTickNanos / 1_000_000.0, "overBudgetCount", slowTicks, "budgetMs", 600);
    }

    /** Runs outside the game thread, so a stuck tick does not hide JVM diagnostics. */
    public static Map<String, Object> snapshot() {
        var memory = ManagementFactory.getMemoryMXBean();
        var heap = memory.getHeapMemoryUsage();
        var threads = ManagementFactory.getThreadMXBean();
        var gc = ManagementFactory.getGarbageCollectorMXBeans().stream().map(bean -> Map.of(
                "name", bean.getName(), "count", bean.getCollectionCount(), "timeMs", bean.getCollectionTime())).toList();
        long[] deadlocks = threads.findDeadlockedThreads();
        return Map.of("schemaVersion", 1, "pid", ProcessHandle.current().pid(),
                "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime(),
                "heap", Map.of("used", heap.getUsed(), "committed", heap.getCommitted(), "max", heap.getMax()),
                "nonHeapUsed", memory.getNonHeapMemoryUsage().getUsed(), "gc", gc,
                "threadCount", threads.getThreadCount(), "deadlockedThreadIds", deadlocks == null ? new long[0] : deadlocks,
                "ticks", ticks());
    }

    public static Map<String, Object> threads() {
        var bean = ManagementFactory.getThreadMXBean();
        long[] all = bean.getAllThreadIds();
        long[] selected = Arrays.copyOf(all, Math.min(all.length, 128));
        var result = new ArrayList<Map<String, Object>>();
        for (var info : bean.getThreadInfo(selected, 24)) {
            if (info == null) continue;
            result.add(Map.of("id", info.getThreadId(), "state", info.getThreadState().name(),
                    "blockedCount", info.getBlockedCount(), "waitedCount", info.getWaitedCount(),
                    "stack", Arrays.stream(info.getStackTrace()).map(Object::toString).toList()));
        }
        return Map.of("threads", result, "total", all.length, "truncated", all.length > selected.length);
    }
}
