package io.luna.game.model.mob.bot.brain;

import com.google.gson.JsonObject;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.bot.Bot;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Permanent PK identity, separate from personality and the equipment available for a session.
 * Targets guide training and cap native XP awards without granting levels or removing existing XP.
 * Existing accounts are assigned only builds compatible with their actual levels.
 *
 * @author lare96
 */
public record BotPkPreference(Purpose purpose, Styles styles, Build build, int teamCape) {

    /**
     * Assigns one of the fifty native team capes once when a CLAN identity is created or migrated.
     * Other purposes have no clan assignment. Explicit assignments use the four-argument constructor.
     */
    public BotPkPreference(Purpose purpose, Styles styles, Build build) {
        this(purpose, styles, build, purpose == Purpose.CLAN ? 4315 + 2 * ThreadLocalRandom.current().nextInt(50) : -1);
    }

    /**
     * The motivation for seeking Wilderness fights; targeting rules belong to the PK coordinator.
     *
     * @author lare96
     */
    public enum Purpose { SKILL, VALUE, CLAN, GENERIC }

    /**
     * All seven permanent combinations of offensive disciplines.
     *
     * @author lare96
     */
    public enum Styles {
        MELEE(true, false, false), RANGED(false, true, false), MAGIC(false, false, true),
        MELEE_RANGED(true, true, false), MELEE_MAGIC(true, false, true),
        RANGED_MAGIC(false, true, true), TRIBRID(true, true, true);

        private final boolean melee;
        private final boolean ranged;
        private final boolean magic;

        Styles(boolean melee, boolean ranged, boolean magic) {
            this.melee = melee;
            this.ranged = ranged;
            this.magic = magic;
        }

        /**
         * Tests whether this permanent combination includes the requested offensive discipline.
         */
        public boolean uses(int skill) {
            return switch (skill) {
                case Skill.ATTACK, Skill.STRENGTH -> melee;
                case Skill.RANGED -> ranged;
                case Skill.MAGIC -> magic;
                default -> false;
            };
        }
    }

    /**
     * Revision-377 account builds. Fixed variants make training targets stable across sessions.
     * Ranged and Magic may be trained for utility even when not selected as a PK attack style.
     * Equipment selection must still check native item requirements.
     *
     * @author lare96
     */
    public enum Build {
        RUNE_DUELIST(40, 99, 1, 31),
        OBBY_MAULER(1, 99, 1, 31),
        GMAUL_PURE(50, 99, 1, 31),
        DRAGON_PURE(60, 99, 1, 52),
        RANGER_PURE(1, 1, 1, 52),
        ELEMENTAL_MAGE(1, 1, 1, 52),
        INITIATE_PK(60, 99, 20, 52),
        RUNE_HYBRID(60, 99, 40, 52),
        BERSERKER(60, 99, 45, 52),
        RANGED_TANK(70, 70, 99, 52),
        BARROWS_HYBRID(70, 99, 70, 70),
        MAIN(99, 99, 99, 99);

        private final int attack;
        private final int strength;
        private final int defence;
        private final int prayer;

        Build(int attack, int strength, int defence, int prayer) {
            this.attack = attack;
            this.strength = strength;
            this.defence = defence;
            this.prayer = prayer;
        }

        /**
         * Returns the desired static level; non-combat skills are unrestricted by this build.
         */
        public int targetLevel(int skill) {
            return switch (skill) {
                case Skill.ATTACK -> attack;
                case Skill.STRENGTH -> strength;
                case Skill.DEFENCE -> defence;
                case Skill.PRAYER -> prayer;
                case Skill.RANGED -> this == ELEMENTAL_MAGE ? 1 : 99;
                default -> 99;
            };
        }

        /**
         * Checks offensive compatibility without requiring an already trained account.
         */
        public boolean supports(Styles styles) {
            return (!styles.melee || strength > 1) &&
                    (!styles.ranged || this != ELEMENTAL_MAGE);
        }

        /**
         * Rejects builds an existing account has already exceeded, without altering its stats.
         */
        public boolean fits(Bot bot) {
            return java.util.stream.IntStream.rangeClosed(0, 6).allMatch(id ->
                    bot.skill(id).getStaticLevel() <= targetLevel(id));
        }
    }

    public BotPkPreference {
        Objects.requireNonNull(purpose);
        Objects.requireNonNull(styles);
        Objects.requireNonNull(build);
        if (!build.supports(styles)) {
            throw new IllegalArgumentException("PK build does not support selected styles: " + build + "/" + styles);
        }
        if (purpose == Purpose.CLAN ? !isTeamCape(teamCape) : teamCape != -1) {
            throw new IllegalArgumentException("Invalid team cape for PK purpose: " + purpose + "/" + teamCape);
        }
    }

    /**
     * Recognizes unnoted native Team-1 through Team-50 capes; noted items cannot identify a worn clan.
     */
    public static boolean isTeamCape(int id) {
        return id >= 4315 && id <= 4413 && (id - 4315) % 2 == 0;
    }

    /**
     * Assigns an identity once. A supplied account restricts migration to compatible builds.
     */
    public static BotPkPreference generate(Bot bot) {
        List<Build> builds = Arrays.stream(Build.values()).filter(it -> bot == null || it.fits(bot)).toList();
        Build build = builds.get(ThreadLocalRandom.current().nextInt(builds.size()));
        List<Styles> styles = Arrays.stream(Styles.values()).filter(build::supports).toList();
        Purpose[] purposes = Purpose.values();
        return new BotPkPreference(purposes[ThreadLocalRandom.current().nextInt(purposes.length)],
                styles.get(ThreadLocalRandom.current().nextInt(styles.size())), build);
    }

    /**
     * Writes explicit enum names rather than serializing implementation fields.
     */
    public JsonObject save() {
        JsonObject object = new JsonObject();
        object.addProperty("purpose", purpose.name());
        object.addProperty("styles", styles.name());
        object.addProperty("build", build.name());
        object.addProperty("teamCape", teamCape);
        return object;
    }

    /**
     * Restores the exact identity. Invalid data fails visibly rather than silently rerolling a build.
     */
    public static BotPkPreference load(JsonObject object) {
        Purpose purpose = Purpose.valueOf(object.get("purpose").getAsString());
        Styles styles = Styles.valueOf(object.get("styles").getAsString());
        Build build = Build.valueOf(object.get("build").getAsString());
        return object.has("teamCape") ? new BotPkPreference(purpose, styles, build, object.get("teamCape").getAsInt()) :
                new BotPkPreference(purpose, styles, build);
    }
}
