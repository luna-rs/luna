package io.luna.net.msg.in;

import io.luna.game.event.impl.ObjectClickEvent;
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent;
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent;
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent object clicks.
 *
 * @author lare96
 */
public final class ObjectClickMessageReader extends GameMessageReader<ObjectClickEvent> {

    @Override
    public ObjectClickEvent decode(Player player, GameMessage msg) {
        int opcode = msg.getOpcode();
        ByteMessage payload = msg.getPayload();
        int objectX;
        int objectY;
        int objectId;
        GameObject gameObject;
        switch (opcode) {
            case 181:
                objectX = payload.getShort(true, ValueType.ADD);
                objectY = payload.getShort(false, ByteOrder.LITTLE);
                objectId = payload.getShort(false, ByteOrder.LITTLE);
                gameObject = findObject(player, objectX, objectY, objectId);
                return new ObjectFirstClickEvent(player, gameObject);
            case 241:
                objectId = payload.getShort(false);
                objectX = payload.getShort(true);
                objectY = payload.getShort(false, ValueType.ADD);
                gameObject = findObject(player, objectX, objectY, objectId);
                return new ObjectSecondClickEvent(player, gameObject);
            case 50:
                objectX = payload.getShort(true, ByteOrder.LITTLE);
                objectY = payload.getShort(false);
                objectId = payload.getShort(false, ByteOrder.LITTLE, ValueType.ADD);
                gameObject = findObject(player, objectX, objectY, objectId);
                return new ObjectThirdClickEvent(player, gameObject);
        }
        throw new IllegalStateException("invalid opcode");
    }

    @Override
    public boolean validate(Player player, ObjectClickEvent event) {
        return event.getGameObject() != null;
    }

    /**
     * Retrieves an existing game object instance from the packet data.
     *
     * @param player The player.
     * @param objectX The x coordinate of the object.
     * @param objectY The y coordinate of the object.
     * @param objectId The object ID.
     * @return The game object, {@code null} if none matching the criteria were found.
     */
    private GameObject findObject(Player player, int objectX, int objectY, int objectId) {
        Position objectPosition = new Position(objectX, objectY, player.getZ());
        return player.getWorld().getObjects().findAll(objectPosition).
                filter(object -> object.getId() == objectId && object.isVisibleTo(player)).
                findFirst().orElse(null);
    }
}
