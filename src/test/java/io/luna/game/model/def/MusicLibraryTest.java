package io.luna.game.model.def;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Position;
import io.luna.game.model.def.MusicDefinition.MusicRegion;
import io.luna.game.model.mob.varp.PersistentVarp;
import io.luna.util.parser.impl.MusicDefinitionFileParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the imported 377 catalog, plane mappings and invalid catalog rejection.
 *
 * @author lare96
 */
final class MusicLibraryTest {

    @Test
    void loadsTheCompleteLostCityCatalog() throws Exception {
        MusicDefinitionFileParser parser = new MusicDefinitionFileParser();
        List<MusicDefinition> tracks = new ArrayList<>();
        try (var reader = Files.newBufferedReader(parser.getFilePath())) {
            for (var token : parser.newParser(reader)) {
                tracks.add(parser.convert(token.getAsJsonObject()));
            }
        }
        MusicLibrary library = new MusicLibrary(tracks);
        assertEquals(383, tracks.size());
        assertEquals(298, tracks.stream().mapToInt(track -> track.getMusicRegions().size()).sum());
        assertEquals(377, tracks.stream().filter(track -> track.getUnlockVarp() != null).count());
        assertEquals(76, library.getMusic(new Position(3222, 3218)).orElseThrow().id());
        assertEquals(76, library.getMusic(new Position(3222, 3218, 1)).orElseThrow().id());
        assertEquals(35, library.getMusic(new Position(3048, 3236)).orElseThrow().id());
        assertEquals(573, library.getMusic(new Position(1750, 5150)).orElseThrow().id());
        assertEquals(573, library.getMusic(new Position(1750, 5200)).orElseThrow().id());
        assertEquals("Forgotten", library.getMusic(new Position(2700, 4880, 0)).orElseThrow().getName());
        assertEquals("Throne of the Demon", library.getMusic(new Position(2700, 4880, 2)).orElseThrow().getName());
        assertTrue(library.getMusic(new Position(2700, 4880, 1)).isEmpty());
        assertEquals(76, library.getButton(4344).orElseThrow().id());
        assertNull(library.getTrack(0).orElseThrow().getUnlockVarp());
        assertEquals(PersistentVarp.MUSIC_UNLOCKS_2, library.getTrack(76).orElseThrow().getUnlockVarp());
        assertEquals(26, library.getTrack(76).orElseThrow().getUnlockBit());
        MusicDefinition barking = tracks.stream().filter(track -> track.getName().equals("Barking Mad"))
                .findFirst().orElseThrow();
        MusicDefinition narnode = tracks.stream().filter(track -> track.getName().equals("Narnode's Theme"))
                .findFirst().orElseThrow();
        assertEquals(barking.getUnlockVarp(), narnode.getUnlockVarp());
        assertEquals(barking.getUnlockBit(), narnode.getUnlockBit());
        assertTrue(tracks.stream().allMatch(track -> track.id() <= 646));
        assertTrue(library.getButton(6269).isEmpty());
        assertTrue(library.getButton(6270).isEmpty());
    }

    @Test
    void specificPlanesOverrideGeneralMappings() {
        MusicDefinition general = track(1, 100, -1, 1);
        MusicDefinition upper = track(2, 101, 2, 2);
        MusicLibrary library = new MusicLibrary(List.of(general, upper));
        assertEquals(1, library.getMusic(new Position(3222, 3218, 0)).orElseThrow().id());
        assertEquals(2, library.getMusic(new Position(3222, 3218, 2)).orElseThrow().id());
        assertTrue(library.getTrack(999).isEmpty());
    }

    @Test
    void rejectsAmbiguousAndInvalidCatalogs() {
        MusicDefinition first = track(1, 100, -1, 1);
        assertThrows(IllegalArgumentException.class, () -> new MusicLibrary(List.of(first, first)));
        assertThrows(IllegalArgumentException.class,
                () -> new MusicLibrary(List.of(first, track(2, 100, 2, 2))));
        assertThrows(IllegalArgumentException.class,
                () -> new MusicLibrary(List.of(first, track(2, 101, -1, 2))));
        assertDoesNotThrow(() -> new MusicLibrary(List.of(first, track(2, 101, 2, 1))),
                "shared client unlock bits are permitted");
        assertThrows(IllegalArgumentException.class, () -> new MusicRegion(12850, 4));
        assertThrows(IllegalArgumentException.class, () -> new MusicRegion(65536, 0));
        assertThrows(IllegalArgumentException.class, () -> track(-1, 100, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> track(65535, 100, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> track(1, 100, -1, 32));
    }

    private static MusicDefinition track(int id, int button, int plane, int bit) {
        return new MusicDefinition(id, "Track " + id, button, button,
                ImmutableSet.of(new MusicRegion(12850, plane)), PersistentVarp.MUSIC_UNLOCKS_1, bit);
    }
}
