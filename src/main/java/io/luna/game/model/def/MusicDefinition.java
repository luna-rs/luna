package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Region;
import io.luna.game.model.mob.varp.PersistentVarp;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A cache/data-backed definition describing a single music track in the in-game music tab.
 * <p>
 * This definition contains both:
 * <ul>
 *     <li><b>UI metadata</b> for the music tab (line id and button id)</li>
 *     <li><b>world metadata</b> describing which {@link Region}s should trigger this track</li>
 * </ul>
 *
 * @author lare96
 */
public final class MusicDefinition implements Definition {

    /**
     * The repository of all music definitions keyed by song id.
     */
    public static final MapDefinitionRepository<MusicDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * Attempts to find the appropriate song for the given region id.
     *
     * @param id The region id to look up.
     * @return The matching {@link MusicDefinition}, if any.
     */
    public static Optional<MusicDefinition> getMusicForRegion(int id) {
        return Optional.ofNullable(getAllMusicForRegions().get(id));
    }

    /**
     * Returns the general region mappings indexed at startup.
     *
     * @return An immutable region mapping.
     */
    public static ImmutableMap<Integer, MusicDefinition> getAllMusicForRegions() {
        return MusicLibrary.getLoaded().getRegions();
    }

    /**
     * The song id.
     */
    private final int id;

    /**
     * The display name shown in the music tab.
     */
    private final String name;

    /**
     * The widget line id used to display the track name/state in the music list.
     */
    private final int lineId;

    /**
     * The button/widget id associated with selecting this track in the music tab.
     */
    private final int buttonId;

    /**
     * Regions in which this track should play.
     */
    private final ImmutableSet<Region> regions;

    private final ImmutableSet<MusicRegion> musicRegions;
    private final PersistentVarp unlockVarp;
    private final int unlockBit;

    /**
     * Creates a new {@link MusicDefinition}.
     *
     * @param id The song id.
     * @param name The song name.
     * @param lineId The music tab line widget id.
     * @param buttonId The music tab button/widget id.
     * @param regions The regions that should map to this track.
     */
    public MusicDefinition(int id, String name, int lineId, int buttonId, ImmutableSet<Region> regions) {
        this(id, name, lineId, buttonId, ImmutableSet.copyOf(regions.stream()
                .map(region -> new MusicRegion(region.getId(), -1)).iterator()), null, -1);
    }

    /**
     * Creates a track with cache-compatible unlock metadata and optional plane mappings.
     *
     * @param id The MIDI file id.
     * @param name The display name.
     * @param lineId The track text component.
     * @param buttonId The selection component.
     * @param musicRegions The map squares where this track is discovered.
     * @param unlockVarp The persistent unlock word, or null for a free track.
     * @param unlockBit The bit within the word, or -1 for a free track.
     */
    public MusicDefinition(int id, String name, int lineId, int buttonId, ImmutableSet<MusicRegion> musicRegions,
                           PersistentVarp unlockVarp, int unlockBit) {
        checkArgument(id >= 0 && id < 65535, "Invalid music id: %s", id);
        checkArgument(name != null && !name.isBlank(), "Missing music name for %s", id);
        checkArgument(lineId >= 0 && buttonId >= 0, "Invalid music component for %s", id);
        checkArgument(unlockVarp == null ? unlockBit == -1 :
                        unlockVarp.name().startsWith("MUSIC_UNLOCKS_") && unlockBit >= 0 && unlockBit < 32,
                "Invalid music unlock metadata for %s", id);
        this.id = id;
        this.name = name;
        this.lineId = lineId;
        this.buttonId = buttonId;
        this.musicRegions = musicRegions;
        this.regions = ImmutableSet.copyOf(musicRegions.stream().map(region -> new Region(region.id())).iterator());
        this.unlockVarp = unlockVarp;
        this.unlockBit = unlockBit;
    }

    @Override
    public int id() {
        return id;
    }

    /**
     * Returns the display name.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the music tab line widget id.
     *
     * @return The line id.
     */
    public int getLineId() {
        return lineId;
    }

    /**
     * Returns the music tab button/widget id.
     *
     * @return The button id.
     */
    public int getButtonId() {
        return buttonId;
    }

    /**
     * Returns the set of regions that trigger this track.
     *
     * @return The regions set.
     */
    public ImmutableSet<Region> getRegions() {
        return regions;
    }

    /**
     * @return Map squares with plane restrictions, where -1 means every plane.
     */
    public ImmutableSet<MusicRegion> getMusicRegions() {
        return musicRegions;
    }

    /**
     * @return The persistent unlock word, or null for tracks available to everyone.
     */
    public PersistentVarp getUnlockVarp() {
        return unlockVarp;
    }

    /**
     * @return The unlock bit, or -1 for a free track.
     */
    public int getUnlockBit() {
        return unlockBit;
    }

    /**
     * A 64-by-64 map square with an optional plane restriction.
     *
     * @param id The packed map-square id.
     * @param plane The plane, or -1 for every plane.
     * @author lare96
     */
    public record MusicRegion(int id, int plane) {
        public MusicRegion {
            checkArgument(id >= 0 && id <= 65535, "Invalid music region: %s", id);
            checkArgument(plane >= -1 && plane <= 3, "Invalid music plane: %s", plane);
        }

        /**
         * @return The region/plane lookup key.
         */
        public int key() {
            return (plane << 16) | id;
        }
    }
}
