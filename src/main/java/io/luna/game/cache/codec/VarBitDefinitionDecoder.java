package io.luna.game.cache.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import io.luna.LunaContext;
import io.luna.game.cache.Archive;
import io.luna.game.cache.Cache;
import io.luna.game.cache.CacheDecoder;
import io.luna.game.model.def.VarBitDefinition;
import io.netty.buffer.ByteBuf;

/**
 * A {@link CacheDecoder} implementation that loads varbit definitions from the cache.
 *
 * @author lare96
 */
public final class VarBitDefinitionDecoder extends CacheDecoder<VarBitDefinition> {

    @Override
    public void decode(Cache cache, Builder<VarBitDefinition> decodedObjects) throws Exception {
        Archive config = Archive.decode(cache.getFile(0, 2));
        ByteBuf dataBuf = config.getFileData("varbit.dat");

        try {
            int count = dataBuf.readShort();
            for (int id = 0; id < count; id++) {
                VarBitDefinition def = decodeEntry(id, dataBuf);
                if (def.getParentVarpId() == -1) {
                    continue;
                }
                decodedObjects.add(def);
            }
        } finally {
            dataBuf.release();
        }
    }

    @Override
    public void handle(LunaContext context, Cache cache, ImmutableList<VarBitDefinition> decodedObjects) {
        VarBitDefinition.ALL.storeAndLock(decodedObjects);
    }

    /**
     * Decodes a single {@link VarBitDefinition}.
     *
     * @param data The data.
     * @return The definition.
     */
    private VarBitDefinition decodeEntry(int id, ByteBuf data) {
        int parentVarpId = -1;
        int msb = -1;
        int lsb = -1;
        while (true) {
            int opcode = data.readUnsignedByte();
            if (opcode == 0) {
                return new VarBitDefinition(id, parentVarpId, msb, lsb);
            } else if (opcode == 1) {
                parentVarpId = data.readUnsignedShort();
                lsb = data.readUnsignedByte();
                msb = data.readUnsignedByte();
            }
        }
    }
}
