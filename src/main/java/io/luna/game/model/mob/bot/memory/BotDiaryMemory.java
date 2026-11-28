package io.luna.game.model.mob.bot.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.SkillSet;
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a bot has been doing: its recent sessions of work and how each one went.
 * <p>
 * A session opens when the bot picks an activity and closes when it next has nothing to do, so it covers the work,
 * the banking and the selling that came of one choice. It survives a logout in between. Each session keeps how long
 * it lasted and what it changed: experience, levels, wealth, player kills and deaths. From those it gets one score
 * for {@link Session#went how it went}.
 * <p>
 * The diary turns that into how much the bot feels like an activity or a skill right now, as a multiplier on its
 * usual interest:
 * <ul>
 *     <li>It tires of what it has just been doing. The tiredness halves every few hours of real time, so a bot that
 *     logs off comes back fresh.</li>
 *     <li>It leans toward what has been going well for it and away from what has not. That fades by its time
 *     online.</li>
 *     <li>A choice that started nothing puts it off trying the same thing again for some minutes.</li>
 * </ul>
 * A choice that started nothing is not kept as a session. The number of sessions is limited by how intelligent the
 * bot is, and the oldest makes room. Activities are held by name.
 *
 * @author TheLining
 */
public final class BotDiaryMemory implements BotMemoryPart {

    /**
     * Where an account stands at one moment. A session is measured between two of these.
     *
     * @param experience The experience in all skills together.
     * @param levels The levels in all skills together.
     * @param wealth The worth of everything the account owns, in coins.
     */
    public record Standing(long experience, int levels, long wealth) {
    }

    /**
     * A session that has just closed.
     *
     * @param activity The name of the activity the bot chose.
     * @param skill The skill it was about, or {@link #NO_SKILL}.
     * @param started Whether it started anything. One that did not is not kept as a session.
     * @param wealth How it changed the bot's wealth, where {@code 1.0} is a gain of a tenth of what the bot owned or
     * more and {@code -1.0} a loss of as much.
     */
    public record Closed(String activity, int skill, boolean started, double wealth) {
    }

    /**
     * One finished session.
     */
    private static final class Session {

        /**
         * The name of the activity the bot chose.
         */
        private final String activity;

        /**
         * The skill it worked on, or {@link #NO_SKILL}.
         */
        private int skill = NO_SKILL;

        /**
         * When it ended, in time online.
         */
        private long endedAt;

        /**
         * When it ended, in real time.
         */
        private long endedReal;

        /**
         * How long it lasted, in minutes online.
         */
        private int minutes;

        /**
         * The experience gained in all skills together.
         */
        private long experience;

        /**
         * The levels gained in all skills together.
         */
        private int levels;

        /**
         * How much the bot's wealth changed, in coins.
         */
        private long coins;

        /**
         * The players it killed.
         */
        private int kills;

        /**
         * The times it died.
         */
        private int deaths;

        /**
         * How the session went for the bot, from {@code -1.0} to {@code 1.0}.
         */
        private double went;

        private Session(String activity) {
            this.activity = activity;
        }
    }

    /**
     * The session the bot is in.
     */
    private static final class Open {

        /**
         * The name of the activity the bot chose.
         */
        private final String activity;

        /**
         * The skill it is working on, or {@link #NO_SKILL}.
         */
        private int skill = NO_SKILL;

        /**
         * When it began, in time online.
         */
        private long startedAt;

        /**
         * Where the account stood when it began.
         */
        private Standing start;

        /**
         * The players killed so far.
         */
        private int kills;

        /**
         * The deaths so far.
         */
        private int deaths;

        private Open(String activity) {
            this.activity = activity;
        }
    }

    /**
     * How strongly the bot is put off something that would not start.
     */
    private static final class PutOff {

        /**
         * The strength held at {@link #at}. Each failed start adds to it.
         */
        private double strength;

        /**
         * When {@link #strength} was last set, in real time.
         */
        private long at;
    }

    /**
     * Marks a session that was not about one skill.
     */
    public static final int NO_SKILL = -1;

    /**
     * The real time over which tiredness of an activity or skill halves.
     */
    public static final long BOREDOM_HALF_LIFE = minutes(180);

    /**
     * The recent minutes of one activity that halve an ordinary bot's wish for more of it.
     */
    public static final double ACTIVITY_PATIENCE = 180.0;

    /**
     * The recent minutes of one skill that halve an ordinary bot's wish for more of it.
     */
    public static final double SKILL_PATIENCE = 120.0;

    /**
     * The fewest minutes a session tires the bot by, so a run of short visits still adds up.
     */
    public static final int LEAST_EFFORT = 20;

    /**
     * The time online over which the memory of how a session went halves.
     */
    public static final long RESULTS_HALF_LIFE = minutes(1_440);

    /**
     * How far a run of sessions that all went as well or as badly as they can moves the bot's interest.
     */
    public static final double RESULTS_SWAY = 0.4;

    /**
     * The real time over which being put off by a failed start halves.
     */
    public static final long PUT_OFF_HALF_LIFE = minutes(15);

    /**
     * A session shorter than this that changed nothing never really started.
     */
    private static final long STARTED_NOTHING = minutes(1);

    /**
     * The share of its wealth a bot has to gain or lose in a session for that to count in full.
     */
    private static final double WEALTH_SHARE = 0.10;

    /**
     * Added to a bot's wealth before taking the share, so a new account does not feel every coin.
     */
    private static final long WEALTH_FLOOR = 25_000L;

    /**
     * A put-off weaker than this is gone.
     */
    private static final double FORGOTTEN = 0.02;

    /**
     * How many sessions the least intelligent bot keeps.
     */
    private static final int FEWEST_SESSIONS = 16;

    /**
     * How many more sessions the most intelligent bot keeps.
     */
    private static final int EXTRA_SESSIONS = 16;

    /**
     * The memory this part belongs to.
     */
    private final BotMemory memory;

    /**
     * The finished sessions, oldest first.
     */
    private final Deque<Session> sessions = new ArrayDeque<>();

    /**
     * What the bot is put off, by activity name or {@link #key(int) skill}.
     */
    private final Map<String, PutOff> putOff = new LinkedHashMap<>();

    /**
     * The session the bot is in, or {@code null} between sessions.
     */
    private Open open;

    BotDiaryMemory(BotMemory memory) {
        this.memory = memory;
    }

    /**
     * Opens a session. One that was still open is dropped unrecorded.
     *
     * @param activity The name of the activity the bot chose.
     * @param standing Where its account stands now.
     */
    public void begin(String activity, Standing standing) {
        if (activity == null || activity.isEmpty() || standing == null) {
            return;
        }
        open = new Open(activity);
        open.startedAt = memory.now();
        open.start = standing;
    }

    /**
     * Names the skill the open session is about. The first skill named stays.
     *
     * @param skill The skill's id.
     */
    public void training(int skill) {
        if (open != null && open.skill == NO_SKILL && SkillSet.SKILL_IDS.contains(skill)) {
            open.skill = skill;
        }
    }

    /**
     * Counts a death in the open session.
     */
    public void died() {
        if (open != null) {
            open.deaths++;
        }
    }

    /**
     * Counts a player killed in the open session.
     */
    public void killed() {
        if (open != null) {
            open.kills++;
        }
    }

    /**
     * @return Whether the bot is in a session.
     */
    public boolean isOpen() {
        return open != null;
    }

    /**
     * @return The skill the open session is about, or {@link #NO_SKILL}.
     */
    public int openSkill() {
        return open == null ? NO_SKILL : open.skill;
    }

    /**
     * @param experience The bot's experience in all skills together now.
     * @return How fast the open session has been gaining experience, per minute online, or {@code 0.0}.
     */
    public double openRate(long experience) {
        if (open == null) {
            return 0.0;
        }
        double minutes = Math.max(0, memory.now() - open.startedAt) / 60_000.0;
        long gained = experience - open.start.experience();
        return minutes >= 1.0 && gained > 0 ? gained / minutes : 0.0;
    }

    /**
     * @param skill The skill's id.
     * @return The minutes of the sessions still in the diary that were about the skill.
     */
    public int minutesOn(int skill) {
        int minutes = 0;
        for (Session session : sessions) {
            if (session.skill == skill) {
                minutes += session.minutes;
            }
        }
        return minutes;
    }

    /**
     * Closes the open session. It is kept, unless it was over at once with nothing to show, in which case the bot is
     * put off trying the same thing again for a while.
     *
     * @param standing Where the bot's account stands now.
     * @return The session that closed, or {@code null} if none was open.
     */
    public Closed end(Standing standing) {
        Open ended = open;
        open = null;
        if (ended == null || standing == null) {
            return null;
        }
        long now = memory.now();
        long lasted = Math.max(0, now - ended.startedAt);
        long experience = Math.max(0, standing.experience() - ended.start.experience());
        long wealthChange = standing.wealth() - ended.start.wealth();
        if (lasted < STARTED_NOTHING && experience == 0 && wealthChange == 0 && ended.kills == 0 && ended.deaths == 0) {
            // With a skill named it was the skill that would not start, and the activity may still have others.
            if (ended.skill != NO_SKILL) {
                putOff(key(ended.skill), 1.0);
                putOff(ended.activity, 0.5);
            } else {
                putOff(ended.activity, 1.0);
            }
            return new Closed(ended.activity, ended.skill, false, 0.0);
        }
        Session session = new Session(ended.activity);
        session.skill = ended.skill;
        session.endedAt = now;
        session.endedReal = memory.realTime();
        session.minutes = (int) Math.min(Integer.MAX_VALUE, lasted / 60_000L);
        session.experience = experience;
        session.levels = Math.max(0, standing.levels() - ended.start.levels());
        session.coins = wealthChange;
        session.kills = ended.kills;
        session.deaths = ended.deaths;
        double wealth = wealthShare(wealthChange, ended.start.wealth());
        session.went = went(session, wealth);
        sessions.addLast(session);
        trim();
        return new Closed(ended.activity, ended.skill, true, wealth);
    }

    /**
     * @param activity The activity's name.
     * @param liking How much the bot likes the activity, from {@code 0.0} to {@code 1.0}. What it likes tires it
     * more slowly.
     * @return How much the bot feels like the activity now, as a multiplier on its usual interest: below
     * {@code 1.0} after a lot of it, a bad run or a failed start, above it after a good run.
     */
    public double interestIn(String activity, double liking) {
        double patience = ACTIVITY_PATIENCE * patience() * (0.6 + 0.8 * personal(liking));
        return interest(session -> session.activity.equals(activity), patience, activity);
    }

    /**
     * @param skill The skill's id.
     * @param preferred Whether it is one of the bot's preferred skills, which tire it more slowly.
     * @return How much the bot feels like the skill now, as a multiplier on its usual interest.
     */
    public double interestIn(int skill, boolean preferred) {
        double patience = SKILL_PATIENCE * patience() * (preferred ? 1.5 : 1.0);
        return interest(session -> session.skill == skill, patience, key(skill));
    }

    /**
     * @return How many sessions the bot can keep, by how intelligent it is.
     */
    public int capacity() {
        return FEWEST_SESSIONS + (int) Math.round(EXTRA_SESSIONS * personal(memory.personality().getIntelligence()));
    }

    /**
     * @return How many finished sessions the bot remembers.
     */
    public int size() {
        return sessions.size();
    }

    @Override
    public String key() {
        return "diary";
    }

    @Override
    public JsonElement save() {
        JsonObject data = new JsonObject();
        JsonArray kept = new JsonArray();
        for (Session session : sessions) {
            JsonObject entry = new JsonObject();
            entry.addProperty("activity", session.activity);
            if (session.skill != NO_SKILL) {
                entry.addProperty("skill", session.skill);
            }
            entry.addProperty("ended", session.endedAt);
            entry.addProperty("ended_real", session.endedReal);
            entry.addProperty("minutes", session.minutes);
            addUnlessZero(entry, "xp", session.experience);
            addUnlessZero(entry, "levels", session.levels);
            addUnlessZero(entry, "coins", session.coins);
            addUnlessZero(entry, "kills", session.kills);
            addUnlessZero(entry, "deaths", session.deaths);
            if (session.went != 0.0) {
                entry.addProperty("went", rounded(session.went));
            }
            kept.add(entry);
        }
        data.add("sessions", kept);
        if (open != null) {
            JsonObject entry = new JsonObject();
            entry.addProperty("activity", open.activity);
            if (open.skill != NO_SKILL) {
                entry.addProperty("skill", open.skill);
            }
            entry.addProperty("started", open.startedAt);
            entry.addProperty("xp", open.start.experience());
            entry.addProperty("levels", open.start.levels());
            entry.addProperty("wealth", open.start.wealth());
            addUnlessZero(entry, "kills", open.kills);
            addUnlessZero(entry, "deaths", open.deaths);
            data.add("open", entry);
        }
        long realTime = memory.realTime();
        JsonArray marks = new JsonArray();
        for (Map.Entry<String, PutOff> mark : putOff.entrySet()) {
            double strength = strength(mark.getValue(), realTime);
            if (strength < FORGOTTEN) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("what", mark.getKey());
            entry.addProperty("strength", rounded(strength));
            marks.add(entry);
        }
        if (!marks.isEmpty()) {
            data.addProperty("as_of_real", realTime);
            data.add("put_off", marks);
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
        JsonElement kept = root.get("sessions");
        if (kept != null && kept.isJsonArray()) {
            for (JsonElement element : kept.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String activity = text(entry, "activity");
                if (activity == null || activity.isEmpty()) {
                    continue;
                }
                Session session = new Session(activity);
                session.skill = skill(entry);
                session.endedAt = (long) number(entry, "ended", 0.0);
                session.endedReal = (long) number(entry, "ended_real", 0.0);
                session.minutes = (int) Math.max(0.0, number(entry, "minutes", 0.0));
                session.experience = (long) Math.max(0.0, number(entry, "xp", 0.0));
                session.levels = (int) Math.max(0.0, number(entry, "levels", 0.0));
                session.coins = (long) number(entry, "coins", 0.0);
                session.kills = (int) Math.max(0.0, number(entry, "kills", 0.0));
                session.deaths = (int) Math.max(0.0, number(entry, "deaths", 0.0));
                session.went = Math.clamp(number(entry, "went", 0.0), -1.0, 1.0);
                sessions.addLast(session);
            }
            trim();
        }
        JsonElement saved = root.get("open");
        if (saved != null && saved.isJsonObject()) {
            JsonObject entry = saved.getAsJsonObject();
            String activity = text(entry, "activity");
            if (activity != null && !activity.isEmpty()) {
                open = new Open(activity);
                open.skill = skill(entry);
                open.startedAt = (long) number(entry, "started", memory.now());
                open.start = new Standing((long) number(entry, "xp", 0.0), (int) number(entry, "levels", 0.0),
                        (long) number(entry, "wealth", 0.0));
                open.kills = (int) Math.max(0.0, number(entry, "kills", 0.0));
                open.deaths = (int) Math.max(0.0, number(entry, "deaths", 0.0));
            }
        }
        JsonElement marks = root.get("put_off");
        if (marks != null && marks.isJsonArray()) {
            long asOfReal = (long) number(root, "as_of_real", memory.realTime());
            for (JsonElement element : marks.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String what = text(entry, "what");
                double strength = number(entry, "strength", 0.0);
                if (what == null || what.isEmpty() || strength < FORGOTTEN) {
                    continue;
                }
                PutOff mark = new PutOff();
                mark.strength = strength;
                mark.at = asOfReal;
                putOff.put(what, mark);
            }
        }
    }

    @Override
    public void clear() {
        sessions.clear();
        putOff.clear();
        open = null;
    }

    @Override
    public List<String> describe() {
        long now = memory.now();
        long realTime = memory.realTime();
        List<String> lines = new ArrayList<>();
        lines.add("Diary: " + sessions.size() + " of " + capacity() + " sessions");
        if (open != null) {
            StringBuilder line = new StringBuilder("Now: ").append(named(open.activity, open.skill)).append(" for ")
                    .append(Math.max(0, now - open.startedAt) / 60_000L).append(" min");
            append(line, open.kills, " kill");
            append(line, open.deaths, " death");
            lines.add(line.toString());
        }
        for (Iterator<Session> newestFirst = sessions.descendingIterator(); newestFirst.hasNext(); ) {
            Session session = newestFirst.next();
            StringBuilder line = new StringBuilder(named(session.activity, session.skill)).append(": ")
                    .append(session.minutes).append(" min, ")
                    .append(Math.max(0, realTime - session.endedReal) / 60_000L).append(" min ago");
            if (session.experience > 0) {
                line.append(", ").append(session.experience).append(" xp");
            }
            append(line, session.levels, " level");
            if (session.coins != 0) {
                line.append(", ").append(session.coins > 0 ? "+" : "").append(session.coins).append(" gp");
            }
            append(line, session.kills, " kill");
            append(line, session.deaths, " death");
            line.append(", went ").append(rounded(session.went));
            lines.add(line.toString());
        }
        for (Map.Entry<String, PutOff> mark : putOff.entrySet()) {
            double strength = strength(mark.getValue(), realTime);
            if (strength >= FORGOTTEN) {
                lines.add("Put off " + mark.getKey() + ": " + rounded(strength));
            }
        }
        return lines;
    }

    /**
     * @return The multiplier for the sessions that match, from tiredness, results and failed starts together.
     */
    private double interest(Predicate<Session> same, double patience, String key) {
        long now = memory.now();
        long realTime = memory.realTime();
        double tired = 0.0;
        double weighed = 0.0;
        double result = 0.0;
        for (Session session : sessions) {
            if (!same.test(session)) {
                continue;
            }
            tired += Math.max(session.minutes, LEAST_EFFORT) * faded(realTime - session.endedReal, BOREDOM_HALF_LIFE);
            double weight = faded(now - session.endedAt, RESULTS_HALF_LIFE);
            weighed += weight;
            result += weight * session.went;
        }
        double bored = 1.0 / (1.0 + tired / patience);
        // The extra one in the divisor holds the average near neutral until several sessions agree.
        double results = 1.0 + RESULTS_SWAY * result / (1.0 + weighed);
        PutOff mark = putOff.get(key);
        double willing = mark == null ? 1.0 : Math.pow(0.5, strength(mark, realTime));
        return bored * results * willing;
    }

    /**
     * Scores a session by what it changed for the bot.
     *
     * @param session The session, with everything but the score set.
     * @param wealth How the session changed the bot's wealth, from {@link #wealthShare}.
     * @return The score, from {@code -1.0} to {@code 1.0}.
     */
    private static double went(Session session, double wealth) {
        double went = 0.25 * Math.min(2, session.levels) + 0.3 * Math.min(2, session.kills) -
                0.4 * Math.min(2, session.deaths);
        went += 0.3 * wealth;
        return Math.clamp(went, -1.0, 1.0);
    }

    /**
     * @param change How much the bot's wealth changed, in coins.
     * @param wealthBefore What the bot owned before.
     * @return The change as a share of what counts in full, from {@code -1.0} to {@code 1.0}.
     */
    private static double wealthShare(long change, long wealthBefore) {
        return Math.clamp(change / (WEALTH_SHARE * (Math.max(0, wealthBefore) + WEALTH_FLOOR)), -1.0, 1.0);
    }

    /**
     * Adds to how strongly the bot is put off something.
     */
    private void putOff(String key, double strength) {
        long realTime = memory.realTime();
        putOff.values().removeIf(mark -> strength(mark, realTime) < FORGOTTEN);
        PutOff mark = putOff.computeIfAbsent(key, unused -> new PutOff());
        mark.strength = strength(mark, realTime) + strength;
        mark.at = realTime;
    }

    /**
     * Drops the oldest sessions beyond what the bot can keep.
     */
    private void trim() {
        int capacity = capacity();
        while (sessions.size() > capacity) {
            sessions.removeFirst();
        }
    }

    /**
     * @return How slowly this kind of bot tires of anything: a kid in half the time, a no-lifer in twice.
     */
    private double patience() {
        PersonalityTemplateType type = memory.personality().getType();
        if (type == PersonalityTemplateType.KID) {
            return 0.5;
        }
        return type == PersonalityTemplateType.NO_LIFE ? 2.0 : 1.0;
    }

    /**
     * @return A put-off's strength after halving once for every half-life of real time since it was set.
     */
    private static double strength(PutOff mark, long realTime) {
        return mark.strength <= 0.0 ? 0.0 : mark.strength * faded(realTime - mark.at, PUT_OFF_HALF_LIFE);
    }

    /**
     * @return What is left of one after {@code age}, halving once every {@code halfLife}.
     */
    private static double faded(long age, long halfLife) {
        return Math.pow(0.5, Math.max(0, age) / (double) halfLife);
    }

    private static String key(int skill) {
        return "skill:" + skill;
    }

    private static String named(String activity, int skill) {
        return skill == NO_SKILL ? activity : activity + " (" + Skill.getName(skill) + ")";
    }

    private static void append(StringBuilder line, int count, String what) {
        if (count > 0) {
            line.append(", ").append(count).append(what).append(count == 1 ? "" : "s");
        }
    }

    private static void addUnlessZero(JsonObject entry, String key, long value) {
        if (value != 0) {
            entry.addProperty(key, value);
        }
    }

    private static int skill(JsonObject entry) {
        int skill = (int) number(entry, "skill", NO_SKILL);
        return SkillSet.SKILL_IDS.contains(skill) ? skill : NO_SKILL;
    }

    /**
     * @return A personality trait or liking from {@code 0.0} to {@code 1.0}, or the middle if it is not a number.
     */
    private static double personal(double trait) {
        return Double.isFinite(trait) ? Math.clamp(trait, 0.0, 1.0) : 0.5;
    }

    private static double rounded(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private static long minutes(long minutes) {
        return minutes * 60_000L;
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
