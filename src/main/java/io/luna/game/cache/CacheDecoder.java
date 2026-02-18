package io.luna.game.cache;

import com.google.common.collect.ImmutableList;
import io.luna.LunaContext;
import io.luna.game.model.def.DefinitionRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Extracts and optionally stores a specific kind of decoded data from a {@link Cache}.
 * <p>
 * A decoder has two phases:
 * <ol>
 *   <li>{@link #decode(Cache, ImmutableList.Builder)}: read raw cache files and build decoded objects.</li>
 *   <li>{@link #handle(LunaContext, Cache, ImmutableList)}: store or apply decoded objects (optional).</li>
 * </ol>
 * <p>
 * Decoders are executed via {@link Cache#runDecoders(LunaContext, CacheDecoder[])} using
 * {@link #toTask(LunaContext, Cache)}.
 *
 * @param <T> The decoded object type produced by this decoder.
 * @author lare96
 */
public abstract class CacheDecoder<T> {

    /**
     * Shared logger for cache decoding.
     */
    protected static final Logger logger = LogManager.getLogger();

    /**
     * Decodes cache data into {@code decodedObjects}.
     * <p>
     * Implementations should:
     * <ul>
     *   <li>Read required raw files via {@link Cache#getFile(int, int)}.</li>
     *   <li>Parse/decompress as required.</li>
     *   <li>Add decoded objects into the provided builder.</li>
     * </ul>
     *
     * @param cache The cache to decode from.
     * @param decodedObjects Destination builder for decoded objects.
     * @throws Exception If decoding fails.
     */
    public abstract void decode(Cache cache, ImmutableList.Builder<T> decodedObjects) throws Exception;

    /**
     * Handles the decoded objects (optional).
     * <p>
     * Override this to store decoded objects into repositories (e.g. a {@link DefinitionRepository}), build indices,
     * or publish decoded tables into the {@link LunaContext}.
     *
     * @param ctx The game context.
     * @param cache The cache that was decoded from.
     * @param decodedObjects The fully decoded objects.
     * @throws Exception If handling fails.
     */
    public void handle(LunaContext ctx, Cache cache, ImmutableList<T> decodedObjects) throws Exception {
        /* optional */
    }

    /**
     * Wraps {@link #decode(Cache, ImmutableList.Builder)} and {@link #handle(LunaContext, Cache, ImmutableList)}
     * into a single {@link Runnable} task.
     * <p>
     * Failures are caught and logged so one decoder does not crash the decoder executor.
     *
     * @param ctx The game context.
     * @param cache The cache to decode from.
     * @return A runnable task that performs decode then handle.
     */
    public Runnable toTask(LunaContext ctx, Cache cache) {
        return () -> {
            try {
                ImmutableList.Builder<T> decodedObjectsBuilder = ImmutableList.builder();
                decode(cache, decodedObjectsBuilder);
                ImmutableList<T> decodedObjectsList = decodedObjectsBuilder.build();
                handle(ctx, cache, decodedObjectsList);
            } catch (Exception e) {
                logger.error("A cache decoder failed to finish!", e);
            }
        };
    }
}
