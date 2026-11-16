package io.luna.game.model.mob.bot.brain;

import com.google.gson.JsonObject;

/**
 * Persists a new account's introductory rune-running allowance and optional profit-session cooldown.
 * Native online playtime owns the allowance; restored profiles begin without a checkpoint so offline time
 * cannot consume it. Legacy accounts receive no introductory allowance.
 *
 * @author lare96
 */
public final class BotRuneRunningPreference {
    /**
     * Maximum introductory allowance, measured in online milliseconds.
     */
    public static final long MAX_MILLIS = 3L * 60 * 60 * 1000;

    /**
     * Online time still allocated to introductory rune running.
     */
    private long remainingMillis;
    /**
     * Earliest wall-clock time at which another optional profit session may be considered.
     */
    private long sessionAfter;
    /**
     * Native online-playtime checkpoint; a loaded profile starts without one.
     */
    private transient long playtimeCheckpoint = -1;

    /**
     * Creates a bounded allowance. Zero represents an ordinary or completed account.
     */
    public BotRuneRunningPreference(long remainingMillis) {
        this.remainingMillis = Math.clamp(remainingMillis, 0, MAX_MILLIS);
    }

    /**
     * Whether a one-off introductory allowance remains available.
     */
    public boolean isIntroductory() {
        return remainingMillis > 0;
    }

    /**
     * Accounts for online time once per bot tick. Backwards checkpoints rebase without consuming time.
     */
    public void advance(long onlineMillis) {
        advance(onlineMillis, true);
    }

    /**
     * Counts only time spent in a running session, preserving the allowance while no host is available.
     */
    public void advance(long onlineMillis, boolean working) {
        if (!working) {
            playtimeCheckpoint = -1;
            return;
        }
        if (playtimeCheckpoint >= 0 && onlineMillis >= playtimeCheckpoint) {
            remainingMillis = Math.max(0, remainingMillis - (onlineMillis - playtimeCheckpoint));
        }
        playtimeCheckpoint = onlineMillis;
    }

    /**
     * Transfers a legacy scavenging introduction once without renewing an existing runner's cooldown.
     */
    public void inheritIntroduction(long unusedMillis) {
        remainingMillis = Math.max(remainingMillis, Math.clamp(unusedMillis, 0, MAX_MILLIS));
    }

    /**
     * Ends introductory participation without changing the account's skills or permanent identity.
     */
    public void completeIntroduction() {
        remainingMillis = 0;
    }

    /**
     * Checks the saved session cooldown without consuming it during activity selection.
     */
    public boolean canStartSession(long now) {
        return now >= sessionAfter;
    }

    /**
     * Starts a cooldown before optional session dispatch, including failed or interrupted attempts.
     */
    public boolean trySession(long now, long cooldownMillis) {
        if (!canStartSession(now)) return false;
        long cooldown = Math.max(0, cooldownMillis);
        sessionAfter = now > Long.MAX_VALUE - cooldown ? Long.MAX_VALUE : now + cooldown;
        return true;
    }

    /**
     * Saves durable progress while leaving native player playtime as the clock authority.
     */
    public JsonObject save() {
        JsonObject data = new JsonObject();
        data.addProperty("remainingMillis", remainingMillis);
        data.addProperty("sessionAfter", sessionAfter);
        return data;
    }

    /**
     * Restores bounded progress without generating an allowance when saved fields are absent.
     */
    public static BotRuneRunningPreference load(JsonObject data) {
        BotRuneRunningPreference preference = new BotRuneRunningPreference(
                data.has("remainingMillis") ? data.get("remainingMillis").getAsLong() : 0);
        preference.sessionAfter = data.has("sessionAfter") ? Math.max(0, data.get("sessionAfter").getAsLong()) : 0;
        return preference;
    }
}
