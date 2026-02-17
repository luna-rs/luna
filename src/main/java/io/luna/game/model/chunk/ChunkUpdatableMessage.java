package io.luna.game.model.chunk;

import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.GroupedEntityMessageWriter;

/**
 * An interface for message types that can be queued through the {@link ChunkUpdatableRequest} system.
 * <p>
 * Implementations are intended to be {@link GameMessageWriter} instances that can be batched into a single
 * {@link GroupedEntityMessageWriter} for efficient transmission to the client.
 *
 * @author lare96
 */
public interface ChunkUpdatableMessage {
}
