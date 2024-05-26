package io.luna.game.cache;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import io.luna.LunaContext;
import io.luna.game.model.def.DefinitionRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Callable;

/**
 * Extracts and stores important information from a {@link Cache}.
 *
 * @param <T> The type of information being extracted.
 * @author lare96
 */
public abstract class CacheDecoder<T> {

    /**
     * The logger.
     */
    protected static final Logger logger = LogManager.getLogger();

    /**
     * Decodes elements from {@code cache} into a series of decoded objects.
     *
     * @param cache The cache to decode from.
     * @param decodedObjects The list of decoded objects to add to.
     */
    public abstract void decode(Cache cache, ImmutableList.Builder<T> decodedObjects) throws Exception;

    /**
     * Handles the decoded objects, usually by storing them in a {@link DefinitionRepository}.
     *
     * @param cache The cache that was decoded from.
     * @param decodedObjects The objects that were decoded.
     */
    public void handle(LunaContext ctx, Cache cache, ImmutableList<T> decodedObjects) throws Exception {

    }

    /**
     * Converts {@link #decode(Cache, Builder)} and {@link #handle(LunaContext, Cache, ImmutableList)} into a sequential task
     * within a {@link Runnable}.
     *
     * @param cache The cache to decode from.
     * @return The task, wrapped in a {@link Runnable}.
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

