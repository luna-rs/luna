package io.luna.game.model.chunk;

import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.GroupedEntityMessageWriter;

/**
 * Signifies a message type that can be included within a {@link GroupedEntityMessageWriter} as a part of
 * the {@link ChunkUpdatableRequest} system. Should only be extended by {@link GameMessageWriter} types.
 *
 * @author lare96
 */
public interface ChunkUpdatableMessage {
}
