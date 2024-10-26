package io.luna.game.cache.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import io.luna.LunaContext;
import io.luna.game.cache.Archive;
import io.luna.game.cache.Cache;
import io.luna.game.cache.CacheDecoder;
import io.luna.game.model.def.VarpDefinition;
import io.netty.buffer.ByteBuf;

/**
 * A {@link CacheDecoder} implementation that loads {@link VarpDefinition} types from the cache.
 *
 * @author lare96
 */
public class VarpDefinitionDecoder extends CacheDecoder<VarpDefinition> {

    @Override
    public void decode(Cache cache, Builder<VarpDefinition> decodedObjects) throws Exception {
        Archive config = Archive.decode(cache.getFile(0, 2));
        ByteBuf dataBuf = config.getFileData("varp.dat");

        try {
            int count = dataBuf.readUnsignedShort();
            for (int i = 0; i < count; i++) {
                decodedObjects.add(decodeEntry(i, dataBuf));
            }
        } finally {
            dataBuf.release();
        }
    }

    @Override
    public void handle(LunaContext context, Cache cache, ImmutableList<VarpDefinition> decodedObjects) {
        VarpDefinition.ALL.storeAndLock(decodedObjects);
    }

    /**
     * Decodes a single {@link VarpDefinition}.
     *
     * @param data The data.
     * @return The definition.
     */
    private VarpDefinition decodeEntry(int id, ByteBuf data) {
        int type = 0;
        while (true) {
            int opcode = data.readUnsignedByte();
            if (opcode == 0) {
                return new VarpDefinition(id, type);
            } else if (opcode == 5) {
                type = data.readUnsignedShort();
            }
        }
    }
}
