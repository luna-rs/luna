package io.luna.game.cache.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import io.luna.LunaContext;
import io.luna.game.cache.Archive;
import io.luna.game.cache.Cache;
import io.luna.game.cache.CacheDecoder;
import io.luna.game.cache.CacheUtils;
import io.luna.game.model.def.WidgetDefinition;
import io.luna.game.model.def.WidgetDefinition.WidgetType;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link CacheDecoder} that decodes widget definitions.
 */
public final class WidgetDefinitionDecoder extends CacheDecoder<WidgetDefinition> {

    @Override
    public void decode(Cache cache, Builder<WidgetDefinition> decodedObjects) throws Exception {
        Archive archive = Archive.decode(cache.getFile(0, 3));
        ByteBuf dataBuf = archive.getFileData("data");

        try {
            int parentId = -1;
            int count = dataBuf.readShort();
            while (dataBuf.readerIndex() < dataBuf.writerIndex()) {
                int index = dataBuf.readUnsignedShort();
                if (index == 65535) {
                    parentId = dataBuf.readUnsignedShort();
                    index = dataBuf.readUnsignedShort();
                }
                decodedObjects.add(decodeEntry(index, parentId, dataBuf));
            }
        } finally {
            dataBuf.release();
        }
    }

    @Override
    public void handle(LunaContext context, Cache cache, ImmutableList<WidgetDefinition> decodedObjects) {
        List<WidgetDefinition> newDecodedObjects = new ArrayList<>(decodedObjects);
        WidgetDefinition.ALL.storeAndLock(newDecodedObjects);
    }

    /**
     * Decodes a single widget from the cache.
     *
     * @param id The widget id.
     * @param parentId The widget parent id.
     * @param data The buffer.
     * @return The decoded widget definition.
     */
    private WidgetDefinition decodeEntry(int id, int parentId, ByteBuf data) {
        int index = id;
        int type;
        int actionType;
        int[][] cs1opcodes = new int[0][];
        int[][] cs2opcodes = new int[0][];
        Integer[] children = new Integer[0];
        boolean inventory = false;
        String[] options = new String[0];
        String disabledText = "";
        String enabledText = "";
        int disabledAnimation = 0;
        int enabledAnimation = 0;
        String tooltip = "";

        type = data.readUnsignedByte();
        actionType = data.readUnsignedByte();
        int contentType = data.readUnsignedShort();
        int width = data.readUnsignedShort();
        int height = data.readUnsignedShort();
        int alpha = (byte) data.readUnsignedByte();
        int hoveredPopup = data.readUnsignedByte();
        if (hoveredPopup != 0)
            hoveredPopup = (hoveredPopup - 1 << 8) + data.readUnsignedByte();
        else
            hoveredPopup = -1;

        int conditionCount = data.readUnsignedByte();
        if (conditionCount > 0) {
            // conditionTypes = new int[conditionCount];
            // conditionValues = new int[conditionCount];
            for (int condition = 0; condition < conditionCount; condition++) {
                /* conditionTypes[condition] =*/
                data.readUnsignedByte();
                /* conditionValues[condition] =*/
                data.readUnsignedShort();
            }
        }
        int cs1length = data.readUnsignedByte();
        if (cs1length > 0) {
            cs1opcodes = new int[cs1length][];
            for (int blockIdx = 0; blockIdx < cs1length; blockIdx++) {
                int cs1blocklen = data.readUnsignedShort();
                cs1opcodes[blockIdx] = new int[cs1blocklen];
                for (int cs1opcIdx = 0; cs1opcIdx < cs1blocklen; cs1opcIdx++) {
                    cs1opcodes[blockIdx][cs1opcIdx] = data.readUnsignedShort();
                }
            }
        }
        if (type == 0) {
            /* scrollLimit =*/
            data.readUnsignedShort();
            /*  hiddenUntilHovered =*/
            data.readUnsignedByte();// == 1;
            int childrenCount = data.readUnsignedShort();
            children = new Integer[childrenCount];
            // childrenX = new int[childrenCount];
            //childrenY = new int[childrenCount];
            for (int child = 0; child < childrenCount; child++) {
                children[child] = data.readUnsignedShort();
                /*   childrenX[child] =*/
                data.readShort();
                /*  childrenY[child] =*/
                data.readShort();
            }

        }
        if (type == 1) {
            //unknownOne =
            data.readUnsignedShort();
            // unknownTwo =
            data.readUnsignedByte(); //== 1;
        }
        if (type == 2) {
            //  itemSwapable = == 1;
            data.readUnsignedByte();
            // isInventory = data.readUnsignedByte() == 1;
            data.readUnsignedByte();
            //itemUsable = data.readUnsignedByte() == 1;
            data.readUnsignedByte();
            //itemDeletesDraged = data.readUnsignedByte() == 1;
            data.readUnsignedByte();
            //itemSpritePadsX = data.readUnsignedByte();
            data.readUnsignedByte();
            //itemSpritePadsY = data.readUnsignedByte();
            data.readUnsignedByte();
            // imageX = new int[20];
            //imageY = new int[20];
            //images = new ImageRGB[20];
            for (int sprite = 0; sprite < 20; sprite++) {
                int hasSprite = data.readUnsignedByte();
                if (hasSprite == 1) {
                    //  imageX[sprite] =
                    data.readShort();
                    //  imageY[sprite] =
                    data.readShort();
                    String spriteName = CacheUtils.readString(data);
                   /* if (!spriteName.isEmpty()) {
                        int spriteId = spriteName.lastIndexOf(",");
                        images[sprite] = getImage(Integer.parseInt(spriteName.substring(spriteId + 1)),
                                spriteName.substring(0, spriteId));
                    }*/
                }
            }

            options = new String[5];
            for (int optionId = 0; optionId < 5; optionId++) {
                options[optionId] = CacheUtils.readString(data);
                if (options[optionId].length() == 0)
                    options[optionId] = null;
            }

        }
        if (type == 3) {
            /*filled =*/
            data.readUnsignedByte();//== 1;
        }
        if (type == 4 || type == 1) {
            /* typeFaceCentered = */
            data.readUnsignedByte(); //== 1;
            /*   int typeFace = */
            data.readUnsignedByte();
            //   if (fonts != null)
            //     typeFaces = fonts[typeFace];
            /*   typeFaceShadowed =  */
            data.readUnsignedByte();// == 1;
        }
        if (type == 4) {
            disabledText = CacheUtils.readString(data);
            enabledText = CacheUtils.readString(data);
        }
        if (type == 1 || type == 3 || type == 4)
            /* disabledColor =  */ data.readInt();
        if (type == 3 || type == 4) {
            /*  enabledColor =  */
            data.readInt();
            /*  disabledHoveredColor =  */
            data.readInt();
            /*  enabledHoveredColor =  */
            data.readInt();
        }
        if (type == 5) {
            String spriteName = CacheUtils.readString(data);
         /*   if (spriteName.length() > 0) {
                int spriteId = spriteName.lastIndexOf(",");
                disabledImage = getImage(Integer.parseInt(spriteName.substring(spriteId + 1)), spriteName.substring(0,
                        spriteId));
            } */
            spriteName = CacheUtils.readString(data);
           /* if (spriteName.length() > 0) {
                int spriteId = spriteName.lastIndexOf(",");
                enabledImage = getImage(Integer.parseInt(spriteName.substring(spriteId + 1)), spriteName.substring(0,
                        spriteId));
            } */
        }
        if (type == 6) {
            id = data.readUnsignedByte();
            if (id != 0) {
                // modelType = 1;
                // modelId = (id - 1 << 8) +
                data.readUnsignedByte();
            }
            id = data.readUnsignedByte();
            if (id != 0) {
                //  enabledModelType = 1;
                // enabledModelId = (id - 1 << 8) +
                data.readUnsignedByte();
            }
            id = data.readUnsignedByte();
            if (id != 0)
                disabledAnimation = (id - 1 << 8) + data.readUnsignedByte();
            else
                disabledAnimation = -1;
            id = data.readUnsignedByte();
            if (id != 0)
                enabledAnimation = (id - 1 << 8) + data.readUnsignedByte();
            else
                enabledAnimation = -1;
            /*   zoom = */
            data.readUnsignedShort();
            /*   rotationX =  */
            data.readUnsignedShort();
            /*   rotationY = */
            data.readUnsignedShort();
        }
        if (type == 7) {
            //items = new Item[width * height];
            /* typeFaceCentered =*/
            data.readUnsignedByte();// == 1;
            int typeFaceCount = data.readUnsignedByte();
            //if (fonts != null)
            //  typeFaces = fonts[typeFaceCount];
            /* typeFaceShadowed =*/
            data.readUnsignedByte();// == 1;
            /* disabledColor =*/
            data.readInt();
            /*  itemSpritePadsX =*/
            data.readShort();
            /*  itemSpritePadsY = */
            data.readShort();
            inventory = data.readUnsignedByte() == 1;
            options = new String[5];
            for (int optionId = 0; optionId < 5; optionId++) {
                options[optionId] = CacheUtils.readString(data);
                if (options[optionId].isEmpty())
                    options[optionId] = null;
            }

        }
        if (type == 8)
            disabledText = CacheUtils.readString(data);
        if (actionType == 2 || type == 2) {
            // optionCircumfix =
            // optionText =
            // optionAttributes =
            CacheUtils.readString(data);
            CacheUtils.readString(data);
            data.readUnsignedShort();
        }
        if (actionType == 1 || actionType == 4 || actionType == 5 || actionType == 6) {
            tooltip = CacheUtils.readString(data);
            if (tooltip.length() == 0) {
                if (actionType == 1)
                    tooltip = "Ok";
                if (actionType == 4)
                    tooltip = "Select";
                if (actionType == 5)
                    tooltip = "Select";
                if (actionType == 6)
                    tooltip = "Continue";
            }
        }
        for(int idx = 0; idx < options.length; idx++) {
            if(options[idx] == null) {
                options[idx] = "null";
            }
        }
        return new WidgetDefinition(index, parentId, WidgetType.ALL.get(type), cs1opcodes, children, inventory,
                options, disabledText, enabledText, disabledAnimation, enabledAnimation);
    }
}
