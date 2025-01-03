package io.luna.game.model.map.builder;

import io.luna.LunaContext;
import io.luna.game.model.Region;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.map.DynamicMap;
import io.luna.game.model.map.DynamicMapController;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.ControllerKey;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A builder to create {@link DynamicMap} types.
 *
 * @author lare96
 */
public final class DynamicMapBuilder {

    /**
     * A builder for the controller portion.
     */
    public final class DynamicMapControllerBuilder {
        private final DynamicMapPalette palette;
        private final Chunk baseChunk;

        private DynamicMapControllerBuilder(DynamicMapPalette palette, Chunk baseChunk) {
            this.palette = palette;
            this.baseChunk = baseChunk;
        }

        /**
         * Sets the controller that will be registered to {@link Player} tupes when a {@link DynamicMap} instance is
         * joined.
         *
         * @param controllerKey The key of the controller.
         * @return The next builder.
         */
        public CreateDynamicMapBuilder setController(ControllerKey<? extends DynamicMapController> controllerKey) {
            return new CreateDynamicMapBuilder(palette, baseChunk, controllerKey);
        }
    }

    /**
     * A builder to construct the instance.
     */
    public final class CreateDynamicMapBuilder {
        private final DynamicMapPalette palette;
        private final Chunk baseChunk;
        private final ControllerKey<? extends DynamicMapController> controllerKey;

        private CreateDynamicMapBuilder(DynamicMapPalette palette, Chunk baseChunk, ControllerKey<? extends DynamicMapController> controllerKey) {
            this.palette = palette;
            this.baseChunk = baseChunk;
            this.controllerKey = controllerKey;
        }

        /**
         * Constructs the {@link DynamicMap} instance based on the supplied settings.
         */
        public DynamicMap build() {
            return new DynamicMap(context, baseChunk, palette, controllerKey);
        }
    }

    /**
     * The context.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link DynamicMapBuilder}.
     *
     * @param context The context.
     */
    public DynamicMapBuilder(LunaContext context) {
        this.context = context;
    }

    /**
     * Fills this builder's palette with {@code regionId} at height level {@code 0}.
     *
     * @param regionId The region to fill the palette with.
     * @return The next builder.
     */
    public DynamicMapControllerBuilder fillWithRegion(int regionId) {
        return fillWithRegion(new Region(regionId));

    }

    /**
     * Fills this builder's palette with {@code region} at height level {@code 0}.
     *
     * @param region The region to fill the palette with.
     * @return The next builder.
     */
    public DynamicMapControllerBuilder fillWithRegion(Region region) {
        DynamicMapPalette palette = new DynamicMapPalette();
        palette.setRegion(4, 0, region);
        DynamicMapChunk mapChunk = palette.getChunk(6, 6, 0);
        return new DynamicMapControllerBuilder(palette, mapChunk.getChunk());
    }

    /**
     * Fills this builder's palette with chunks surrounding {@code baseChunk} with {@code radius} at height level
     * {@code 0}.
     *
     * @param baseChunk The base chunk.
     * @param radius The radius.
     * @return The next builder.
     */
    public DynamicMapControllerBuilder fillWithChunks(Chunk baseChunk, int radius) {
        checkArgument(radius >= 0 && radius < 6, "radius must be >= 0 && < 6");
        DynamicMapPalette palette = new DynamicMapPalette();
        palette.setChunkRadius(6 - radius, 6 - radius, 0, baseChunk, radius, radius);
        return new DynamicMapControllerBuilder(palette, baseChunk);
    }

    /**
     * Fills this builder's palette with {@code palette}.
     *
     * @param baseChunk The base chunk that will be used to translate coordinates between the real and instanced map.
     * @param palette The palette to fill this builder with.
     * @return The next builder.
     */
    public DynamicMapControllerBuilder setPalette(Chunk baseChunk, DynamicMapPalette palette) {
        return new DynamicMapControllerBuilder(palette, baseChunk);
    }
}
