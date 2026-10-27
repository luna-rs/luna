package io.luna.game.model.mob;

import com.google.common.collect.ImmutableSet;
import game.player.Jingles;
import io.luna.game.model.Position;
import io.luna.game.model.def.MusicDefinition;
import io.luna.game.model.def.MusicDefinition.MusicRegion;
import io.luna.game.model.def.MusicLibrary;
import io.luna.game.model.def.VarpDefinition;
import io.luna.game.model.mob.varp.PersistentVarp;
import io.luna.game.model.mob.varp.PersistentVarpManager;
import io.luna.game.model.mob.varp.Varp;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.JingleMessageWriter;
import io.luna.net.msg.out.MusicMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises player music selection, persistence, mute, plane changes and jingles.
 *
 * @author lare96
 */
final class PlayerMusicTest {

    private Player player;
    private PlayerMusic music;
    private PersistentVarpManager varps;
    private final List<GameMessageWriter> messages = new ArrayList<>();
    private final List<Varp> updates = new ArrayList<>();

    @BeforeAll
    static void initializeModeVarp() {
        if (VarpDefinition.ALL.get(18).isEmpty()) {
            VarpDefinition.ALL.storeDefinition(new VarpDefinition(18, 0));
        }
    }

    @BeforeEach
    void initialize() {
        messages.clear();
        updates.clear();
        player = mock(Player.class);
        varps = new PersistentVarpManager(player);
        when(player.getVarpManager()).thenReturn(varps);
        when(player.getPosition()).thenReturn(new Position(3222, 3218));
        doAnswer(call -> { messages.add(call.getArgument(0)); return null; })
                .when(player).queue(any(GameMessageWriter.class));
        doAnswer(call -> { updates.add(call.getArgument(0)); return null; })
                .when(player).sendVarp(any(Varp.class));
        doAnswer(call -> { varps.setValue(call.getArgument(0), call.getArgument(1)); return null; })
                .when(player).sendVarp(any(PersistentVarp.class), anyInt());
        MusicDefinition lower = new MusicDefinition(76, "Harmony", 4344, 4344,
                ImmutableSet.of(new MusicRegion(12850, 0), new MusicRegion(12851, 0)),
                PersistentVarp.MUSIC_UNLOCKS_1, 31);
        MusicDefinition upper = new MusicDefinition(35, "Sea Shanty 2", 4391, 4391,
                ImmutableSet.of(new MusicRegion(12850, 2)), PersistentVarp.MUSIC_UNLOCKS_14, 5);
        MusicDefinition free = new MusicDefinition(0, "Scape Main", 4389, 4389, ImmutableSet.of());
        music = new PlayerMusic(player, new MusicLibrary(List.of(lower, upper, free)));
    }

    @Test
    void loginUnlocksAndPlaysTheAreaOnceIncludingWhileMuted() {
        varps.setValue(PersistentVarp.MUSIC_VOLUME, 4);
        music.initialize();
        assertTrue(music.isAutomatic());
        assertEquals(List.of(76), songIds());
        assertEquals(1, updates.getFirst().getValue());
        assertTrue(music.isUnlocked(76));
        assertEquals(Integer.MIN_VALUE, varps.getValue(PersistentVarp.MUSIC_UNLOCKS_1));
        music.updatePosition();
        when(player.getPosition()).thenReturn(new Position(3222, 3270));
        music.updatePosition();
        music.setAutomatic(true);
        assertEquals(List.of(76), songIds(), "adjacent areas with the same track must not restart it");
        verify(player, times(1)).sendVarp(eq(PersistentVarp.MUSIC_UNLOCKS_1), anyInt());
    }

    @Test
    void lockedClicksCannotChangeModeAndManualExplorationStillUnlocksTracks() {
        music.initialize();
        assertFalse(music.playManually(35));
        assertTrue(music.isAutomatic());
        verify(player).sendMessage("You have not unlocked this piece of music yet!");
        assertTrue(music.playManually(0), "free tracks can be selected immediately");
        assertFalse(music.isAutomatic());
        when(player.getPosition()).thenReturn(new Position(3222, 3218, 2));
        music.updatePosition();
        assertTrue(music.isUnlocked(35));
        assertEquals(List.of(76, 0), songIds());
        music.setAutomatic(true);
        assertEquals(List.of(76, 0, 35), songIds());
        assertEquals(35, music.getCurrentTrackId());
    }

    @Test
    void stopCancelsAreaPlaybackUntilAutomaticModeResumes() {
        music.initialize();
        music.playJingle(Jingles.DEATH_2);
        music.stop();
        when(player.getPosition()).thenReturn(new Position(3222, 3218, 2));
        music.updatePosition();
        assertFalse(music.isAutomatic());
        assertEquals(-1, music.getCurrentTrackId());
        assertEquals(List.of(76, -1), songIds());
        music.setAutomatic(true);
        assertEquals(List.of(76, -1, 35), songIds());
    }

    @Test
    void jinglesKeepTheBackgroundSelectionAndNewAreaCanReplaceIt() {
        music.initialize();
        music.playJingle(Jingles.DEATH_2);
        assertEquals(76, music.getCurrentTrackId());
        assertEquals(1, messages.stream().filter(JingleMessageWriter.class::isInstance).count());
        when(player.getPosition()).thenReturn(new Position(3222, 3218, 2));
        music.updatePosition();
        assertEquals(List.of(76, 35), songIds());
        assertTrue(music.isAutomatic());
    }

    @Test
    void unknownAreasKeepTheCurrentSongAndScriptsCanPlayAndUnlockMusic() {
        music.initialize();
        when(player.getPosition()).thenReturn(new Position(1000, 1000));
        music.updatePosition();
        assertEquals(List.of(76), songIds());
        music.play(35);
        assertEquals(List.of(76, 35), songIds());
        assertTrue(music.isUnlocked(35));
        assertTrue(music.isAutomatic());
        assertThrows(IllegalArgumentException.class, () -> music.play(65535));
        assertEquals(List.of(76, 35), songIds());
    }

    @Test
    void unlocksSurviveSaveLoadButLoginResetsManualMode() {
        varps.setValue(PersistentVarp.MUSIC_UNLOCKS_1, 1 << 4);
        music.initialize();
        music.unlock(35);
        music.setAutomatic(false);
        PersistentVarpManager restored = new PersistentVarpManager(player);
        restored.fromMap(varps.toMap());
        assertEquals((1 << 4) | Integer.MIN_VALUE, restored.getValue(PersistentVarp.MUSIC_UNLOCKS_1));
        assertEquals(1 << 5, restored.getValue(PersistentVarp.MUSIC_UNLOCKS_14));
        assertNull(PersistentVarp.ALL.get(PlayerMusic.MODE_VARP), "playback mode must stay temporary");
        when(player.getVarpManager()).thenReturn(restored);
        music.initialize();
        assertTrue(music.isAutomatic());
        assertTrue(music.isUnlocked(76));
        assertTrue(music.isUnlocked(35));
    }

    @Test
    void botsDoNotReceiveMusicOrInterfaceTraffic() {
        when(player.isBot()).thenReturn(true);
        music.initialize();
        music.updatePosition();
        music.play(76);
        music.playJingle(Jingles.DEATH_2);
        music.stop();
        music.setAutomatic(false);
        assertFalse(music.playManually(0));
        assertTrue(messages.isEmpty());
        assertTrue(updates.isEmpty());
    }

    @Test
    void selectedTrackNameReachesThePlayingWidget() {
        music.initialize();
        WidgetTextMessageWriter writer = messages.stream().filter(WidgetTextMessageWriter.class::isInstance)
                .map(WidgetTextMessageWriter.class::cast).reduce((first, last) -> last).orElseThrow();
        var buffer = Unpooled.buffer();
        try {
            writer.write(player, buffer);
            int id = (buffer.readUnsignedByte() - 128 & 255) | (buffer.readUnsignedByte() << 8);
            assertEquals(4439, id);
            byte[] text = new byte[buffer.readableBytes() - 1];
            buffer.readBytes(text);
            assertEquals("Harmony", new String(text, java.nio.charset.StandardCharsets.ISO_8859_1));
        } finally {
            buffer.release();
        }
    }

    private List<Integer> songIds() {
        List<Integer> ids = new ArrayList<>();
        for (GameMessageWriter writer : messages) {
            if (!(writer instanceof MusicMessageWriter)) continue;
            var buffer = Unpooled.buffer();
            try {
                writer.write(player, buffer);
                int id = (buffer.readUnsignedByte() - 128 & 255) | (buffer.readUnsignedByte() << 8);
                ids.add(id == 65535 ? -1 : id);
            } finally {
                buffer.release();
            }
        }
        return ids;
    }
}
