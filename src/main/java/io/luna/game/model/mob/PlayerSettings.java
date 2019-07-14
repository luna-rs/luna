package io.luna.game.model.mob;

import io.luna.net.msg.out.ConfigMessageWriter;

import static java.util.Objects.requireNonNull;

/**
 * A model representing client settings on the 'Game options' and 'Player controls' menus.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerSettings {

    /**
     * An enumerated type whose elements represent brightness levels.
     */
    public enum BrightnessLevel {
        NONE(0),
        DARK(1),
        NORMAL(2),
        BRIGHT(3),
        V_BRIGHT(4);

        /**
         * The client value.
         */
        private final int value;

        /**
         * Creates a {@link BrightnessLevel}.
         *
         * @param value The client value.
         */
        BrightnessLevel(int value) {
            this.value = value;
        }
    }

    /**
     * An enumerated type whose elements represent mouse button types.
     */
    public enum MouseType {
        NONE(0),
        ONE_BUTTON(1),
        TWO_BUTTONS(2);

        /**
         * The client value.
         */
        private final int value;

        /**
         * Creates a {@link MouseType}.
         *
         * @param value The client value.
         */
        MouseType(int value) {
            this.value = value;
        }
    }

    public enum VolumeLevel {
        OFF(4),
        ONE(3),
        TWO(2),
        THREE(1),
        FOUR(0);

        /**
         * The client value.
         */
        private final int value;

        /**
         * Creates a {@link VolumeLevel}.
         *
         * @param value The client value.
         */
        VolumeLevel(int value) {
            this.value = value;
        }
    }

    /**
     * The brightness level.
     */
    private BrightnessLevel brightnessLevel = BrightnessLevel.NORMAL;

    /**
     * The mouse type.
     */
    private MouseType mouseType = MouseType.TWO_BUTTONS;

    /**
     * If chat effects should be shown.
     */
    private boolean chatEffects = true;

    /**
     * If private chat should be split.
     */
    private boolean splitPrivateChat = true;

    /**
     * If aid should be accepted.
     */
    private boolean acceptAid = true;

    /**
     * The music volume.
     */
    private VolumeLevel musicVolume = VolumeLevel.FOUR;

    /**
     * The sound effects volume.
     */
    private VolumeLevel effectsVolume = VolumeLevel.FOUR;

    /**
     * If the player will run.
     */
    private boolean running;

    /**
     * If the player will auto-retaliate when attacked.
     */
    private boolean autoRetaliate = true;

    /**
     * The player.
     */
    private transient Player player;

    /**
     * Creates a new copy of this model.
     */
    public PlayerSettings copy() {
        var settings = new PlayerSettings();
        settings.brightnessLevel = brightnessLevel;
        settings.mouseType = mouseType;
        settings.chatEffects = chatEffects;
        settings.splitPrivateChat = splitPrivateChat;
        settings.acceptAid = acceptAid;
        settings.musicVolume = musicVolume;
        settings.effectsVolume = effectsVolume;
        settings.running = running;
        settings.autoRetaliate = autoRetaliate;
        return settings;
    }

    /**
     * Show all settings.
     */
    public void showAll() {
        showBrightnessLevel();
        showMouseType();
        showChatEffects();
        showSplitPrivateChat();
        showAcceptAid();
        showMusicVolume();
        showEffectsVolume();
        showRunning();
        showAutoRetaliate();
    }

    /**
     * @return The brightness level.
     */
    public BrightnessLevel getBrightnessLevel() {
        return brightnessLevel;
    }

    /**
     * Sets the brightness level.
     *
     * @param newBrightnessLevel The new value.
     */
    public void setBrightnessLevel(BrightnessLevel newBrightnessLevel) {
        if (brightnessLevel != newBrightnessLevel) {
            brightnessLevel = requireNonNull(newBrightnessLevel);
            showBrightnessLevel();
        }
    }

    /**
     * Show the brightness level setting.
     */
    public void showBrightnessLevel() {
        player.queue(new ConfigMessageWriter(166, brightnessLevel.value));
    }

    /**
     * @return The mouse type.
     */
    public MouseType getMouseType() {
        return mouseType;
    }

    /**
     * Sets the mouse type.
     *
     * @param newMouseType The new value.
     */
    public void setMouseType(MouseType newMouseType) {
        if (mouseType != newMouseType) {
            mouseType = requireNonNull(newMouseType);
            showMouseType();
        }
    }

    /**
     * Shows the mouse type setting.
     */
    public void showMouseType() {
        player.queue(new ConfigMessageWriter(170, mouseType.value));
    }

    /**
     * @return {@code true} if chat effects should be shown.
     */
    public boolean isChatEffects() {
        return chatEffects;
    }

    /**
     * Sets if chat effects should be shown.
     *
     * @param newChatEffects The new value.
     */
    public void setChatEffects(boolean newChatEffects) {
        if (chatEffects != newChatEffects) {
            chatEffects = newChatEffects;
            showChatEffects();
        }
    }

    public void showChatEffects() {
        player.queue(new ConfigMessageWriter(171, chatEffects ? 0 : 1));
    }

    /**
     * @return
     */
    public boolean isSplitPrivateChat() {
        return splitPrivateChat;
    }

    public void setSplitPrivateChat(boolean newSplitPrivateChat) {
        if (splitPrivateChat != newSplitPrivateChat) {
            splitPrivateChat = newSplitPrivateChat;
            showSplitPrivateChat();
        }
    }

    public void showSplitPrivateChat() {
        player.queue(new ConfigMessageWriter(287, splitPrivateChat ? 1 : 0));
    }

    /**
     * @return {@code true} f aid should be accepted.
     */
    public boolean isAcceptAid() {
        return acceptAid;
    }

    public void setAcceptAid(boolean newAcceptAid) {
        if (acceptAid != newAcceptAid) {
            acceptAid = newAcceptAid;
            showAcceptAid();
        }
    }

    public void showAcceptAid() {
        player.queue(new ConfigMessageWriter(427, acceptAid ? 1 : 0));
    }

    /**
     * @return The music volume.
     */
    public VolumeLevel getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(VolumeLevel newMusicVolume) {
        if (musicVolume != newMusicVolume) {
            musicVolume = requireNonNull(newMusicVolume);
            showMusicVolume();
        }
    }

    public void showMusicVolume() {
        player.queue(new ConfigMessageWriter(168, musicVolume.value));
    }

    /**
     * @return The sound effects volume.
     */
    public VolumeLevel getEffectsVolume() {
        return effectsVolume;
    }

    public void setEffectsVolume(VolumeLevel newEffectsVolume) {
        if (effectsVolume != newEffectsVolume) {
            effectsVolume = requireNonNull(newEffectsVolume);
            showEffectsVolume();
        }
    }

    public void showEffectsVolume() {
        player.queue(new ConfigMessageWriter(169, effectsVolume.value));
    }


    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean newRunning) {
        if (running != newRunning) {
            running = newRunning;
            showRunning();
        }
    }

    public void showRunning() {
        player.queue(new ConfigMessageWriter(173, running ? 1 : 0));
    }

    public boolean isAutoRetaliate() {
        return autoRetaliate;
    }

    public void setAutoRetaliate(boolean newAutoRetaliate) {
        if (autoRetaliate != newAutoRetaliate) {
            autoRetaliate = newAutoRetaliate;
            showAutoRetaliate();
        }
    }

    public void showAutoRetaliate() {
        player.queue(new ConfigMessageWriter(172, autoRetaliate ? 0 : 1));
    }

    public void setPlayer(Player newPlayer) {
        if (player == null) {
            player = requireNonNull(newPlayer);
        }
    }
}