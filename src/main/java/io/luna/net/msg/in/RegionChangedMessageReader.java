package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.RegionChangedEvent;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.StationaryEntity.UpdateType;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkPosition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.net.msg.out.ClearChunkMessageWriter;

import java.util.Iterator;
import java.util.Optional;

import static io.luna.game.model.chunk.ChunkManager.RADIUS;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the region changes.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionChangedMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        if (player.isRegionChanged()) {
            player.setRegionChanged(false);
            refreshDisplay(player);
            return new RegionChangedEvent(player);
        }
        return null;
    }

    /**
     * Refreshes {@link StationaryEntity}s within all viewable chunks of {@code player}.
     *
     * @param player The player.
     */
    public void refreshDisplay(Player player) {
        World world = player.getWorld();
        ChunkPosition position = player.getChunkPosition();
        for (int x = -RADIUS; x < RADIUS; x++) {
            for (int y = -RADIUS; y < RADIUS; y++) {
                Chunk chunk = world.getChunks().load(position.translate(x, y));

                // Clear chunk.
                Position chunkPos = chunk.getAbsolutePosition();

                // Repopulate chunk with entities.
                Iterator<GameObject> objectIterator = chunk.iterator(EntityType.OBJECT);
                Iterator<GroundItem> itemIterator = chunk.iterator(EntityType.ITEM);
                if (objectIterator.hasNext() || itemIterator.hasNext()) {
                    player.queue(new ClearChunkMessageWriter(chunkPos));
                    while (objectIterator.hasNext()) {
                        GameObject object = objectIterator.next();
                        if(object.isDynamic()) {
                            showEntity(player, object);
                        }
                    }
                    while (itemIterator.hasNext()) {
                        GroundItem item = itemIterator.next();
                        showEntity(player, item);
                    }
                }
            }
        }
    }

    /**
     * Shows a single entity to {@code player}, if necessary.
     *
     * @param player The player to show to.
     * @param entity The entity to show.
     */
    private void showEntity(Player player, StationaryEntity entity) {
        Optional<Player> updatePlr = entity.getOwner();
        boolean isUpdate = updatePlr.isEmpty() || updatePlr.get().equals(player);
        if (isUpdate) {
            // TODO Use group packet to show multiple entities?
            entity.sendUpdateMessage(player, UpdateType.SHOW);
        }
    }
}
