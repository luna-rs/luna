package io.luna.game.model.mob;

import game.player.Jingles;
import io.luna.game.model.Position;
import io.luna.game.model.def.MusicDefinition;
import io.luna.game.model.def.MusicLibrary;
import io.luna.game.model.mob.varp.Varp;
import io.luna.net.msg.out.JingleMessageWriter;
import io.luna.net.msg.out.MusicMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

/**
 * Owns a player's area music, manual selection and persistent music-tab unlocks.
 * All operations run on the game thread; jingles resume in the patched 377 client.
 *
 * @author lare96
 */
public final class PlayerMusic {

    public static final int MODE_VARP = 18;
    public static final int PLAYING_WIDGET = 4439;
    public static final int AUTO_BUTTON = 6269;
    public static final int MANUAL_BUTTON = 6270;

    private final Player player;
    private final MusicLibrary library;
    private boolean automatic = true;
    private int currentTrackId = -1;
    private int lastArea = -1;

    public PlayerMusic(Player player) {
        this(player, MusicLibrary.getLoaded());
    }

    PlayerMusic(Player player, MusicLibrary library) {
        this.player = player;
        this.library = library;
    }

    /**
     * Restores automatic mode each login, preserving saved unlocks like Lost City.
     */
    public void initialize() {
        if (player.isBot()) {
            return;
        }
        automatic = true;
        currentTrackId = -1;
        lastArea = -1;
        player.sendVarp(new Varp(MODE_VARP, 1));
        player.queue(new WidgetTextMessageWriter("AUTO", PLAYING_WIDGET));
        updatePosition();
    }

    /**
     * Unlocks entered areas even in manual mode or with client music muted.
     * Called during player updating; unchanged map squares/planes do no work.
     */
    public void updatePosition() {
        if (player.isBot()) {
            return;
        }
        Position position = player.getPosition();
        int area = (position.getZ() << 16) | position.getRegionId();
        if (area == lastArea) {
            return;
        }
        lastArea = area;
        library.getMusic(position).ifPresent(track -> {
            unlock(track.id());
            if (automatic) {
                send(track);
            }
        });
    }

    /**
     * Selects automatic area music or keeps the current selection in manual mode.
     *
     * @param value Whether area changes should select music.
     */
    public void setAutomatic(boolean value) {
        if (player.isBot()) {
            return;
        }
        if (automatic != value) {
            automatic = value;
            player.sendVarp(new Varp(MODE_VARP, value ? 1 : 0));
        }
        if (value) {
            lastArea = -1;
            updatePosition();
        }
    }

    /**
     * Plays an unlocked music-tab selection and switches to manual mode.
     *
     * @param id The music file id.
     * @return Whether the selection was allowed.
     */
    public boolean playManually(int id) {
        MusicDefinition track = requireTrack(id);
        if (player.isBot()) {
            return false;
        }
        if (!isUnlocked(id)) {
            player.sendMessage("You have not unlocked this piece of music yet!");
            return false;
        }
        setAutomatic(false);
        send(track);
        return true;
    }

    /**
     * Plays and unlocks music from content scripts without changing the selected mode.
     * The next mapped area can replace it in automatic mode.
     *
     * @param id The music file id.
     */
    public void play(int id) {
        MusicDefinition track = requireTrack(id);
        if (!player.isBot()) {
            unlock(id);
            send(track);
        }
    }

    /**
     * Stops music and jingles until a script selects music or automatic mode resumes.
     */
    public void stop() {
        if (!player.isBot()) {
            setAutomatic(false);
            currentTrackId = -1;
            player.queue(new MusicMessageWriter(-1));
            player.queue(new WidgetTextMessageWriter("None", PLAYING_WIDGET));
        }
    }

    /**
     * Plays a one-shot jingle while retaining the selected background music.
     *
     * @param jingle The jingle to play.
     */
    public void playJingle(Jingles jingle) {
        if (!player.isBot()) {
            player.queue(new JingleMessageWriter(jingle));
        }
    }

    /**
     * @param id The music file id.
     * @return Whether it is free or its persistent unlock bit is set.
     */
    public boolean isUnlocked(int id) {
        MusicDefinition track = requireTrack(id);
        return track.getUnlockVarp() == null ||
                (player.getVarpManager().getValue(track.getUnlockVarp()) & (1 << track.getUnlockBit())) != 0;
    }

    /**
     * Unlocks a listed track once and lets the client's varp scripts recolor its button.
     *
     * @param id The music file id.
     * @return Whether a previously locked track was unlocked.
     */
    public boolean unlock(int id) {
        MusicDefinition track = requireTrack(id);
        if (player.isBot() || isUnlocked(id)) {
            return false;
        }
        int value = player.getVarpManager().getValue(track.getUnlockVarp());
        player.sendVarp(track.getUnlockVarp(), value | (1 << track.getUnlockBit()));
        return true;
    }

    private MusicDefinition requireTrack(int id) {
        return library.getTrack(id).orElseThrow(() -> new IllegalArgumentException("Unknown music track: " + id));
    }

    private void send(MusicDefinition track) {
        if (currentTrackId != track.id()) {
            currentTrackId = track.id();
            player.queue(new WidgetTextMessageWriter(track.getName(), PLAYING_WIDGET));
            player.queue(new MusicMessageWriter(track.id()));
        }
    }

    /**
     * @return Whether area changes select the background music.
     */
    public boolean isAutomatic() {
        return automatic;
    }

    /**
     * @return The selected background music id, or -1 when stopped/unselected.
     */
    public int getCurrentTrackId() {
        return currentTrackId;
    }
}
