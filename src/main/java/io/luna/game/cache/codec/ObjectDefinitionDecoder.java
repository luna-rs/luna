package io.luna.game.cache.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import io.luna.LunaContext;
import io.luna.game.cache.Archive;
import io.luna.game.cache.Cache;
import io.luna.game.cache.CacheDecoder;
import io.luna.game.cache.CacheUtils;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.def.VarpChildDefinition;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.OptionalInt;

/**
 * A {@link CacheDecoder} implementation that loads object definitions from the cache.
 *
 * @author lare96
 */
public final class ObjectDefinitionDecoder extends CacheDecoder<GameObjectDefinition> {

    @Override
    public void decode(Cache cache, Builder<GameObjectDefinition> decodedObjects) throws Exception {
        Archive archive = Archive.decode(cache.getFile(0, 2));
        ByteBuf datBuf = archive.getFileData("loc.dat");
        ByteBuf idxBuf = archive.getFileData("loc.idx");

        try {
            int count = idxBuf.readShort();
            int index = 2;
            int[] indices = new int[count];
            for (int i = 0; i < count; i++) {
                indices[i] = index;
                index += idxBuf.readShort();
            }

            for (int i = 0; i < count; i++) {
                datBuf.readerIndex(indices[i]);
                decodedObjects.add(decodeEntry(i, datBuf));
            }
        } finally {
            datBuf.release();
            idxBuf.release();
        }

    }

    @Override
    public void handle(LunaContext context, Cache cache, ImmutableList<GameObjectDefinition> decodedObjects) {
        GameObjectDefinition.ALL.storeAndLock(decodedObjects);
    }

    /**
     * Decodes a single {@link GameObjectDefinition}.
     *
     * @param id The object ID.
     * @param data The data.
     * @return The definition.
     */
    private GameObjectDefinition decodeEntry(int id, ByteBuf data) {
        String name = "null";
        String description = "null";
        int width = 1;
        int length = 1;
        OptionalInt animationId = OptionalInt.empty();
        boolean solid = true;
        boolean impenetrable = true;
        boolean interactive = false;
        String[] actions = new String[10];
        boolean obstructive = false;
        VarpChildDefinition varpDef = new VarpChildDefinition(-1, -1, ImmutableList.of());

        Arrays.fill(actions, "null");
        while (true) {
            int opcode = data.readUnsignedByte();

            if (opcode == 0) {
                return new GameObjectDefinition(id, name, description, width, length, solid, impenetrable, interactive,
                        animationId, ImmutableList.copyOf(actions), obstructive, varpDef);
            } else if (opcode == 1) {
                int amount = data.readUnsignedByte();
                for (int i = 0; i < amount; i++) {
                    data.readShort();
                    data.readByte();
                }
            } else if (opcode == 2) {
                name = CacheUtils.readString(data);
            } else if (opcode == 3) {
                description = CacheUtils.readString(data);
            } else if (opcode == 5) {
                int amount = data.readUnsignedByte();
                for (int i = 0; i < amount; i++) {
                    data.readShort();
                }
            } else if (opcode == 14) {
                width = data.readUnsignedByte();
            } else if (opcode == 15) {
                length = data.readUnsignedByte();
            } else if (opcode == 17) {
                solid = false;
            } else if (opcode == 18) {
                impenetrable = false;
            } else if (opcode == 19) {
                interactive = data.readBoolean();
            } else if (opcode == 24) {
                animationId = OptionalInt.of(data.readUnsignedShort());
            } else if (opcode == 28 || opcode == 29) {
                data.readUnsignedByte();
            } else if (opcode >= 30 && opcode < 39) {
                String action = CacheUtils.readString(data);
                actions[opcode - 30] = action;
            } else if (opcode == 39) {
                data.readByte();
            } else if (opcode == 40) {
                int amount = data.readUnsignedByte();
                for (int i = 0; i < amount; i++) {
                    data.readShort();
                    data.readShort();
                }
            } else if (opcode == 60 || opcode >= 65 && opcode <= 68) {
                data.readShort();
            } else if (opcode == 69) {
                data.readByte();
            } else if (opcode >= 70 && opcode <= 72) {
                data.readShort();
            } else if (opcode == 73) {
                obstructive = true;
            } else if (opcode == 75) {
                data.readByte();
            } else if (opcode == 77) {
                int varBitId = data.readShort();
                int varpId = data.readShort();
                int childCount = data.readByte();
                ImmutableList.Builder<Integer> children = ImmutableList.builder();
                for (int i = 0; i <= childCount; i++) {
                    children.add((int) data.readShort());
                }
                varpDef = new VarpChildDefinition(varBitId, varpId, children.build());
            }
        }
    }
}
