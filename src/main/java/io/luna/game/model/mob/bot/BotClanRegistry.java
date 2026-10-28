package io.luna.game.model.mob.bot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType;
import io.luna.game.model.mob.bot.brain.BotPkPreference;
import io.luna.game.persistence.PlayerData;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Durable clan membership independent of PK purpose, including offline accounts in capacity decisions.
 * Startup reconciles the full saved roster; new members join a smallest clan without moving existing members.
 * Mutations run on the persistence worker and publish only after an atomic file replacement succeeds.
 *
 * @author lare96
 */
public final class BotClanRegistry {

    public static final int CLAN_COUNT = 50;
    public static final int MAX_MEMBERS = 100;

    private final Path file;
    private volatile Map<String, Integer> members = Map.of();
    private boolean loaded;

    /**
     * Creates a registry at the specified external membership file.
     */
    public BotClanRegistry(Path file) {
        this.file = file;
    }

    /**
     * Loads and validates the registry, removes deleted/ineligible accounts, and enrolls all eligible saved bots.
     * Old per-account cape values are ignored: the external roster is authoritative.
     */
    public synchronized void load(Collection<String> savedNames, Function<String, PlayerData> loader) {
        Map<String, Integer> restored = new HashMap<>();
        try {
            if (Files.exists(file)) {
                JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (root.get("version").getAsBigDecimal().intValueExact() != 1) throw new IllegalStateException("Unknown clan registry version.");
                root.getAsJsonObject("members").entrySet().forEach(entry -> {
                    String name = normalize(entry.getKey());
                    int cape = entry.getValue().getAsBigDecimal().intValueExact();
                    if (!BotPkPreference.isTeamCape(cape) || restored.putIfAbsent(name, cape) != null) {
                        throw new IllegalStateException("Invalid clan assignment for " + name);
                    }
                });
            }
            TreeSet<String> eligible = new TreeSet<>();
            for (String name : savedNames) {
                PlayerData data = loader.apply(name);
                if (data == null) throw new IllegalStateException("Missing saved bot: " + name);
                if (isEligible(data)) eligible.add(normalize(name));
            }
            restored.keySet().retainAll(eligible);
            validateCapacity(restored);
            for (String name : eligible) {
                if (!restored.containsKey(name)) restored.put(name, smallestClan(restored));
            }
            publish(restored);
            loaded = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load bot clan registry.", e);
        }
    }

    /**
     * Mirrors saved PK eligibility without constructing offline bots or changing their permanent build.
     * Missing legacy identities follow the same personality/activity rules as preference migration.
     */
    static boolean isEligible(PlayerData data) {
        if (data.personality == null || data.preferences == null) {
            throw new IllegalStateException("Missing saved bot personality/preferences.");
        }
        if (data.personality.getType() == PersonalityTemplateType.SKILLER) return false;
        JsonObject preferences = data.preferences;
        if (preferences.has("pk")) return !preferences.get("pk").isJsonNull();
        if (data.personality.getType() == PersonalityTemplateType.PKER) return true;
        return preferences.getAsJsonArray("activities").asList().stream().map(it -> it.getAsJsonObject())
                .anyMatch(it -> it.get("type").getAsString().equals("PKING") && it.get("weight").getAsDouble() > 0.60);
    }

    /**
     * Updates one account before login becomes visible. Call on the persistence worker, never the game thread.
     */
    public synchronized void update(String username, boolean eligible) {
        if (!loaded) throw new IllegalStateException("Bot clan registry has not loaded.");
        String name = normalize(username);
        if (members.containsKey(name) == eligible) return;
        Map<String, Integer> next = new HashMap<>(members);
        if (eligible) next.put(name, smallestClan(next));
        else next.remove(name);
        try {
            publish(next);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save bot clan registry.", e);
        }
    }

    /**
     * Returns an assigned unnoted cape, or null for an account without membership. This never performs disk I/O.
     */
    public Integer capeFor(String username) {
        return members.get(normalize(username));
    }

    /**
     * Selects a smallest clan, breaking ties by cape number. Full rosters reject enrollment visibly.
     */
    private static int smallestClan(Map<String, Integer> roster) {
        int[] counts = counts(roster);
        int smallest = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] < counts[smallest]) smallest = i;
        if (counts[smallest] >= MAX_MEMBERS) throw new IllegalStateException("All 50 bot clans are full (5,000 members).");
        return 4315 + smallest * 2;
    }

    /**
     * Counts all registered members, including accounts that are currently offline.
     */
    private static int[] counts(Map<String, Integer> roster) {
        int[] counts = new int[CLAN_COUNT];
        roster.values().forEach(cape -> counts[(cape - 4315) / 2]++);
        return counts;
    }

    /**
     * Rejects over-capacity external rosters rather than silently changing allegiances.
     */
    private static void validateCapacity(Map<String, Integer> roster) {
        for (int count : counts(roster)) {
            if (count > MAX_MEMBERS) throw new IllegalStateException("A bot clan exceeds 100 members.");
        }
    }

    /**
     * Replaces the durable roster before exposing its immutable snapshot to game-thread readers.
     */
    private void publish(Map<String, Integer> roster) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject entries = new JsonObject();
        new TreeMap<>(roster).forEach(entries::addProperty);
        root.add("members", entries);
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "clans-", ".tmp");
        try {
            Files.writeString(temp, root.toString());
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        members = Map.copyOf(roster);
    }

    /**
     * Matches the account index by folding capitalization while preserving distinct account spellings.
     */
    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
