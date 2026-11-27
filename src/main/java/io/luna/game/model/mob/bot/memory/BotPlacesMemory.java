package io.luna.game.model.mob.bot.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The places a bot knows: where it has worked, where others took what it came for and where it nearly or actually
 * died.
 * <p>
 * Every place is held under its zone name. Familiarity grows with time spent working there. Danger grows with each
 * death or escape there. Crowding is held for each {@link Resource} and is set when the bot gives up on a place
 * because others had taken the rocks, trees or stalls it came for. All of them fade by half over a set stretch of
 * time, and a place with none left is forgotten.
 * <p>
 * Only resources that run out can be crowded. Work that any number of players share at once, such as fishing,
 * cooking or smithing, never is, and a mine full of miners says nothing about the fishing spots or ranges in the same
 * zone.
 * <p>
 * Familiarity and danger fade by the bot's time online, like everything else it remembers. Crowding is about the
 * world, not the bot, so it fades by real time: a mine packed an hour ago may have room now whether or not the bot
 * played in between.
 * <p>
 * A place the bot finds {@link #dangerous(String) dangerous} is one it stays away from. Two deaths close together
 * are enough, and three keep it away for days of play. A place whose resource it finds
 * {@link #packed(String, Resource) packed} is skipped for that resource for about half an hour while it has somewhere
 * else to go. Below those lines a place only ranks lower, and familiar places rank higher, by
 * {@link #preference(String, Resource)}.
 * <p>
 * The number of places is limited by how intelligent the bot is. When the limit is reached, the place the bot
 * remembers least makes room.
 *
 * @author TheLining
 */
public final class BotPlacesMemory implements BotMemoryPart {

    /**
     * Something that happened to the bot at a place.
     */
    public enum Event {

        /**
         * The bot died there, or on its way there.
         */
        DIED(1.0 / 3.0),

        /**
         * The bot ran or teleported away from an attacker there.
         */
        FLED(0.15),

        /**
         * The bot left after seeing someone else attacked there.
         */
        WITNESSED_ATTACK(0.10);

        /**
         * How much danger the event adds.
         */
        private final double danger;

        Event(double danger) {
            this.danger = danger;
        }
    }

    /**
     * Something a bot works at that runs out when taken and comes back later, so other players can use it up first.
     */
    public enum Resource {
        ROCKS,
        TREES,
        STALLS,
        CHESTS,
        CROPS,
        GROUND_ITEMS;

        /**
         * The name the resource is saved under.
         */
        private final String key = name().toLowerCase(Locale.ROOT);
    }

    private static final Resource[] RESOURCES = Resource.values();

    /**
     * One place the bot knows.
     */
    private static final class Place {

        /**
         * The zone's name.
         */
        private final String name;

        /**
         * The familiarity held at {@link #familiarAt}.
         */
        private double familiarity;

        /**
         * When {@link #familiarity} was last set, in time online.
         */
        private long familiarAt;

        /**
         * The crowding seen for each resource at its {@link #crowdedAt} time.
         */
        private final double[] crowding = new double[RESOURCES.length];

        /**
         * When each resource's {@link #crowding} was last set, in real time.
         */
        private final long[] crowdedAt = new long[RESOURCES.length];

        /**
         * The danger held at {@link #dangerAt}.
         */
        private double danger;

        /**
         * When {@link #danger} was last set, in time online.
         */
        private long dangerAt;

        private Place(String name) {
            this.name = name;
        }
    }

    /**
     * The time online over which familiarity halves, for a bot of average intelligence.
     */
    public static final long FAMILIARITY_HALF_LIFE = hours(336);

    /**
     * The time spent working at a new place that makes it half familiar.
     */
    public static final long GETS_FAMILIAR = hours(7);

    /**
     * The real time over which crowding halves.
     */
    public static final long CROWDING_HALF_LIFE = hours(1);

    /**
     * The time online over which danger halves, for a bot of average confidence.
     */
    public static final long DANGER_HALF_LIFE = hours(48);

    /**
     * Danger at least this strong keeps the bot away.
     */
    public static final double AVOID_DANGER = 0.6;

    /**
     * Crowding at least this strong makes the bot skip a place for that resource while it has another.
     */
    public static final double AVOID_CROWDING = 0.7;

    /**
     * The lowest {@link #preference(String) preference} a place can have.
     */
    private static final double LEAST_PREFERENCE = 0.25;

    /**
     * A strength weaker than this is gone.
     */
    private static final double FORGOTTEN = 0.02;

    /**
     * How many places the least intelligent bot keeps track of.
     */
    private static final int FEWEST_PLACES = 16;

    /**
     * How many more places the most intelligent bot keeps track of.
     */
    private static final int EXTRA_PLACES = 16;

    /**
     * The memory this part belongs to.
     */
    private final BotMemory memory;

    /**
     * Every place the bot knows, by zone name.
     */
    private final Map<String, Place> places = new LinkedHashMap<>();

    BotPlacesMemory(BotMemory memory) {
        this.memory = memory;
    }

    /**
     * Records something that happened to the bot at a place.
     *
     * @param zone The zone's name.
     * @param event What happened.
     * @return Whether the bot now stays away from the place.
     */
    public boolean remember(String zone, Event event) {
        Place place = place(zone);
        if (place == null) {
            return false;
        }
        long now = memory.now();
        place.danger = clamp(danger(place, now) + event.danger);
        place.dangerAt = now;
        return dangerous(place, now);
    }

    /**
     * Records that the bot gave up on a place because others had taken the resource it came for.
     *
     * @param zone The zone's name.
     * @param resource What others had taken.
     */
    public void crowded(String zone, Resource resource) {
        Place place = resource == null ? null : place(zone);
        if (place == null) {
            return;
        }
        place.crowding[resource.ordinal()] = 1.0;
        place.crowdedAt[resource.ordinal()] = memory.realTime();
    }

    /**
     * Records time the bot spent working at a place.
     *
     * @param zone The zone's name.
     * @param millis How long it worked there.
     */
    public void worked(String zone, long millis) {
        if (millis <= 0) {
            return;
        }
        Place place = place(zone);
        if (place == null) {
            return;
        }
        long now = memory.now();
        double familiar = familiarity(place, now);
        place.familiarity = familiar + (1.0 - familiar) * (1.0 - Math.pow(0.5, millis / (double) GETS_FAMILIAR));
        place.familiarAt = now;
    }

    /**
     * @param zone The zone's name.
     * @return Whether the bot stays away from the place because of what happened to it there.
     */
    public boolean dangerous(String zone) {
        Place place = places.get(zone);
        return place != null && dangerous(place, memory.now());
    }

    /**
     * @param zone The zone's name.
     * @param resource What the bot would work at there, or {@code null} for work no one else can use up.
     * @return Whether the bot found that resource at the place taken by others recently enough to skip it.
     */
    public boolean packed(String zone, Resource resource) {
        Place place = resource == null ? null : places.get(zone);
        return place != null && crowding(place, resource, memory.realTime()) >= AVOID_CROWDING;
    }

    /**
     * @param zone The zone's name.
     * @return How strongly the bot leans toward the place for work no one else can use up.
     * @see #preference(String, Resource)
     */
    public double preference(String zone) {
        return preference(zone, null);
    }

    /**
     * @param zone The zone's name.
     * @param resource What the bot would work at there, or {@code null} for work no one else can use up.
     * @return How strongly the bot leans toward the place for that work: {@code 1.0} for a place it knows nothing
     * about, up to {@code 2.0} for one it knows well, and down to {@code 0.25} for one that was dangerous or whose
     * resource was crowded.
     */
    public double preference(String zone, Resource resource) {
        Place place = places.get(zone);
        if (place == null) {
            return 1.0;
        }
        long now = memory.now();
        double crowding = resource == null ? 0.0 : crowding(place, resource, memory.realTime());
        double preference = (1.0 + familiarity(place, now)) * (1.0 - danger(place, now)) * (1.0 - crowding);
        return Math.max(LEAST_PREFERENCE, preference);
    }

    /**
     * @return How many places the bot can keep track of, by how intelligent it is.
     */
    public int capacity() {
        double intelligence = memory.personality().getIntelligence();
        return FEWEST_PLACES + (int) Math.round(EXTRA_PLACES * personal(intelligence));
    }

    /**
     * @return How many places the bot knows.
     */
    public int size() {
        return places.size();
    }

    @Override
    public String key() {
        return "places";
    }

    @Override
    public JsonElement save() {
        // Fading has no memory of its own, so the faded strengths and one time per clock say everything.
        long now = memory.now();
        long realTime = memory.realTime();
        JsonArray known = new JsonArray();
        for (Place place : places.values()) {
            double familiarity = familiarity(place, now);
            double danger = danger(place, now);
            if (Math.max(familiarity, Math.max(crowding(place, realTime), danger)) < FORGOTTEN) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", place.name);
            if (familiarity >= FORGOTTEN) {
                entry.addProperty("familiarity", rounded(familiarity));
            }
            JsonObject crowding = new JsonObject();
            for (Resource resource : RESOURCES) {
                double crowded = crowding(place, resource, realTime);
                if (crowded >= FORGOTTEN) {
                    crowding.addProperty(resource.key, rounded(crowded));
                }
            }
            if (!crowding.entrySet().isEmpty()) {
                entry.add("crowding", crowding);
            }
            if (danger >= FORGOTTEN) {
                entry.addProperty("danger", rounded(danger));
            }
            known.add(entry);
        }
        JsonObject data = new JsonObject();
        data.addProperty("as_of", now);
        data.addProperty("as_of_real", realTime);
        data.add("known", known);
        return data;
    }

    @Override
    public void load(JsonElement data) {
        clear();
        if (data == null || !data.isJsonObject()) {
            return;
        }
        JsonObject root = data.getAsJsonObject();
        JsonElement known = root.get("known");
        if (known == null || !known.isJsonArray()) {
            return;
        }
        long asOf = (long) number(root, "as_of", memory.now());
        long asOfReal = (long) number(root, "as_of_real", memory.realTime());
        for (JsonElement element : known.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String name = text(entry, "name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            Place place = new Place(name);
            place.familiarity = clamp(number(entry, "familiarity", 0.0));
            place.familiarAt = asOf;
            place.danger = clamp(number(entry, "danger", 0.0));
            place.dangerAt = asOf;
            JsonElement crowding = entry.get("crowding");
            if (crowding != null && crowding.isJsonObject()) {
                for (Resource resource : RESOURCES) {
                    place.crowding[resource.ordinal()] = clamp(number(crowding.getAsJsonObject(), resource.key, 0.0));
                    place.crowdedAt[resource.ordinal()] = asOfReal;
                }
            }
            if (Math.max(place.familiarity, Math.max(crowding(place, asOfReal), place.danger)) < FORGOTTEN) {
                continue;
            }
            places.put(name, place);
        }
        int capacity = capacity();
        long now = memory.now();
        long realTime = memory.realTime();
        while (places.size() > capacity) {
            places.remove(weakest(now, realTime).name);
        }
    }

    @Override
    public void clear() {
        places.clear();
    }

    @Override
    public List<String> describe() {
        long now = memory.now();
        long realTime = memory.realTime();
        List<String> lines = new ArrayList<>();
        lines.add("Places: " + places.size() + " of " + capacity());
        places.values().stream()
                .sorted(Comparator.comparingDouble((Place place) -> weight(place, now, realTime)).reversed())
                .forEach(place -> {
                    StringBuilder line = new StringBuilder(place.name).append(": preference ")
                            .append(rounded(preference(place.name)));
                    double familiarity = familiarity(place, now);
                    if (familiarity >= FORGOTTEN) {
                        line.append(", familiarity ").append(rounded(familiarity));
                    }
                    for (Resource resource : RESOURCES) {
                        double crowding = crowding(place, resource, realTime);
                        if (crowding >= FORGOTTEN) {
                            line.append(", ").append(resource.key)
                                    .append(crowding >= AVOID_CROWDING ? " packed " : " crowding ")
                                    .append(rounded(crowding));
                        }
                    }
                    double danger = danger(place, now);
                    if (danger >= FORGOTTEN) {
                        line.append(danger >= AVOID_DANGER ? ", avoided, danger " : ", danger ")
                                .append(rounded(danger));
                    }
                    lines.add(line.toString());
                });
        return lines;
    }

    /**
     * Finds a place, making room for it first if it is new.
     *
     * @param zone The zone's name.
     * @return The place, or {@code null} if there is no name to hold it under.
     */
    private Place place(String zone) {
        if (zone == null || zone.isEmpty()) {
            return null;
        }
        Place known = places.get(zone);
        if (known != null) {
            return known;
        }
        long now = memory.now();
        long realTime = memory.realTime();
        places.values().removeIf(place -> weight(place, now, realTime) < FORGOTTEN);
        if (places.size() >= capacity()) {
            places.remove(weakest(now, realTime).name);
        }
        Place place = new Place(zone);
        places.put(zone, place);
        return place;
    }

    /**
     * @return The place the bot remembers least.
     */
    private Place weakest(long now, long realTime) {
        Place weakest = null;
        double least = Double.MAX_VALUE;
        for (Place place : places.values()) {
            double weight = weight(place, now, realTime);
            if (weight < least) {
                least = weight;
                weakest = place;
            }
        }
        return weakest;
    }

    /**
     * @return How much the bot still remembers about a place, of any kind.
     */
    private double weight(Place place, long now, long realTime) {
        return Math.max(familiarity(place, now), Math.max(crowding(place, realTime), danger(place, now)));
    }

    private boolean dangerous(Place place, long now) {
        return danger(place, now) >= AVOID_DANGER;
    }

    /**
     * @return A place's familiarity after halving once for every half-life since it was set. Intelligent bots keep
     * it longer.
     */
    private double familiarity(Place place, long now) {
        if (place.familiarity <= 0.0) {
            return 0.0;
        }
        double halfLife = FAMILIARITY_HALF_LIFE * (0.6 + 0.8 * personal(memory.personality().getIntelligence()));
        return place.familiarity * Math.pow(0.5, Math.max(0, now - place.familiarAt) / halfLife);
    }

    /**
     * @return A resource's crowding at a place after halving once for every half-life of real time since it was seen.
     */
    private double crowding(Place place, Resource resource, long realTime) {
        int index = resource.ordinal();
        double crowding = place.crowding[index];
        return crowding <= 0.0 ? 0.0 :
                crowding * Math.pow(0.5, Math.max(0, realTime - place.crowdedAt[index]) / (double) CROWDING_HALF_LIFE);
    }

    /**
     * @return The strongest crowding the bot remembers at a place, for any resource.
     */
    private double crowding(Place place, long realTime) {
        double most = 0.0;
        for (Resource resource : RESOURCES) {
            most = Math.max(most, crowding(place, resource, realTime));
        }
        return most;
    }

    /**
     * @return A place's danger after halving once for every half-life since it was set. Confident bots let go
     * sooner: from one day of play for the most confident to three for the least.
     */
    private double danger(Place place, long now) {
        if (place.danger <= 0.0) {
            return 0.0;
        }
        double halfLife = DANGER_HALF_LIFE * (1.5 - personal(memory.personality().getConfidence()));
        return place.danger * Math.pow(0.5, Math.max(0, now - place.dangerAt) / halfLife);
    }

    /**
     * @return A personality trait from {@code 0.0} to {@code 1.0}, or the middle if it is not a number.
     */
    private static double personal(double trait) {
        return Double.isFinite(trait) ? clamp(trait) : 0.5;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double rounded(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private static long hours(long hours) {
        return hours * 3_600_000L;
    }

    private static double number(JsonObject entry, String key, double otherwise) {
        JsonElement value = entry.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return otherwise;
        }
        double number = value.getAsDouble();
        return Double.isFinite(number) ? number : otherwise;
    }

    private static String text(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        return value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() ? null :
                value.getAsString();
    }
}
