package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Region;

import java.util.Optional;

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
     * Lazy-built map of region id to music definition.
     * <p>
     * Built by {@link #getAllMusicForRegions()} on first access.
     */
    private static ImmutableMap<Integer, MusicDefinition> ALL_REGIONS;

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
     * Returns the backing region-id to music-definition map, building it if necessary.
     * <p>
     * This method is intentionally lazy to avoid paying the indexing cost unless region-based music lookup is used.
     *
     * @return An immutable region mapping.
     */
    public static ImmutableMap<Integer, MusicDefinition> getAllMusicForRegions() {
        if (ALL_REGIONS == null) {
            var builder = ImmutableMap.<Integer, MusicDefinition>builder();
            for (MusicDefinition def : ALL) {
                for (Region region : def.regions) {
                    builder.put(region.getId(), def);
                }
            }
            ALL_REGIONS = builder.build();
        }
        return ALL_REGIONS;
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
        this.id = id;
        this.name = name;
        this.lineId = lineId;
        this.buttonId = buttonId;
        this.regions = regions;
    }

    @Override
    public int getId() {
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
}
