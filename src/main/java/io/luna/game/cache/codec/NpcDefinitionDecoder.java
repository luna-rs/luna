package io.luna.game.cache.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import io.luna.LunaContext;
import io.luna.game.cache.Archive;
import io.luna.game.cache.Cache;
import io.luna.game.cache.CacheDecoder;
import io.luna.game.cache.CacheUtils;
import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.def.VarpChildDefinition;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;

/**
 * A {@link CacheDecoder} implementation that loads npc definitions from the cache.
 *
 * @author lare96
 */
public final class NpcDefinitionDecoder extends CacheDecoder<NpcDefinition> {

    @Override
    public void decode(Cache cache, Builder<NpcDefinition> decodedObjects) throws Exception {
        Archive config = Archive.decode(cache.getFile(0, 2));
        ByteBuf dataBuf = config.getFileData("npc.dat");
        ByteBuf idxBuf = config.getFileData("npc.idx");

        try {
            int count = idxBuf.readShort();
            int index = 2;
            int[] indices = new int[count];
            for (int i = 0; i < count; i++) {
                indices[i] = index;
                index += idxBuf.readShort();
            }
            for (int i = 0; i < count; i++) {
                dataBuf.readerIndex(indices[i]);
                decodedObjects.add(decodeEntry(i, dataBuf));
            }
        } finally {
            dataBuf.release();
            idxBuf.release();
        }
    }

    @Override
    public void handle(LunaContext context, Cache cache, ImmutableList<NpcDefinition> decodedObjects) {
        NpcDefinition.ALL.storeAndLock(decodedObjects);
    }

    /**
     * Decodes a single {@link NpcDefinition}.
     *
     * @param id The npc ID.
     * @param data The data.
     * @return The definition.
     */
    private NpcDefinition decodeEntry(int id, ByteBuf data) {
        String name = "null";
        String description = "null";
        int size = 1;
        int standAnimation = -1;
        int walkAnimation = -1;
        int turnAnimationId = -1;
        int turnRightAnimationId = -1;
        int turnLeftAnimationId = -1;
        int degreesToTurn = -1;
        String[] actions = new String[10];
        boolean minimapVisible = true;
        int combatLevel = 1;
        VarpChildDefinition varpDef = new VarpChildDefinition(-1, -1, ImmutableList.of());

        Arrays.fill(actions, "null");
        while (true) {
            int opcode = data.readUnsignedByte();

            if (opcode == 0) {
                return new NpcDefinition(id, name, description, size, standAnimation, walkAnimation, turnAnimationId,
                        turnRightAnimationId, turnLeftAnimationId, degreesToTurn, ImmutableList.copyOf(actions),
                        minimapVisible, combatLevel, varpDef);
            } else if (opcode == 1) {
                int length = data.readUnsignedByte();
                for (int index = 0; index < length; index++) {
                    data.readShort();
                }
            } else if (opcode == 2) {
                name = CacheUtils.readString(data);
            } else if (opcode == 3) {
                description = CacheUtils.readString(data);
            } else if (opcode == 12) {
                size = data.readByte();
            } else if (opcode == 13) {
                standAnimation = data.readShort();
            } else if (opcode == 14) {
                walkAnimation = data.readShort();
            } else if (opcode == 17) {
                walkAnimation = data.readShort();
                turnAnimationId = data.readShort();
                turnRightAnimationId = data.readShort();
                turnLeftAnimationId = data.readShort();
            } else if (opcode >= 30 && opcode < 40) {
                String action = CacheUtils.readString(data);
                if (action.equals("hidden")) {
                    action = "null";
                }
                actions[opcode - 30] = action;
            } else if (opcode == 40) {
                int length = data.readUnsignedByte();
                for (int index = 0; index < length; index++) {
                    data.readShort(); // originalColors
                    data.readShort(); // replacementColors
                }
            } else if (opcode == 60) {
                int length = data.readUnsignedByte();
                for (int index = 0; index < length; index++) {
                    data.readShort(); // additionalModels
                }
            } else if (opcode >= 90 && opcode <= 92) {
                data.readShort(); // Dummy
            } else if (opcode == 95) {
                combatLevel = data.readUnsignedShort();
            } else if (opcode == 97) {
                data.readShort(); // scaleXY / scaleZ
            } else if (opcode == 98) {
                data.readShort(); // scaleXY / scaleZ
            } else if (opcode == 100 || opcode == 101) {
                data.readByte(); // brightness and contrast
            } else if (opcode == 102) {
                data.readShort(); // headicon
            } else if (opcode == 103) {
                degreesToTurn = data.readShort();
            } else if (opcode == 106) {
                int varBitId = data.readShort();
                int varpId = data.readShort();
                int childCount = data.readUnsignedByte();
                ImmutableList.Builder<Integer> children = ImmutableList.builder();
                for (int i = 0; i <= childCount; i++) {
                    children.add((int) data.readShort());
                }
                varpDef = new VarpChildDefinition(varBitId, varpId, children.build());
            }
        }
    }
}
