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
import java.util.List;

/**
 * What a bot is working toward: one long ambition and one nearer aim, plus the aims it has reached or given up.
 * <p>
 * The ambition is what the account is for: a skill to 99, a finished PK build, a combat level or a total level. The aim
 * is the next level the bot is going for in one skill, usually on the way to the ambition. The aim pulls the bot toward
 * the work that trains it, more strongly as it gets close and more strongly for a driven kind of player, while the
 * diary still lets it tire of that work. A bot that reaches its aim moves on to another; one that stalls gives up on it
 * and leaves that skill alone for a while.
 * <p>
 * This part holds the goals and the arithmetic. What counts as a sensible target is decided by the game code that sets
 * them. Everything here fades by the bot's time online.
 *
 * @author TheLining
 */
public final class BotGoalsMemory implements BotMemoryPart {

    /**
     * The kinds of ambition.
     */
    public enum Kind {

        /**
         * One skill to its highest level.
         */
        SKILL,

        /**
         * Every level the bot's PK build calls for.
         */
        BUILD,

        /**
         * A combat level.
         */
        COMBAT,

        /**
         * A total level.
         */
        TOTAL
    }

    /**
     * The ambition.
     */
    private static final class Ambition {

        /**
         * What kind of ambition it is.
         */
        private final Kind kind;

        /**
         * The skill, for {@link Kind#SKILL}, otherwise {@link #NO_SKILL}.
         */
        private final int skill;

        /**
         * The level aimed for. A build has none of its own.
         */
        private final int target;

        /**
         * When it was set, in time online.
         */
        private long setAt;

        private Ambition(Kind kind, int skill, int target) {
            this.kind = kind;
            this.skill = skill;
            this.target = target;
        }
    }

    /**
     * The aim.
     */
    private static final class Aim {

        /**
         * The skill.
         */
        private final int skill;

        /**
         * The level aimed for.
         */
        private final int target;

        /**
         * What the level unlocks, as a player would put it, or {@code null}.
         */
        private final String reason;

        /**
         * The level when it was set.
         */
        private int fromLevel;

        /**
         * The experience when it was set.
         */
        private double fromExperience;

        /**
         * When it was set, in time online.
         */
        private long setAt;

        /**
         * When the bot last worked on it or gained a level in it, in time online.
         */
        private long workedAt;

        /**
         * The experience when the bot last worked on it.
         */
        private double lastExperience;

        /**
         * The sessions spent on it.
         */
        private int sessions;

        /**
         * The sessions in a row that gained it nothing.
         */
        private int idle;

        /**
         * The times work on it would not start.
         */
        private int failed;

        private Aim(int skill, int target, String reason) {
            this.skill = skill;
            this.target = target;
            this.reason = reason;
        }
    }

    /**
     * A goal met.
     *
     * @param subject The skill, {@link #COMBAT_LEVEL} or {@link #TOTAL_LEVEL}.
     * @param level The level reached.
     * @param at When, in time online.
     * @param atReal When, in real time.
     */
    private record Reached(int subject, int level, long at, long atReal) {
    }

    /**
     * An aim given up.
     *
     * @param skill The skill.
     * @param at When, in time online.
     */
    private record Dropped(int skill, long at) {
    }

    /**
     * Marks the lack of a skill.
     */
    public static final int NO_SKILL = -1;

    /**
     * The subject of a combat level reached.
     */
    public static final int COMBAT_LEVEL = -2;

    /**
     * The subject of a total level reached.
     */
    public static final int TOTAL_LEVEL = -3;

    /**
     * The activity that trains skills.
     */
    public static final String TRAINING_SKILLS = "TRAINING_SKILLS";

    /**
     * The activity that trains combat.
     */
    public static final String TRAINING_COMBAT = "TRAINING_COMBAT";

    /**
     * How long of the bot's time online a skill it gave up on is left alone.
     */
    public static final long DROPPED_FOR = hours(24);

    /**
     * How long of the bot's time online an aim may go without work before the bot loses interest in it.
     */
    public static final long NEGLECTED_AFTER = hours(72);

    /**
     * How many times work on an aim may fail to start before the bot gives up on it.
     */
    public static final int MOST_FAILED_STARTS = 3;

    /**
     * How many sessions in a row may gain an aim nothing before the bot gives up on it.
     */
    public static final int MOST_IDLE_SESSIONS = 3;

    /**
     * The most minutes a session runs past its time to reach the aim.
     */
    public static final double MOST_RUN_ON_MINUTES = 30.0;

    /**
     * The levels left at which an aim is close.
     */
    public static final int CLOSE = 3;

    /**
     * The share of the skill roll an aim never goes past.
     */
    private static final double MOST_SHARE = 0.75;

    /**
     * How many goals met are kept.
     */
    private static final int MOST_REACHED = 8;

    /**
     * How many aims given up are kept.
     */
    private static final int MOST_DROPPED = 4;

    /**
     * The memory this part belongs to.
     */
    private final BotMemory memory;

    /**
     * Goals met, oldest first.
     */
    private final Deque<Reached> reached = new ArrayDeque<>();

    /**
     * Aims given up, oldest first.
     */
    private final Deque<Dropped> dropped = new ArrayDeque<>();

    /**
     * The ambition, or {@code null}.
     */
    private Ambition ambition;

    /**
     * The aim, or {@code null}.
     */
    private Aim aim;

    BotGoalsMemory(BotMemory memory) {
        this.memory = memory;
    }

    /**
     * Sets the ambition, replacing any other.
     *
     * @param kind What kind of ambition.
     * @param skill The skill, for {@link Kind#SKILL}.
     * @param target The level aimed for, or {@code 0} for a build.
     */
    public void setAmbition(Kind kind, int skill, int target) {
        if (kind == null || kind == Kind.SKILL && !isSkill(skill)) {
            return;
        }
        ambition = new Ambition(kind, kind == Kind.SKILL ? skill : NO_SKILL, Math.max(0, target));
        ambition.setAt = memory.now();
    }

    /**
     * Records the ambition as met and clears it.
     *
     * @param subject The skill, {@link #COMBAT_LEVEL} or {@link #TOTAL_LEVEL}.
     * @param level The level reached.
     */
    public void ambitionReached(int subject, int level) {
        if (ambition != null) {
            ambition = null;
            record(subject, level);
        }
    }

    /**
     * Forgets the ambition without counting it as met, when it no longer fits the account.
     */
    public void clearAmbition() {
        ambition = null;
    }

    /**
     * @return Whether the bot has an ambition.
     */
    public boolean hasAmbition() {
        return ambition != null;
    }

    /**
     * @return The kind of ambition, or {@code null} without one.
     */
    public Kind ambitionKind() {
        return ambition == null ? null : ambition.kind;
    }

    /**
     * @return The ambition's skill, or {@link #NO_SKILL}.
     */
    public int ambitionSkill() {
        return ambition == null ? NO_SKILL : ambition.skill;
    }

    /**
     * @return The level the ambition is for, or {@code 0}.
     */
    public int ambitionTarget() {
        return ambition == null ? 0 : ambition.target;
    }

    /**
     * Sets the aim, replacing any other.
     *
     * @param skill The skill.
     * @param target The level aimed for, above the current one.
     * @param reason What that level unlocks, or {@code null}.
     * @param level The current level.
     */
    public void setAim(int skill, int target, String reason, int level) {
        if (!isSkill(skill) || level < 1 || target <= level || target > 99) {
            return;
        }
        aim = new Aim(skill, target, reason == null || reason.isBlank() ? null : reason);
        aim.fromLevel = level;
        aim.fromExperience = memory.experience(skill);
        aim.lastExperience = aim.fromExperience;
        aim.setAt = memory.now();
        aim.workedAt = aim.setAt;
    }

    /**
     * Records the aim as met and clears it.
     */
    public void aimReached() {
        if (aim != null) {
            record(aim.skill, aim.target);
            aim = null;
        }
    }

    /**
     * Gives up on the aim. Its skill is left alone for {@link #DROPPED_FOR}.
     */
    public void dropAim() {
        if (aim == null) {
            return;
        }
        dropped.addLast(new Dropped(aim.skill, memory.now()));
        while (dropped.size() > MOST_DROPPED) {
            dropped.removeFirst();
        }
        aim = null;
    }

    /**
     * @return Whether the bot has an aim.
     */
    public boolean hasAim() {
        return aim != null;
    }

    /**
     * @return The aim's skill, or {@link #NO_SKILL}.
     */
    public int aimSkill() {
        return aim == null ? NO_SKILL : aim.skill;
    }

    /**
     * @return The level aimed for, or {@code 0}.
     */
    public int aimTarget() {
        return aim == null ? 0 : aim.target;
    }

    /**
     * @return What the aim unlocks, or {@code null}.
     */
    public String aimReason() {
        return aim == null ? null : aim.reason;
    }

    /**
     * @param skill The skill.
     * @return Whether the bot gave up on an aim in this skill too recently to pick it again.
     */
    public boolean recentlyDropped(int skill) {
        long now = memory.now();
        for (Dropped mark : dropped) {
            if (mark.skill == skill && now - mark.at < DROPPED_FOR) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return How far the bot has come from where it set its aim to the aim, by experience, from {@code 0.0} to
     * {@code 1.0}, or {@code 0.0} without one.
     */
    public double progress() {
        if (aim == null) {
            return 0.0;
        }
        double needed = SkillSet.experienceForLevel(aim.target) - aim.fromExperience;
        if (needed <= 0.0) {
            return 1.0;
        }
        return Math.clamp((memory.experience(aim.skill) - aim.fromExperience) / needed, 0.0, 1.0);
    }

    /**
     * Notes a level gained, which counts as work on the aim if it is in the aim's skill.
     *
     * @param skill The skill.
     */
    public void levelled(int skill) {
        if (aim != null && aim.skill == skill) {
            aim.workedAt = memory.now();
            aim.idle = 0;
        }
    }

    /**
     * Notes the end of a session that was about one skill.
     *
     * @param skill The skill.
     * @param started Whether the work started at all.
     */
    public void sessionEnded(int skill, boolean started) {
        if (aim == null || aim.skill != skill) {
            return;
        }
        if (!started) {
            aim.failed++;
            return;
        }
        double experience = memory.experience(skill);
        aim.sessions++;
        aim.idle = experience > aim.lastExperience ? 0 : aim.idle + 1;
        aim.lastExperience = experience;
        aim.workedAt = memory.now();
    }

    /**
     * @return Whether the bot should give up on its aim: work on it keeps failing to start, keeps gaining nothing, or
     * has not happened for {@link #NEGLECTED_AFTER}.
     */
    public boolean stalled() {
        return aim != null && (aim.failed >= MOST_FAILED_STARTS || aim.idle >= MOST_IDLE_SESSIONS ||
                memory.now() - aim.workedAt >= NEGLECTED_AFTER);
    }

    /**
     * @return How much this kind of player wants to get things done, from {@code 0.0} to {@code 1.0}.
     */
    public double drive() {
        PersonalityTemplateType type = memory.personality().getType();
        return switch (type == null ? PersonalityTemplateType.NONE : type) {
            case NO_LIFE -> 0.9;
            case SKILLER -> 0.85;
            case PKER -> 0.8;
            case HERO -> 0.65;
            case JACK_OF_ALL_TRADES -> 0.55;
            case KID -> 0.45;
            case MERCHANT -> 0.35;
            case SCAMMER -> 0.3;
            case NONE -> 0.5;
        };
    }

    /**
     * @return The chance the bot keeps to its plans: that an aim serves its ambition, or that it trains the style its
     * aim needs. Driven and intelligent bots keep to them more.
     */
    public double focus() {
        return Math.clamp(drive() + 0.2 * (personal(memory.personality().getIntelligence()) - 0.5), 0.2, 0.95);
    }

    /**
     * @return The fewest levels between where the bot is and what it aims for: two for a kid, three to seven for the
     * rest by intelligence.
     */
    public int gap() {
        if (memory.personality().getType() == PersonalityTemplateType.KID) {
            return 2;
        }
        return 3 + (int) Math.round(4.0 * personal(memory.personality().getIntelligence()));
    }

    /**
     * @param activity The activity's name.
     * @return The multiplier the aim puts on the bot's interest in the activity: above {@code 1.0} for the activity
     * that trains the aim, rising as the aim gets close.
     */
    public double interestIn(String activity) {
        if (aim == null || activity == null || !trains(activity, aim.skill)) {
            return 1.0;
        }
        return 1.0 + drive() * (0.5 + 0.5 * progress());
    }

    /**
     * @return The share of the skill roll the aim's skill gets for a bot that isn't tired of it, rising as the aim
     * gets close, or {@code 0.0} without an aim.
     */
    public double share() {
        return aim == null ? 0.0 : drive() * (0.35 + 0.25 * progress());
    }

    /**
     * Gives the aim's skill its share of the skill roll.
     *
     * @param ownWeight The skill's weight before the aim.
     * @param othersWeight The weights of every other skill in the roll together.
     * @param interest What the bot's other memory says about the skill, as a multiplier. Tiredness lowers the share.
     * @return The skill's weight with the aim: its own weight, or more if that falls short of the share.
     */
    public double aimWeight(double ownWeight, double othersWeight, double interest) {
        if (aim == null || !(othersWeight > 0.0) || !Double.isFinite(interest)) {
            return ownWeight;
        }
        double share = Math.clamp(share() * Math.clamp(interest, 0.0, 1.25), 0.0, MOST_SHARE);
        return Math.max(ownWeight, othersWeight * share / (1.0 - share));
    }

    /**
     * @param skill The skill a session is training.
     * @param experiencePerMinute How fast the session has been gaining experience, per minute online.
     * @return How many more minutes the session needs to reach the aim, or a negative number if the session is not
     * about the aim, isn't gaining, or would need more than {@link #MOST_RUN_ON_MINUTES}.
     */
    public double runOnMinutes(int skill, double experiencePerMinute) {
        if (aim == null || aim.skill != skill || !(experiencePerMinute > 0.0)) {
            return -1.0;
        }
        double minutes = (SkillSet.experienceForLevel(aim.target) - memory.experience(skill)) / experiencePerMinute;
        return minutes > 0.0 && minutes <= MOST_RUN_ON_MINUTES ? minutes : -1.0;
    }

    /**
     * @return How many goals the bot has met and still remembers.
     */
    public int reachedCount() {
        return reached.size();
    }

    @Override
    public String key() {
        return "goals";
    }

    @Override
    public JsonElement save() {
        JsonObject data = new JsonObject();
        if (ambition != null) {
            JsonObject entry = new JsonObject();
            entry.addProperty("kind", ambition.kind.name());
            if (ambition.skill != NO_SKILL) {
                entry.addProperty("skill", ambition.skill);
            }
            if (ambition.target > 0) {
                entry.addProperty("target", ambition.target);
            }
            entry.addProperty("set", ambition.setAt);
            data.add("ambition", entry);
        }
        if (aim != null) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", aim.skill);
            entry.addProperty("target", aim.target);
            if (aim.reason != null) {
                entry.addProperty("reason", aim.reason);
            }
            entry.addProperty("from_level", aim.fromLevel);
            entry.addProperty("from_xp", Math.round(aim.fromExperience));
            entry.addProperty("set", aim.setAt);
            entry.addProperty("worked", aim.workedAt);
            entry.addProperty("last_xp", Math.round(aim.lastExperience));
            addUnlessZero(entry, "sessions", aim.sessions);
            addUnlessZero(entry, "idle", aim.idle);
            addUnlessZero(entry, "failed", aim.failed);
            data.add("aim", entry);
        }
        if (!reached.isEmpty()) {
            JsonArray marks = new JsonArray();
            for (Reached mark : reached) {
                JsonObject entry = new JsonObject();
                entry.addProperty("subject", mark.subject);
                entry.addProperty("level", mark.level);
                entry.addProperty("at", mark.at);
                entry.addProperty("at_real", mark.atReal);
                marks.add(entry);
            }
            data.add("reached", marks);
        }
        long now = memory.now();
        JsonArray marks = new JsonArray();
        for (Dropped mark : dropped) {
            if (now - mark.at < DROPPED_FOR) {
                JsonObject entry = new JsonObject();
                entry.addProperty("skill", mark.skill);
                entry.addProperty("at", mark.at);
                marks.add(entry);
            }
        }
        if (!marks.isEmpty()) {
            data.add("dropped", marks);
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
        JsonObject savedAmbition = object(root, "ambition");
        if (savedAmbition != null) {
            Kind kind = kind(text(savedAmbition, "kind"));
            int skill = (int) number(savedAmbition, "skill", NO_SKILL);
            int target = (int) number(savedAmbition, "target", 0.0);
            if (kind != null && (kind != Kind.SKILL || isSkill(skill)) && target >= 0) {
                ambition = new Ambition(kind, kind == Kind.SKILL ? skill : NO_SKILL, target);
                ambition.setAt = (long) number(savedAmbition, "set", memory.now());
            }
        }
        JsonObject savedAim = object(root, "aim");
        if (savedAim != null) {
            int skill = (int) number(savedAim, "skill", NO_SKILL);
            int target = (int) number(savedAim, "target", 0.0);
            int fromLevel = (int) number(savedAim, "from_level", 0.0);
            if (isSkill(skill) && fromLevel >= 1 && target > fromLevel && target <= 99) {
                aim = new Aim(skill, target, text(savedAim, "reason"));
                aim.fromLevel = fromLevel;
                aim.fromExperience = Math.max(0.0, number(savedAim, "from_xp", 0.0));
                aim.setAt = (long) number(savedAim, "set", memory.now());
                aim.workedAt = (long) number(savedAim, "worked", aim.setAt);
                aim.lastExperience = Math.max(aim.fromExperience, number(savedAim, "last_xp", aim.fromExperience));
                aim.sessions = (int) Math.max(0.0, number(savedAim, "sessions", 0.0));
                aim.idle = (int) Math.max(0.0, number(savedAim, "idle", 0.0));
                aim.failed = (int) Math.max(0.0, number(savedAim, "failed", 0.0));
            }
        }
        JsonElement savedReached = root.get("reached");
        if (savedReached != null && savedReached.isJsonArray()) {
            for (JsonElement element : savedReached.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                int subject = (int) number(entry, "subject", NO_SKILL);
                int level = (int) number(entry, "level", 0.0);
                if ((isSkill(subject) || subject == COMBAT_LEVEL || subject == TOTAL_LEVEL) && level > 0) {
                    reached.addLast(new Reached(subject, level, (long) number(entry, "at", 0.0),
                            (long) number(entry, "at_real", 0.0)));
                }
            }
            while (reached.size() > MOST_REACHED) {
                reached.removeFirst();
            }
        }
        JsonElement savedDropped = root.get("dropped");
        if (savedDropped != null && savedDropped.isJsonArray()) {
            for (JsonElement element : savedDropped.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                int skill = (int) number(entry, "skill", NO_SKILL);
                if (isSkill(skill)) {
                    dropped.addLast(new Dropped(skill, (long) number(entry, "at", 0.0)));
                }
            }
            while (dropped.size() > MOST_DROPPED) {
                dropped.removeFirst();
            }
        }
    }

    @Override
    public void clear() {
        ambition = null;
        aim = null;
        reached.clear();
        dropped.clear();
    }

    @Override
    public List<String> describe() {
        long now = memory.now();
        List<String> lines = new ArrayList<>();
        if (ambition == null) {
            lines.add("Ambition: none");
        } else {
            lines.add("Ambition: " + switch (ambition.kind) {
                case SKILL -> ambition.target + " " + Skill.getName(ambition.skill);
                case BUILD -> "finish the PK build";
                case COMBAT -> ambition.target + " combat";
                case TOTAL -> ambition.target + " total";
            } + ", set " + Math.max(0, now - ambition.setAt) / 3_600_000L + " h ago");
        }
        if (aim == null) {
            lines.add("Aim: none");
        } else {
            StringBuilder line = new StringBuilder("Aim: ").append(aim.target).append(' ')
                    .append(Skill.getName(aim.skill));
            if (aim.reason != null) {
                line.append(" (").append(aim.reason).append(')');
            }
            line.append(", from ").append(aim.fromLevel).append(", ")
                    .append(Math.round(progress() * 100.0)).append("% there, set ")
                    .append(Math.max(0, now - aim.setAt) / 3_600_000L).append(" h ago, ")
                    .append(aim.sessions).append(aim.sessions == 1 ? " session" : " sessions");
            if (aim.idle > 0) {
                line.append(", ").append(aim.idle).append(" without gain");
            }
            if (aim.failed > 0) {
                line.append(", ").append(aim.failed).append(" failed starts");
            }
            lines.add(line.toString());
        }
        for (Iterator<Reached> newestFirst = reached.descendingIterator(); newestFirst.hasNext(); ) {
            Reached mark = newestFirst.next();
            lines.add("Reached " + mark.level + " " + subject(mark.subject) + ", " +
                    Math.max(0, now - mark.at) / 3_600_000L + " h ago");
        }
        for (Dropped mark : dropped) {
            if (now - mark.at < DROPPED_FOR) {
                lines.add("Gave up on " + Skill.getName(mark.skill) + ", " + Math.max(0, now - mark.at) / 3_600_000L +
                        " h ago");
            }
        }
        return lines;
    }

    /**
     * @param activity The activity's name.
     * @param skill The skill.
     * @return Whether the activity trains the skill. Prayer is trained by skilling, Magic by both.
     */
    static boolean trains(String activity, int skill) {
        if (skill == Skill.MAGIC) {
            return activity.equals(TRAINING_SKILLS) || activity.equals(TRAINING_COMBAT);
        }
        boolean combat = Skill.isCombatSkill(skill) && skill != Skill.PRAYER;
        return activity.equals(combat ? TRAINING_COMBAT : TRAINING_SKILLS);
    }

    private void record(int subject, int level) {
        reached.addLast(new Reached(subject, level, memory.now(), memory.realTime()));
        while (reached.size() > MOST_REACHED) {
            reached.removeFirst();
        }
    }

    private static String subject(int subject) {
        return switch (subject) {
            case COMBAT_LEVEL -> "combat";
            case TOTAL_LEVEL -> "total";
            default -> Skill.getName(subject);
        };
    }

    private static boolean isSkill(int skill) {
        return SkillSet.SKILL_IDS.contains(skill);
    }

    private static Kind kind(String name) {
        if (name == null) {
            return null;
        }
        for (Kind kind : Kind.values()) {
            if (kind.name().equals(name)) {
                return kind;
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

    private static long hours(long hours) {
        return hours * 3_600_000L;
    }

    private static void addUnlessZero(JsonObject entry, String key, int value) {
        if (value != 0) {
            entry.addProperty(key, value);
        }
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
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
