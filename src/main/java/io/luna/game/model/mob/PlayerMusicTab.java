package io.luna.game.model.mob;

import com.google.common.base.Objects;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents data contained within the music tab.
 *
 * @author lare96
 */
public final class PlayerMusicTab {

    /**
     * Represents a single unlocked song.
     */
    public static final class UnlockedSong {

        /**
         * The song ID.
         */
        private final int songId;

        /**
         * The song line ID.
         */
        private final int lineId;

        /**
         * Creates a new {@link UnlockedSong}.
         *
         * @param songId The song ID.
         * @param lineId The song line ID.
         */
        public UnlockedSong(int songId, int lineId) {
            this.songId = songId;
            this.lineId = lineId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnlockedSong that = (UnlockedSong) o;
            return songId == that.songId;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(songId);
        }

        /**
         * @return The song ID.
         */
        public int getSongId() {
            return songId;
        }

        /**
         * @return The song line ID.
         */
        public int getLineId() {
            return lineId;
        }
    }

    /**
     * The unlocked music tracks.
     */
    private final Set<UnlockedSong> unlocked = new HashSet<>();

    /**
     * The ID of the last played track.
     */
    private int lastPlayed = -1;

    /**
     * IF the music is playing in automatic mode.
     */
    private boolean automaticMode = true;

    /**
     * If the music is playing in loop mode.
     */
    private boolean loopMode;

    /**
     * Creates a new copy of this model.
     */
    public PlayerMusicTab copy() {
        var musicTab = new PlayerMusicTab();
        musicTab.unlocked.addAll(unlocked);
        musicTab.lastPlayed = lastPlayed;
        musicTab.automaticMode = automaticMode;
        musicTab.loopMode = loopMode;
        return musicTab;
    }

    /**
     * @return The unlocked music tracks.
     */
    public Set<UnlockedSong> getUnlocked() {
        return unlocked;
    }

    /**
     * Sets the ID of the last played track.
     *
     * @param lastPlayed The new value.
     */
    public void setLastPlayed(int lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    /**
     * @return The ID of the last played track.
     */

    public int getLastPlayed() {
        return lastPlayed;
    }

    /**
     * Sets if the music is playing in automatic mode.
     *
     * @param automaticMode The new value.
     */
    public void setAutomaticMode(boolean automaticMode) {
        this.automaticMode = automaticMode;
    }

    /**
     * @return If the music is playing in automatic mode.
     */
    public boolean isAutomaticMode() {
        return automaticMode;
    }

    /**
     * Sets if the music is playing in loop mode.
     *
     * @param loopMode The new value.
     */
    public void setLoopMode(boolean loopMode) {
        this.loopMode = loopMode;
    }

    /**
     * @return If the music is playing in loop mode.
     */
    public boolean isLoopMode() {
        return loopMode;
    }
}
