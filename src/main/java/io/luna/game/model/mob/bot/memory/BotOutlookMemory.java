package io.luna.game.model.mob.bot.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * How things have been going for a bot lately: the part of each of its emotions that lasts.
 * <p>
 * A bot's emotions react to what happens to it for a few minutes at a time. The outlook is what is left after that:
 * one lasting lean for each {@link EmotionType}, from {@code -1.0} to {@code 1.0}. Being killed leaves a bot unhappy
 * and wary for the rest of the day, a rare drop leaves it cheerful and content, and being scammed leaves it sore and
 * wanting its money back. Each lean halves about once a day of real time, so a bot that logs off after a bad night
 * comes back mostly over it.
 * <p>
 * Only things worth remembering move the outlook. Passing scares, such as walking into the Wilderness, stay with the
 * emotions. A move further from neutral covers a share of what is left to the limit, so a run of the same thing
 * counts for less each time and no lean can pass the limit. A move back toward neutral counts in full, so good news
 * on a bad day helps without wiping the day out.
 * <p>
 * The outlook is read in two places. A bot's emotions add it to their scores, so a wary bot eats sooner and a wanting
 * one values loot more. The activity roll reads it across activities: a wary bot picks less dangerous work, a wanting
 * one picks more work that pays, and one in low spirits takes it easy. What happened in one activity or at one place
 * is for the diary and the places to remember. A bot that is wary enough is also left out when others are called to
 * patrol the Wilderness.
 * <p>
 * The most notable recent events are kept by name, so the bot can say why it feels as it does. Activities are held by
 * name.
 *
 * @author TheLining
 */
public final class BotOutlookMemory implements BotMemoryPart {

    /**
     * Something that happened to the bot and stays with it. Each event moves the lasting lean of every emotion by a
     * share: a positive share toward more of the emotion, a negative one toward less.
     */
    public enum Event {

        /**
         * A player killed the bot.
         */
        KILLED_BY_PLAYER(-0.30, 0.35, 0.10, true),

        /**
         * A monster killed the bot.
         */
        KILLED_BY_MONSTER(-0.25, 0.20, 0.0, true),

        /**
         * The bot killed a player.
         */
        KILLED_PLAYER(0.25, -0.20, -0.10, true),

        /**
         * The bot got away from a fight with almost no health left.
         */
        NARROW_ESCAPE(0.0, 0.10, 0.0, false),

        /**
         * The bot gained a level. The shares are for level 99, and lower levels count for less.
         */
        LEVELLED(0.15, 0.0, 0.0, false),

        /**
         * The bot reached a level it was working toward.
         */
        REACHED_GOAL(0.25, 0.0, 0.0, true),

        /**
         * The bot gave up on a level it was working toward.
         */
        GAVE_UP_GOAL(-0.08, 0.0, 0.0, false),

        /**
         * The bot got a rare or valuable drop.
         */
        RARE_DROP(0.35, 0.0, -0.25, true),

        /**
         * The bot was scammed in a trade.
         */
        SCAMMED(-0.35, 0.0, 0.30, true),

        /**
         * The bot was lured into the Wilderness and killed for its items. The death is counted apart.
         */
        LURED(-0.20, 0.15, 0.20, true),

        /**
         * A session left the bot richer. The shares are for a gain of a tenth of what it owned or more.
         */
        GAINED_WEALTH(0.15, 0.0, -0.25, true),

        /**
         * A session left the bot poorer. The shares are for a loss of a tenth of what it owned or more.
         */
        LOST_WEALTH(-0.15, 0.0, 0.25, true);

        /**
         * All events, read once.
         */
        private static final Event[] ALL = values();

        /**
         * The share it moves {@link EmotionType#HAPPY}.
         */
        private final double happy;

        /**
         * The share it moves {@link EmotionType#SCARED}.
         */
        private final double scared;

        /**
         * The share it moves {@link EmotionType#GREEDY}.
         */
        private final double greedy;

        /**
         * Whether the bot keeps it by name, to talk about.
         */
        private final boolean notable;

        Event(double happy, double scared, double greedy, boolean notable) {
            this.happy = happy;
            this.scared = scared;
            this.greedy = greedy;
            this.notable = notable;
        }

        /**
         * @return Whether this is something good, which lifts the bot's spirits.
         */
        public boolean lifts() {
            return happy > 0.0;
        }
    }

    /**
     * How the bot would say things have been going, by how happy its outlook is.
     */
    public enum Run {

        /**
         * Things have been going badly.
         */
        BAD,

        /**
         * Nothing much either way.
         */
        FAIR,

        /**
         * Things have been going well.
         */
        GOOD
    }

    /**
     * A notable event the bot still remembers.
     *
     * @param what The event.
     * @param atReal When it happened, in real time.
     */
    private record Mark(Event what, long atReal) {
    }

    /**
     * The real time over which a lean halves.
     */
    public static final long HALF_LIFE = hours(24);

    /**
     * How long a notable event is kept by name, in real time.
     */
    public static final long KEPT = hours(48);

    /**
     * How far from neutral the happy lean has to be for a {@link Run#GOOD good} or {@link Run#BAD bad} run.
     */
    public static final double MARKED = 0.25;

    /**
     * How wary a bot has to be before it is {@link #isShaken() shaken}. An ordinary bot gets there after two or three
     * deaths close together.
     */
    public static final double SHAKEN = 0.4;

    /**
     * How much of a dangerous activity a bot gives up when it is as wary as it can be.
     */
    private static final double WARY_SWAY = 0.9;

    /**
     * How much more of a dangerous activity a bot takes on when it is as bold as it can be.
     */
    private static final double BOLD_SWAY = 0.4;

    /**
     * How much more paying work a bot takes on when it wants money as badly as it can.
     */
    private static final double WANTING_SWAY = 0.6;

    /**
     * How much paying work a bot gives up when it is as content as it can be.
     */
    private static final double CONTENT_SWAY = 0.25;

    /**
     * How much more easy-going activity a bot takes on when its spirits are as low as they can be.
     */
    private static final double EASE_SWAY = 0.4;

    /**
     * How much bolder being killed by a player leaves a bot that is {@link #tilts() too sure of itself} to be scared.
     */
    private static final double TILT_BOLDER = 0.15;

    /**
     * How much more such a bot wants its losses back.
     */
    private static final double TILT_WANTING = 0.25;

    /**
     * The share of a level 99's lift that the lowest levels give.
     */
    private static final double LEAST_LEVEL = 0.07;

    /**
     * A lean weaker than this is gone.
     */
    private static final double FORGOTTEN = 0.01;

    /**
     * How many notable events are kept by name. The oldest makes room.
     */
    private static final int MOST_MARKS = 12;

    /**
     * The emotions, read once.
     */
    private static final EmotionType[] EMOTIONS = EmotionType.values();

    /**
     * The memory this part belongs to.
     */
    private final BotMemory memory;

    /**
     * The lean of each emotion as it stood at {@link #at}, by {@link EmotionType#ordinal()}.
     */
    private final double[] held = new double[EMOTIONS.length];

    /**
     * The notable events, oldest first.
     */
    private final Deque<Mark> marks = new ArrayDeque<>();

    /**
     * When {@link #held} was last brought up to date, in real time.
     */
    private long at;

    BotOutlookMemory(BotMemory memory) {
        this.memory = memory;
    }

    /**
     * Takes in something that happened to the bot.
     *
     * @param event What happened.
     */
    public void felt(Event event) {
        felt(event, 1.0);
    }

    /**
     * Takes in a level the bot gained. Higher levels lift it more.
     *
     * @param level The level reached.
     */
    public void levelled(int level) {
        double height = Math.clamp(level / 99.0, 0.0, 1.0);
        felt(Event.LEVELLED, LEAST_LEVEL + (1.0 - LEAST_LEVEL) * height * height);
    }

    /**
     * Takes in how a finished session changed the bot's wealth.
     *
     * @param wealthShare The change, where {@code 1.0} is a gain of a tenth of what the bot owned or more and
     * {@code -1.0} a loss of as much.
     */
    public void sessionEnded(double wealthShare) {
        if (!Double.isFinite(wealthShare) || wealthShare == 0.0) {
            return;
        }
        felt(wealthShare > 0.0 ? Event.GAINED_WEALTH : Event.LOST_WEALTH, Math.min(1.0, Math.abs(wealthShare)));
    }

    /**
     * @param type The emotion.
     * @return The bot's lasting lean toward the emotion now, from {@code -1.0} (far less of it than usual) to
     * {@code 1.0} (far more).
     */
    public double of(EmotionType type) {
        double lean = held[type.ordinal()];
        return lean == 0.0 ? 0.0 : lean * faded(memory.realTime() - at, halfLife(type));
    }

    /**
     * @param activity The activity's name.
     * @return The multiplier the outlook puts on the bot's interest in the activity. A wary bot wants less of
     * dangerous work and a bold one more, a wanting bot wants more of what pays and a content one a little less, and
     * a bot in low spirits wants more of what is easy-going.
     */
    public double interestIn(String activity) {
        if (activity == null) {
            return 1.0;
        }
        double interest = 1.0;
        double danger = danger(activity);
        if (danger > 0.0) {
            double scared = of(EmotionType.SCARED);
            interest *= 1.0 - danger * scared * (scared > 0.0 ? WARY_SWAY : BOLD_SWAY);
        }
        double pay = pay(activity);
        if (pay > 0.0) {
            double greedy = of(EmotionType.GREEDY);
            interest *= 1.0 + pay * greedy * (greedy > 0.0 ? WANTING_SWAY : CONTENT_SWAY);
        }
        double ease = ease(activity);
        if (ease > 0.0) {
            interest *= 1.0 + ease * EASE_SWAY * Math.max(0.0, -of(EmotionType.HAPPY));
        }
        return Math.max(0.0, interest);
    }

    /**
     * @return How the bot would say things have been going.
     */
    public Run run() {
        double happy = of(EmotionType.HAPPY);
        if (happy >= MARKED) {
            return Run.GOOD;
        }
        return happy <= -MARKED ? Run.BAD : Run.FAIR;
    }

    /**
     * @return Whether the bot is wary enough to turn down dangerous work it did not choose, such as a call to patrol
     * the Wilderness. It may still pick such work by itself, less often.
     */
    public boolean isShaken() {
        return of(EmotionType.SCARED) >= SHAKEN;
    }

    /**
     * @param event A notable event.
     * @param within How far back to look, in milliseconds of real time.
     * @return How many times it happened to the bot in that time, as far as it remembers.
     */
    public int count(Event event, long within) {
        long since = memory.realTime() - within;
        int count = 0;
        for (Mark mark : marks) {
            if (mark.what == event && mark.atReal >= since) {
                count++;
            }
        }
        return count;
    }

    /**
     * @param good Whether to look for something good or something bad.
     * @param within How far back to look, in milliseconds of real time.
     * @return The latest notable event of that kind in that time, or {@code null}.
     */
    public Event latest(boolean good, long within) {
        long since = memory.realTime() - within;
        for (Iterator<Mark> newestFirst = marks.descendingIterator(); newestFirst.hasNext(); ) {
            Mark mark = newestFirst.next();
            if (mark.atReal < since) {
                break;
            }
            if (mark.what.lifts() == good) {
                return mark.what;
            }
        }
        return null;
    }

    @Override
    public String key() {
        return "outlook";
    }

    @Override
    public JsonElement save() {
        JsonObject data = new JsonObject();
        long realTime = memory.realTime();
        boolean leaning = false;
        for (EmotionType type : EMOTIONS) {
            double lean = of(type);
            if (Math.abs(lean) >= FORGOTTEN) {
                data.addProperty(key(type), rounded(lean));
                leaning = true;
            }
        }
        if (leaning) {
            data.addProperty("as_of_real", realTime);
        }
        JsonArray kept = new JsonArray();
        for (Mark mark : marks) {
            if (realTime - mark.atReal > KEPT) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("what", mark.what.name());
            entry.addProperty("at_real", mark.atReal);
            kept.add(entry);
        }
        if (!kept.isEmpty()) {
            data.add("lately", kept);
        }
        return data;
    }

    @Override
    public void load(JsonElement data) {
        clear();
        if (data == null || !data.isJsonObject()) {
            return;
        }
        JsonObject root = data.getAsJsonObject();
        long realTime = memory.realTime();
        at = (long) number(root, "as_of_real", realTime);
        for (EmotionType type : EMOTIONS) {
            double lean = Math.clamp(number(root, key(type), 0.0), -1.0, 1.0);
            held[type.ordinal()] = Math.abs(lean) < FORGOTTEN ? 0.0 : lean;
        }
        JsonElement kept = root.get("lately");
        if (kept != null && kept.isJsonArray()) {
            for (JsonElement element : kept.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                Event what = event(text(entry, "what"));
                long atReal = (long) number(entry, "at_real", 0.0);
                if (what == null || !what.notable || atReal <= 0 || realTime - atReal > KEPT) {
                    continue;
                }
                marks.addLast(new Mark(what, atReal));
            }
            trim();
        }
    }

    @Override
    public void clear() {
        Arrays.fill(held, 0.0);
        marks.clear();
        at = 0L;
    }

    @Override
    public List<String> describe() {
        long realTime = memory.realTime();
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder("Outlook: ");
        for (EmotionType type : EMOTIONS) {
            double lean = rounded(of(type));
            line.append(key(type)).append(' ').append(lean > 0.0 ? "+" : "").append(lean).append(", ");
        }
        lines.add(line.append("a ").append(run().name().toLowerCase(Locale.ROOT)).append(" run").toString());
        for (Iterator<Mark> newestFirst = marks.descendingIterator(); newestFirst.hasNext(); ) {
            Mark mark = newestFirst.next();
            long age = Math.max(0, realTime - mark.atReal);
            if (age <= KEPT) {
                lines.add("Lately: " + mark.what.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ", " +
                        age / 60_000L + " min ago");
            }
        }
        return lines;
    }

    /**
     * Moves every lean by an event's shares, scaled by {@code size}.
     */
    private void felt(Event event, double size) {
        if (event == null || !(size > 0.0)) {
            return;
        }
        long realTime = memory.realTime();
        for (EmotionType type : EMOTIONS) {
            held[type.ordinal()] = of(type);
        }
        at = realTime;

        double scared = event.scared * size;
        double greedy = event.greedy * size;
        if (event == Event.KILLED_BY_PLAYER && tilts()) {
            scared = -TILT_BOLDER * size;
            greedy = TILT_WANTING * size;
        } else if (scared > 0.0) {
            scared *= nerves();
        }
        move(EmotionType.HAPPY, event.happy * size);
        move(EmotionType.SCARED, scared);
        move(EmotionType.GREEDY, greedy);

        if (event.notable && size >= 1.0) {
            marks.removeIf(mark -> realTime - mark.atReal > KEPT);
            marks.addLast(new Mark(event, realTime));
            trim();
        }
    }

    /**
     * Moves one lean. A move further from neutral covers {@code share} of what is left to the limit, and a move back
     * toward neutral is {@code share} in full.
     *
     * @param share The share: positive toward {@code 1.0}, negative toward {@code -1.0}.
     */
    private void move(EmotionType type, double share) {
        int index = type.ordinal();
        double lean = held[index];
        double room = 1.0 - Math.max(0.0, Math.signum(share) * lean);
        held[index] = Math.clamp(lean + share * room, -1.0, 1.0);
    }

    /**
     * @return Whether being killed by a player makes this bot want to go straight back out. A bot that is confident
     * and not bright does not learn fear from it.
     */
    private boolean tilts() {
        return memory.personality().isStupidlyConfident();
    }

    /**
     * @return How hard a fright hits this bot and how slowly it gets over one, from half as much for the most
     * confident bot to half as much again for the least.
     */
    private double nerves() {
        return 1.5 - personal(memory.personality().getConfidence());
    }

    /**
     * @return The real time over which the lean of an emotion halves. Fear lasts longer in a bot with less confidence.
     */
    private long halfLife(EmotionType type) {
        return type == EmotionType.SCARED ? Math.round(HALF_LIFE * nerves()) : HALF_LIFE;
    }

    /**
     * Drops the oldest notable events beyond what is kept.
     */
    private void trim() {
        while (marks.size() > MOST_MARKS) {
            marks.removeFirst();
        }
    }

    /**
     * @return How dangerous an activity is, from {@code 0.0} to {@code 1.0}.
     */
    private static double danger(String activity) {
        return switch (activity) {
            case "PKING" -> 1.0;
            case "TRAINING_COMBAT", "PROFIT_COMBAT" -> 0.5;
            default -> 0.0;
        };
    }

    /**
     * @return How much an activity is about making money, from {@code 0.0} to {@code 1.0}.
     */
    private static double pay(String activity) {
        return switch (activity) {
            case "PROFIT_SKILLS", "PROFIT_COMBAT", "GENERAL_PROFIT", "MERCHANTING" -> 1.0;
            case "PKING" -> 0.5;
            default -> 0.0;
        };
    }

    /**
     * @return How easy-going an activity is, from {@code 0.0} to {@code 1.0}.
     */
    private static double ease(String activity) {
        return switch (activity) {
            case "SOCIALIZING", "MINIGAMES" -> 1.0;
            case "GENERAL_ACTIVITIES" -> 0.5;
            default -> 0.0;
        };
    }

    /**
     * @return What is left of one after {@code age}, halving once every {@code halfLife}.
     */
    private static double faded(long age, long halfLife) {
        return Math.pow(0.5, Math.max(0, age) / (double) halfLife);
    }

    private static String key(EmotionType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static Event event(String name) {
        if (name == null) {
            return null;
        }
        for (Event event : Event.ALL) {
            if (event.name().equals(name)) {
                return event;
            }
        }
        return null;
    }

    /**
     * @return A personality trait from {@code 0.0} to {@code 1.0}, or the middle if it is not a number.
     */
    private static double personal(double trait) {
        return Double.isFinite(trait) ? Math.clamp(trait, 0.0, 1.0) : 0.5;
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
