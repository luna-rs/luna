package io.luna.game.model.mob.bot.brain;

import com.google.gson.JsonObject;

/**
 * Persists a new account's one-off scavenging allowance and its optional PK detour cooldown.
 * The playtime checkpoint is deliberately transient: offline time and a restored session's first tick
 * cannot consume an allowance. Completed accounts never receive another introductory allowance.
 *
 * @author lare96
 */
public final class BotScavengingPreference {
    /**
     * Maximum introductory allowance, measured in online milliseconds.
     */
    public static final long MAX_MILLIS = 10L * 60 * 60 * 1000;

    /**
     * Online time still allocated to the introductory phase.
     */
    private long remainingMillis;
    /**
     * Earliest wall-clock time at which another optional PK detour may be considered.
     */
    private long detourAfter;
    /**
     * Native online-playtime checkpoint; a freshly loaded profile starts without one.
     */
    private transient long playtimeCheckpoint = -1;

    /**
     * Creates a bounded allowance. Zero represents an ordinary or completed account.
     */
    public BotScavengingPreference(long remainingMillis) {
        this.remainingMillis = Math.clamp(remainingMillis, 0, MAX_MILLIS);
    }

    /**
     * Whether introductory scavenging still takes precedence over normal activity selection.
     */
    public boolean isIntroductory() {
        return remainingMillis > 0;
    }

    /**
     * Accounts for online playtime once per bot tick, independently of script pauses and banking.
     * A backwards checkpoint is rebased without adding or removing time.
     */
    public void advance(long onlineMillis) {
        if (playtimeCheckpoint >= 0 && onlineMillis >= playtimeCheckpoint) {
            remainingMillis = Math.max(0, remainingMillis - (onlineMillis - playtimeCheckpoint));
        }
        playtimeCheckpoint = onlineMillis;
    }

    /**
     * Ends an introductory phase if the account no longer has level-three combat stats.
     * Levels and the permanent PK identity are never reset or replaced.
     */
    public void completeIntroduction() {
        remainingMillis = 0;
    }

    /**
     * Starts a PK detour cooldown before dispatch, including failed or interrupted detours.
     */
    public boolean tryDetour(long now, long cooldownMillis) {
        if (now < detourAfter) return false;
        detourAfter = now + Math.max(0, cooldownMillis);
        return true;
    }

    /**
     * Saves only durable progress; native player playtime remains the clock authority.
     */
    public JsonObject save() {
        JsonObject data = new JsonObject();
        data.addProperty("remainingMillis", remainingMillis);
        data.addProperty("detourAfter", detourAfter);
        return data;
    }

    /**
     * Restores bounded progress without generating a new allowance for a legacy account.
     */
    public static BotScavengingPreference load(JsonObject data) {
        BotScavengingPreference preference = new BotScavengingPreference(
                data.has("remainingMillis") ? data.get("remainingMillis").getAsLong() : 0);
        preference.detourAfter = data.has("detourAfter") ? Math.max(0, data.get("detourAfter").getAsLong()) : 0;
        return preference;
    }
}
