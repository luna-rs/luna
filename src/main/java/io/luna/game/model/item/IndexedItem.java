package io.luna.game.model.item;

/**
     * An {@link Item} wrapper holding an additional value. The value represents its index in some sort of collection or
     * group. This is only utilized during serialization to avoid having to serialize 'empty' ({@code null}) indexes.
     */
    public static final class IndexedItem {

        private final Item item;

        private final int index;

        public IndexedItem(Item item, int index) {
            this.item = item;
            this.index = index;
        }

        public int getId() {
            return item.getId();
        }

        public int getAmount() {
            return
        }

        public int getIndex() {
            return index;
        }
    }