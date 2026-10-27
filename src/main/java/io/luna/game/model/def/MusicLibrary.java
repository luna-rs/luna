package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.game.model.Position;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Indexes the 377 music catalog by track, button, map square and optional plane.
 * The catalog is published after startup parsing, before player logins begin.
 *
 * @author lare96
 */
public final class MusicLibrary {

    private static volatile MusicLibrary loaded = new MusicLibrary(ImmutableList.of());

    private final ImmutableMap<Integer, MusicDefinition> tracks;
    private final ImmutableMap<Integer, MusicDefinition> buttons;
    private final ImmutableMap<Integer, MusicDefinition> regions;
    private final ImmutableMap<Integer, MusicDefinition> planeRegions;

    /**
     * Builds and validates an immutable catalog without changing the loaded catalog.
     *
     * @param definitions The track definitions.
     */
    public MusicLibrary(Collection<MusicDefinition> definitions) {
        Map<Integer, MusicDefinition> byTrack = new LinkedHashMap<>();
        Map<Integer, MusicDefinition> byButton = new LinkedHashMap<>();
        Map<Integer, MusicDefinition> byRegion = new LinkedHashMap<>();
        Map<Integer, MusicDefinition> byPlane = new LinkedHashMap<>();
        for (MusicDefinition track : definitions) {
            putUnique(byTrack, track.id(), track, "track");
            putUnique(byButton, track.getButtonId(), track, "button");
            // The original 377 interface shares bit 9:18 between Barking Mad and
            // Narnode's Theme. Shared unlocks must match the cache, not be renumbered.
            for (MusicDefinition.MusicRegion region : track.getMusicRegions()) {
                if (region.plane() == -1) {
                    putUnique(byRegion, region.id(), track, "region");
                } else {
                    putUnique(byPlane, region.key(), track, "region/plane");
                }
            }
        }
        tracks = ImmutableMap.copyOf(byTrack);
        buttons = ImmutableMap.copyOf(byButton);
        regions = ImmutableMap.copyOf(byRegion);
        planeRegions = ImmutableMap.copyOf(byPlane);
    }

    private static void putUnique(Map<Integer, MusicDefinition> map, int key,
                                  MusicDefinition track, String kind) {
        checkArgument(map.putIfAbsent(key, track) == null,
                "Duplicate music %s mapping %s for %s", kind, key, track.getName());
    }

    /**
     * Validates and publishes the startup catalog and its definition repository.
     *
     * @param definitions The complete catalog.
     */
    public static void load(ImmutableList<MusicDefinition> definitions) {
        MusicLibrary library = new MusicLibrary(definitions);
        checkArgument(!definitions.isEmpty(), "The music catalog must not be empty.");
        MusicDefinition.ALL.storeAndLock(definitions);
        loaded = library;
    }

    /**
     * @return The catalog loaded at startup.
     */
    public static MusicLibrary getLoaded() {
        return loaded;
    }

    /**
     * @param id The MIDI file id.
     * @return The listed track, if present.
     */
    public Optional<MusicDefinition> getTrack(int id) {
        return Optional.ofNullable(tracks.get(id));
    }

    /**
     * @param id The clicked music component.
     * @return The listed track, if present.
     */
    public Optional<MusicDefinition> getButton(int id) {
        return Optional.ofNullable(buttons.get(id));
    }

    /**
     * Looks up plane-specific music before the map square's general mapping.
     *
     * @param position The player's position.
     * @return The area's music, if mapped.
     */
    public Optional<MusicDefinition> getMusic(Position position) {
        int region = position.getRegionId();
        MusicDefinition track = planeRegions.get((position.getZ() << 16) | region);
        return Optional.ofNullable(track != null ? track : regions.get(region));
    }

    /**
     * @return Mappings that apply to every plane of a map square.
     */
    public ImmutableMap<Integer, MusicDefinition> getRegions() {
        return regions;
    }
}
