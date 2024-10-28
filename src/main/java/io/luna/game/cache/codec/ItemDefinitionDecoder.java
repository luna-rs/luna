package io.luna.game.cache.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import io.luna.LunaContext;
import io.luna.game.cache.Archive;
import io.luna.game.cache.Cache;
import io.luna.game.cache.CacheDecoder;
import io.luna.game.cache.CacheUtils;
import io.luna.game.model.def.ItemDefinition;
import io.luna.util.GsonUtils;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A {@link CacheDecoder} implementation that loads item definitions from the cache.
 *
 * @author lare96
 */
public final class ItemDefinitionDecoder extends CacheDecoder<ItemDefinition> {

    /**
     * A child definition within an {@link ItemDefinition}. Represents data that is not present in the #377 cache.
     */
    private static final class ItemDefinitionChild {

        /**
         * The item identifier.
         */
        private final int id;

        /**
         * The weight of the item.
         */
        private final double weight;

        /**
         * If the item is tradeable or not.
         */
        private final boolean tradeable;

        /**
         * Creates a new {@link ItemDefinitionChild}.
         *
         * @param id The item identifier.
         * @param weight The weight of the item.
         * @param tradeable If the item is tradeable or not.
         */
        private ItemDefinitionChild(int id, double weight, boolean tradeable) {
            this.id = id;
            this.weight = weight;
            this.tradeable = tradeable;
        }
    }

    /**
     * A repository that loads and provides accessibility to {@link ItemDefinitionChild} types.
     */
    private static final class ItemDefinitionChildRepository {

        /**
         * The loaded {@link ItemDefinitionChild} types.
         */
        private final Map<Integer, ItemDefinitionChild> repository = new HashMap<>();

        /**
         * Loads all {@link ItemDefinitionChild} types from the backing file.
         *
         * @throws IOException If any errors occur.
         */
        private void load() throws IOException {
            Path childDefPath = Paths.get("data", "game", "def", "items.json");
            ItemDefinitionChild[] children = GsonUtils.readAsType(childDefPath, ItemDefinitionChild[].class);
            for (ItemDefinitionChild child : children) {
                repository.put(child.id, child);
            }
        }

        /**
         * Attempts to lookup the {@link ItemDefinitionChild} for {@code id}.
         *
         * @param id The item identifier to lookup.
         * @return The associated {@link ItemDefinitionChild}, {@code null} if one doesn't exist for {@code id}.
         */
        public ItemDefinitionChild lookup(int id) {
            return repository.getOrDefault(id, new ItemDefinitionChild(id, 0.0, true));
        }
    }

    /**
     * The child definition repository.
     */
    private final ItemDefinitionChildRepository childRepository = new ItemDefinitionChildRepository();

    /**
     * A list of noted {@link ItemDefinition} types that need to be converted into a note.
     */
    private final List<ItemDefinition> noted = new ArrayList<>();

    @Override
    public void decode(Cache cache, Builder<ItemDefinition> decodedObjects) throws Exception {
        childRepository.load();
        Archive archive = Archive.decode(cache.getFile(0, 2));
        ByteBuf datBuf = archive.getFileData("obj.dat");
        ByteBuf idxBuf = archive.getFileData("obj.idx");

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
                ItemDefinition def = decodeEntry(i, datBuf);
                if (def != null) {
                    decodedObjects.add(def);
                }
            }
        } finally {
            datBuf.release();
            idxBuf.release();
        }
    }

    @Override
    public void handle(LunaContext context, Cache cache, ImmutableList<ItemDefinition> decodedObjects) {
        Map<Integer, ItemDefinition> lookup = new HashMap<>(decodedObjects.size());
        decodedObjects.forEach(def -> lookup.put(def.getId(), def));

        List<ItemDefinition> newDecodedObjects = new ArrayList<>(decodedObjects);
        for (ItemDefinition def : noted) {
            if (def.getUnnotedId().isEmpty()) {
                throw new IllegalStateException("Item [" + def.getId() + ", " + def.getName() + "] is not noted, but in noted definition list!");
            }
            ItemDefinition unnotedId = lookup.get(def.getUnnotedId().getAsInt());
            newDecodedObjects.add(def.toNote(unnotedId));
        }
        ItemDefinition.ALL.storeAndLock(newDecodedObjects);
    }

    /**
     * Decodes a single {@link ItemDefinition}.
     *
     * @param id The item ID.
     * @param data The data.
     * @return The definition.
     */
    private ItemDefinition decodeEntry(int id, ByteBuf data) {
        String name = "null";
        String description = "null";
        int modelId = -1;
        int modelZoom = -1;
        int modelRotationX = -1;
        int modelRotationY = -1;
        int modelRotationZ = -1;
        int modelOffset1 = -1;
        int modelOffset2 = -1;
        boolean stackable = false;
        int value = -1;
        boolean members = false;
        String[] inventoryActions = new String[5];
        String[] groundActions = new String[5];
        OptionalInt unnotedId = OptionalInt.empty();
        OptionalInt noteGraphicId = OptionalInt.empty();
        int modelScaleX = -1;
        int modelScaleY = -1;
        int modelScaleZ = -1;
        OptionalInt teamId = OptionalInt.empty();

        Arrays.fill(inventoryActions, "null");
        Arrays.fill(groundActions, "null");

        while (true) {
            int opcode = data.readUnsignedByte();
            if (opcode == 0) {
                ItemDefinitionChild childDef = childRepository.lookup(id);
                ItemDefinition def = new ItemDefinition(id, name, description, modelId, modelZoom, modelRotationX, modelRotationY,
                        modelRotationZ, modelOffset1, modelOffset2, stackable, value, members, inventoryActions,
                        groundActions, unnotedId, modelScaleX, modelScaleY, modelScaleZ, teamId,
                        childDef.weight, childDef.tradeable);
                if (unnotedId.isPresent() && noteGraphicId.isPresent()) {
                    noted.add(def);
                    return null;
                } else {
                    return def;
                }
            } else if (opcode == 1) {
                modelId = data.readUnsignedShort();
            } else if (opcode == 2) {
                name = CacheUtils.readString(data);
            } else if (opcode == 3) {
                description = CacheUtils.readString(data);
            } else if (opcode == 4) {
                modelZoom = data.readUnsignedShort();
            } else if (opcode == 5) {
                modelRotationX = data.readUnsignedShort();
            } else if (opcode == 6) {
                modelRotationY = data.readUnsignedShort();
            } else if (opcode == 7) {
                modelOffset1 = data.readUnsignedShort();
            } else if (opcode == 8) {
                modelOffset2 = data.readUnsignedShort();
            } else if (opcode == 10) {
                data.readUnsignedShort(); // Dummy
            } else if (opcode == 11) {
                stackable = true;
            } else if (opcode == 12) {
                value = data.readInt();
            } else if (opcode == 16) {
                members = true;
            } else if (opcode == 23 || opcode == 25) {
                data.readUnsignedShort(); // maleEquipModelIdPrimary & femaleEquipModelIdPrimary
                data.readByte(); // equipModelTranslationMale & equipModelTranslationFemale
            } else if (opcode == 24 || opcode == 26) {
                data.readUnsignedShort(); // maleEquipModelIdSecondary & femaleEquipModelIdSecondary
            } else if (opcode >= 30 && opcode <= 34) {
                String action = CacheUtils.readString(data);
                if (action.equalsIgnoreCase("hidden")) {
                    action = "null";
                }
                groundActions[opcode - 30] = action;
            } else if (opcode >= 35 && opcode <= 39) {
                inventoryActions[opcode - 35] = CacheUtils.readString(data);
            } else if (opcode == 40) {
                int colourCount = data.readUnsignedByte();
                for (int i = 0; i < colourCount; i++) {
                    data.readUnsignedShort(); // modifiedModelColors
                    data.readUnsignedShort(); // originalModelColors
                }
            } else if (opcode == 78 || opcode == 79 || (opcode >= 90 && opcode <= 93)) {
                data.readUnsignedShort(); // maleEquipModelIdEmblem, femaleEquipModelIdEmblem, maleDialogueModelId
                // femaleDialogueModelId, maleDialogueHatModelId, femaleDialogueHatModelId
            } else if (opcode == 95) {
                modelRotationZ = data.readUnsignedShort();
            } else if (opcode == 97) {
                unnotedId = OptionalInt.of(data.readUnsignedShort());
            } else if (opcode == 98) {
                noteGraphicId = OptionalInt.of(data.readUnsignedShort());
            } else if (opcode >= 100 && opcode <= 109) {
                data.readShort(); // stackableIds
                data.readShort(); // stackableAmounts
            } else if (opcode == 110) {
                modelScaleX = data.readUnsignedShort();
            } else if (opcode == 111) {
                modelScaleY = data.readUnsignedShort();
            } else if (opcode == 112) {
                modelScaleZ = data.readUnsignedShort();
            } else if (opcode == 113 || opcode == 114) {
                data.readByte(); // lightModifier, shadowModifier
            } else if (opcode == 115) {
                teamId = OptionalInt.of(data.readUnsignedByte());
            }
        }
    }
}
