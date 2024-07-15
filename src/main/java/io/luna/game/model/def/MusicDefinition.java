package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Region;

import java.util.Optional;

/**
 * Represents a single song that can be played in the music tab
 *
 * @author lare96
 */
public final class MusicDefinition implements Definition {

    /**
     * A repository of all music definitions.
     */
    public static final MapDefinitionRepository<MusicDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * A repository of all regions identifiers to music definitions.
     */
    private static ImmutableMap<Integer, MusicDefinition> ALL_REGIONS;

    /**
     * Attempts to find the proper song to play based on region ID.
     *
     * @param id The region ID to check.
     * @return The result wrapped in an optional.
     */
    public static Optional<MusicDefinition> getMusicForRegion(int id) {
        return Optional.ofNullable(getAllMusicForRegions().get(id));
    }

    /**
     * Returns the backing map of region mappings to definitions. Will build it first if necessary.
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
     * The song name.
     */
    private final String name;

    /**
     * The song line id.
     */
    private final int lineId;

    /**
     * The song button id.
     */
    private final int buttonId;

    /**
     * The regions this song is played in.
     */
    private final ImmutableSet<Region> regions;

    /**
     * Creates a new {@link MusicDefinition}.
     *
     * @param id The song id.
     * @param name The song name.
     * @param lineId The song line id.
     * @param buttonId The song button id.
     * @param regions The regions this song is played in.
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
     * @return The song name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The song line id.
     */
    public int getLineId() {
        return lineId;
    }

    /**
     * @return The song button id.
     */
    public int getButtonId() {
        return buttonId;
    }

    /**
     * @return The regions this song is played in.
     */
    public ImmutableSet<Region> getRegions() {
        return regions;
    }

}
