package io.luna.game.model.mob.bot.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who a bot knows, and how it feels about each of them.
 * <p>
 * Every person is held under their username with the grudge the bot bears them. A grudge fades by half over a set
 * stretch of the bot's time online, and a person with none left is forgotten. The feeling other code asks for is
 * {@code 0.5 - grudge}, so {@code 0.0} is strong dislike and {@code 0.5} is neutral, and someone the bot has never
 * met is neutral.
 * <p>
 * A grudge from being killed is {@link #GRUDGE_HALF_LIFE ordinary}: one kill is held for about a week of play. A
 * grudge from being cheated is {@link #DEEP_GRUDGE_HALF_LIFE deep} and lasts months. Kind bots let go sooner.
 * <p>
 * The number of people is limited by how social the bot is. When the limit is reached, the person the bot feels
 * least about makes room.
 *
 * @author TheLining
 */
public final class BotPeopleMemory implements BotMemoryPart {

    /**
     * Something a person did that the bot holds on to.
     */
    public enum Event {

        /**
         * They killed the bot.
         */
        KILLED_ME(0.25, false),

        /**
         * They cheated the bot in a trade.
         */
        SCAMMED_ME(0.50, true),

        /**
         * They led the bot somewhere to be killed for its items.
         */
        LURED_ME(0.50, true);

        /**
         * How much grudge the event adds.
         */
        private final double grudge;

        /**
         * Whether the grudge is the slow-fading kind.
         */
        private final boolean deep;

        Event(double grudge, boolean deep) {
            this.grudge = grudge;
            this.deep = deep;
        }
    }

    /**
     * One person the bot knows.
     */
    private static final class Person {

        /**
         * Their username.
         */
        private final String name;

        /**
         * The grudge held at {@link #grudgeAt}.
         */
        private double grudge;

        /**
         * When {@link #grudge} was last set.
         */
        private long grudgeAt;

        /**
         * Whether the grudge fades at the deep rate.
         */
        private boolean deep;

        /**
         * The last thing they did that the bot holds on to, or {@code null}.
         */
        private Event last;

        /**
         * When {@link #last} happened.
         */
        private long lastAt;

        private Person(String name) {
            this.name = name;
        }
    }

    /**
     * The feeling toward someone the bot knows nothing about.
     */
    public static final double NEUTRAL = 0.5;

    /**
     * The time online over which an ordinary grudge halves, for a bot of average kindness.
     */
    public static final long GRUDGE_HALF_LIFE = hours(120);

    /**
     * The time online over which a deep grudge halves, for a bot of average kindness.
     */
    public static final long DEEP_GRUDGE_HALF_LIFE = hours(720);

    /**
     * A grudge weaker than this is gone.
     */
    private static final double FORGOTTEN = 0.02;

    /**
     * How many people the least social bot keeps track of.
     */
    private static final int FEWEST_PEOPLE = 16;

    /**
     * How many more people the most social bot keeps track of.
     */
    private static final int EXTRA_PEOPLE = 32;

    /**
     * The memory this part belongs to.
     */
    private final BotMemory memory;

    /**
     * Everyone the bot knows, by username.
     */
    private final Map<String, Person> people = new LinkedHashMap<>();

    BotPeopleMemory(BotMemory memory) {
        this.memory = memory;
    }

    /**
     * @param username Who to ask about.
     * @return How the bot feels about them now, from {@code 0.0} to {@code 1.0}.
     */
    public double feeling(String username) {
        return clamp(NEUTRAL - grudge(username));
    }

    /**
     * @param username Who to ask about.
     * @return The grudge the bot holds against them now, from {@code 0.0} to {@code 1.0}.
     */
    public double grudge(String username) {
        Person person = people.get(username);
        return person == null ? 0.0 : grudge(person, memory.now());
    }

    /**
     * Records something a person did to the bot.
     *
     * @param username Who did it.
     * @param event What they did.
     */
    public void remember(String username, Event event) {
        Person person = person(username);
        if (person == null) {
            return;
        }
        long now = memory.now();
        double held = grudge(person, now);
        // A deep grudge stays deep for as long as any of it is left.
        person.deep = event.deep || person.deep && held >= FORGOTTEN;
        person.grudge = Math.min(1.0, held + event.grudge);
        person.grudgeAt = now;
        person.last = event;
        person.lastAt = now;
    }

    /**
     * @return The current feeling toward everyone the bot does not feel neutral about, by username.
     */
    public Map<String, Double> feelings() {
        Map<String, Double> feelings = new LinkedHashMap<>();
        long now = memory.now();
        for (Person person : people.values()) {
            double grudge = grudge(person, now);
            if (grudge >= FORGOTTEN) {
                feelings.put(person.name, clamp(NEUTRAL - grudge));
            }
        }
        return feelings;
    }

    /**
     * @return How many people the bot can keep track of, by how social it is.
     */
    public int capacity() {
        double social = memory.personality().getSocial();
        return FEWEST_PEOPLE + (int) Math.round(EXTRA_PEOPLE * (Double.isFinite(social) ? clamp(social) : 0.5));
    }

    /**
     * @return How many people the bot knows.
     */
    public int size() {
        return people.size();
    }

    @Override
    public String key() {
        return "people";
    }

    @Override
    public JsonElement save() {
        // Fading has no memory of its own, so the faded grudges and one shared time say everything.
        long now = memory.now();
        JsonArray known = new JsonArray();
        for (Person person : people.values()) {
            double grudge = grudge(person, now);
            if (grudge < FORGOTTEN) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", person.name);
            entry.addProperty("grudge", rounded(grudge));
            if (person.deep) {
                entry.addProperty("deep", true);
            }
            if (person.last != null) {
                entry.addProperty("last", person.last.name());
                entry.addProperty("last_at", person.lastAt);
            }
            known.add(entry);
        }
        JsonObject data = new JsonObject();
        data.addProperty("as_of", now);
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
        long now = memory.now();
        long asOf = (long) number(root, "as_of", now);
        for (JsonElement element : known.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String name = text(entry, "name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            double grudge = clamp(number(entry, "grudge", 0.0));
            if (grudge < FORGOTTEN) {
                continue;
            }
            Person person = new Person(name);
            person.grudge = grudge;
            person.grudgeAt = asOf;
            person.deep = entry.has("deep") && entry.get("deep").isJsonPrimitive() && entry.get("deep").getAsBoolean();
            person.last = event(text(entry, "last"));
            person.lastAt = (long) number(entry, "last_at", 0.0);
            people.put(name, person);
        }
        int capacity = capacity();
        while (people.size() > capacity) {
            people.remove(weakest(now).name);
        }
    }

    @Override
    public void clear() {
        people.clear();
    }

    @Override
    public List<String> describe() {
        long now = memory.now();
        List<String> lines = new ArrayList<>();
        lines.add("People: " + people.size() + " of " + capacity());
        people.values().stream()
                .sorted(Comparator.comparingDouble((Person person) -> grudge(person, now)).reversed())
                .forEach(person -> {
                    double grudge = grudge(person, now);
                    StringBuilder line = new StringBuilder(person.name).append(": feeling ")
                            .append(rounded(clamp(NEUTRAL - grudge)))
                            .append(person.deep ? ", deep grudge " : ", grudge ").append(rounded(grudge));
                    if (person.last != null) {
                        line.append(", ").append(person.last).append(' ')
                                .append(Math.max(0, now - person.lastAt) / hours(1)).append(" h ago");
                    }
                    lines.add(line.toString());
                });
        return lines;
    }

    /**
     * Finds a person, making room for them first if they are new.
     *
     * @param username Their username.
     * @return The person, or {@code null} if there is no name to hold them under.
     */
    private Person person(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        Person known = people.get(username);
        if (known != null) {
            return known;
        }
        long now = memory.now();
        people.values().removeIf(person -> grudge(person, now) < FORGOTTEN);
        if (people.size() >= capacity()) {
            people.remove(weakest(now).name);
        }
        Person person = new Person(username);
        people.put(username, person);
        return person;
    }

    /**
     * @return The person the bot feels least about.
     */
    private Person weakest(long now) {
        Person weakest = null;
        double least = Double.MAX_VALUE;
        for (Person person : people.values()) {
            double grudge = grudge(person, now);
            if (grudge < least) {
                least = grudge;
                weakest = person;
            }
        }
        return weakest;
    }

    /**
     * @return A person's grudge after halving once for every half-life since it was set.
     */
    private double grudge(Person person, long now) {
        if (person.grudge <= 0.0) {
            return 0.0;
        }
        double kindness = memory.personality().getKindness();
        double patience = 1.4 - 0.8 * (Double.isFinite(kindness) ? clamp(kindness) : 0.5);
        double halfLife = (person.deep ? DEEP_GRUDGE_HALF_LIFE : GRUDGE_HALF_LIFE) * patience;
        return person.grudge * Math.pow(0.5, Math.max(0, now - person.grudgeAt) / halfLife);
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

    private static Event event(String name) {
        if (name != null) {
            for (Event event : Event.values()) {
                if (event.name().equals(name)) {
                    return event;
                }
            }
        }
        return null;
    }
}
