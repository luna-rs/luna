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
 * Every person is held under their username with two strengths: a grudge and a liking. Both fade by half over a set
 * stretch of the bot's time online. A person with neither left is forgotten unless the bot remembers messaging them
 * after a PK. The feeling other code asks for is {@code 0.5 + liking - grudge}, so {@code 0.0} is strong dislike,
 * {@code 0.5} is neutral and {@code 1.0} is strong approval. Someone the bot has never met is neutral.
 * <p>
 * A grudge from being killed or chased off is {@link #GRUDGE_HALF_LIFE ordinary}: one kill is held for about a week of
 * play. A grudge from being cheated is {@link #DEEP_GRUDGE_HALF_LIFE deep} and lasts months. Kind bots let go sooner.
 * Killing someone back settles as much grudge as one kill leaves.
 * <p>
 * Liking comes in small steps from good dealings, and each kind of dealing can only raise it so far. None of them
 * reaches the level other code reads as love.
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
        KILLED_ME(0.25, false, 0.0, 0.0),

        /**
         * They attacked the bot and drove it off. It takes two of these within a few days of play to make a grudge
         * the bot acts on.
         */
        CHASED_ME(0.06, false, 0.0, 0.0),

        /**
         * They cheated the bot in a trade.
         */
        SCAMMED_ME(0.50, true, 0.0, 0.0),

        /**
         * They led the bot somewhere to be killed for its items.
         */
        LURED_ME(0.50, true, 0.0, 0.0),

        /**
         * The bot killed them, which settles one kill's worth of grudge.
         */
        KILLED_THEM(-0.25, false, 0.0, 0.0),

        /**
         * They greeted the bot or spoke to it by name.
         */
        GREETED_ME(0.0, false, 0.03, 0.10),

        /**
         * They and the bot completed a trade.
         */
        TRADED(0.0, false, 0.05, 0.25),

        /**
         * They and the bot worked together for pay.
         */
        PARTNERED(0.0, false, 0.05, 0.25);

        /**
         * How much grudge the event adds, or settles if below zero.
         */
        private final double grudge;

        /**
         * Whether the grudge is the slow-fading kind.
         */
        private final boolean deep;

        /**
         * How much liking the event adds.
         */
        private final double liking;

        /**
         * The most liking this kind of event can build up to.
         */
        private final double likingLimit;

        Event(double grudge, boolean deep, double liking, double likingLimit) {
            this.grudge = grudge;
            this.deep = deep;
            this.liking = liking;
            this.likingLimit = likingLimit;
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
         * The liking held at {@link #likingAt}.
         */
        private double liking;

        /**
         * When {@link #liking} was last set.
         */
        private long likingAt;

        /**
         * The last thing that happened between them and the bot, or {@code null}.
         */
        private Event last;

        /**
         * When {@link #last} happened.
         */
        private long lastAt;

        /**
         * Whether the bot previously sent this person a message after they killed it.
         */
        private boolean pkDeathMessageSent;

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
     * The time online over which liking halves.
     */
    public static final long LIKING_HALF_LIFE = hours(168);

    /**
     * A grudge at least this strong is one the bot acts on. It is where a feeling leaves the neutral band.
     */
    public static final double GRUDGE_HELD = 0.10;

    /**
     * A grudge or liking weaker than this is gone.
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
        Person person = people.get(username);
        return person == null ? NEUTRAL : feeling(person, memory.now());
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
     * @param username Who to ask about.
     * @return Whether the bot bears them a grudge strong enough to act on.
     */
    public boolean holdsGrudge(String username) {
        return grudge(username) >= GRUDGE_HELD;
    }

    /**
     * @param username Who to ask about.
     * @return The bot's time online since anything last happened between them, or {@code -1} for a stranger.
     */
    public long sinceLast(String username) {
        Person person = people.get(username);
        return person == null || person.last == null ? -1 : Math.max(0, memory.now() - person.lastAt);
    }

    /**
     * @param username Who to ask about.
     * @param within How far back to look, in milliseconds of the bot's time online.
     * @return Whether the last thing between them was a good dealing, no longer ago than {@code within}.
     */
    public boolean dealtWellWith(String username, long within) {
        Person person = people.get(username);
        return person != null && person.last != null && person.last.liking > 0.0 &&
                memory.now() - person.lastAt < within;
    }

    /**
     * @param events The events to look for.
     * @return The username of whoever most recently did one of {@code events} as their last act, or {@code null}.
     */
    public String latest(Event... events) {
        Person latest = null;
        for (Person person : people.values()) {
            if (person.last == null || latest != null && person.lastAt < latest.lastAt) {
                continue;
            }
            for (Event event : events) {
                if (person.last == event) {
                    latest = person;
                    break;
                }
            }
        }
        return latest == null ? null : latest.name;
    }

    /**
     * @param username Who to ask about.
     * @return Whether the bot remembers sending this person a PK death message.
     */
    public boolean hasSentPkDeathMessage(String username) {
        Person person = people.get(username);
        return person != null && person.pkDeathMessageSent;
    }

    /**
     * Remembers a delivered PK death message independently of the bot's last dealing and feelings.
     *
     * @param username Who received the message.
     */
    public void rememberPkDeathMessage(String username) {
        Person person = person(username);
        if (person != null) {
            person.pkDeathMessageSent = true;
        }
    }

    /**
     * Records something that happened between a person and the bot.
     * <p>
     * An event that only settles a grudge does nothing for someone the bot does not know.
     *
     * @param username Who it happened with.
     * @param event What happened.
     */
    public void remember(String username, Event event) {
        boolean adds = event.grudge > 0.0 || event.liking > 0.0;
        Person person = adds ? person(username) : people.get(username);
        if (person == null) {
            return;
        }
        long now = memory.now();
        if (event.grudge != 0.0) {
            double held = grudge(person, now);
            // A deep grudge stays deep for as long as any of it is left.
            person.deep = event.deep || person.deep && held >= FORGOTTEN;
            person.grudge = clamp(held + event.grudge);
            person.grudgeAt = now;
        }
        if (event.liking > 0.0) {
            double liked = liking(person, now);
            person.liking = Math.max(liked, Math.min(event.likingLimit, liked + event.liking));
            person.likingAt = now;
        }
        person.last = event;
        person.lastAt = now;
        // The most recent dealings come last, which settles ties in time.
        people.remove(person.name);
        people.put(person.name, person);
    }

    /**
     * @return The current feeling toward everyone the bot does not feel neutral about, by username.
     */
    public Map<String, Double> feelings() {
        Map<String, Double> feelings = new LinkedHashMap<>();
        long now = memory.now();
        for (Person person : people.values()) {
            double feeling = feeling(person, now);
            if (Math.abs(feeling - NEUTRAL) >= FORGOTTEN) {
                feelings.put(person.name, feeling);
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
        // Fading has no memory of its own, so the faded strengths and one shared time say everything.
        long now = memory.now();
        JsonArray known = new JsonArray();
        for (Person person : people.values()) {
            double grudge = grudge(person, now);
            double liking = liking(person, now);
            if (Math.max(grudge, liking) < FORGOTTEN && !person.pkDeathMessageSent) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", person.name);
            if (grudge >= FORGOTTEN) {
                entry.addProperty("grudge", rounded(grudge));
                if (person.deep) {
                    entry.addProperty("deep", true);
                }
            }
            if (liking >= FORGOTTEN) {
                entry.addProperty("liking", rounded(liking));
            }
            if (person.last != null) {
                entry.addProperty("last", person.last.name());
                entry.addProperty("last_at", person.lastAt);
            }
            if (person.pkDeathMessageSent) {
                entry.addProperty("pk_death_pm", true);
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
            double liking = clamp(number(entry, "liking", 0.0));
            JsonElement pkDeathPm = entry.get("pk_death_pm");
            boolean pkDeathMessageSent = pkDeathPm != null && pkDeathPm.isJsonPrimitive() &&
                    pkDeathPm.getAsJsonPrimitive().isBoolean() && pkDeathPm.getAsBoolean();
            if (Math.max(grudge, liking) < FORGOTTEN && !pkDeathMessageSent) {
                continue;
            }
            Person person = new Person(name);
            person.grudge = grudge;
            person.grudgeAt = asOf;
            person.liking = liking;
            person.likingAt = asOf;
            person.deep = entry.has("deep") && entry.get("deep").isJsonPrimitive() && entry.get("deep").getAsBoolean();
            person.last = event(text(entry, "last"));
            person.lastAt = (long) number(entry, "last_at", 0.0);
            person.pkDeathMessageSent = pkDeathMessageSent;
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
                .sorted(Comparator.comparingDouble((Person person) -> weight(person, now)).reversed())
                .forEach(person -> {
                    StringBuilder line = new StringBuilder(person.name).append(": feeling ")
                            .append(rounded(feeling(person, now)));
                    double grudge = grudge(person, now);
                    if (grudge >= FORGOTTEN) {
                        line.append(person.deep ? ", deep grudge " : ", grudge ").append(rounded(grudge));
                    }
                    double liking = liking(person, now);
                    if (liking >= FORGOTTEN) {
                        line.append(", liking ").append(rounded(liking));
                    }
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
        people.values().removeIf(person -> weight(person, now) < FORGOTTEN && !person.pkDeathMessageSent);
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
            double weight = weight(person, now);
            if (weight < least) {
                least = weight;
                weakest = person;
            }
        }
        return weakest;
    }

    /**
     * @return How much the bot still feels about a person, either way.
     */
    private double weight(Person person, long now) {
        return Math.max(grudge(person, now), liking(person, now));
    }

    private double feeling(Person person, long now) {
        return clamp(NEUTRAL + liking(person, now) - grudge(person, now));
    }

    /**
     * @return A person's liking after halving once for every half-life since it was set.
     */
    private double liking(Person person, long now) {
        return person.liking <= 0.0 ? 0.0 :
                person.liking * Math.pow(0.5, Math.max(0, now - person.likingAt) / (double) LIKING_HALF_LIFE);
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
