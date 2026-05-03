package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import io.luna.util.RandomUtils;

/**
 * A {@link Definition} for the nickname aliases that bots may use when referring to an item.
 * <p>
 * Item nicknames let bot speech sound less mechanical by allowing common player slang, shorthand, and older RSPS terms
 * to be used instead of always using the full cache item name. For example, a dragon dagger may be referred to as
 * {@code "dds"} in bot speech.
 *
 * @author lare96
 */
public final class ItemNicknameDefinition implements Definition {

    /**
     * The global repository of item nickname definitions.
     * <p>
     * Definitions are keyed by item id. Nickname lookup normalizes noted items to their unnoted id before checking this
     * repository, allowing noted and unnoted forms to share the same nickname pool.
     */
    public static final MapDefinitionRepository<ItemNicknameDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The item id this nickname definition belongs to.
     */
    private final int id;

    /**
     * The immutable list of nicknames that may be used for this item.
     */
    private final ImmutableList<String> nicknames;

    /**
     * Gets a random nickname for an item.
     * <p>
     * If the supplied item is noted, its unnoted id is used for the nickname lookup. If no nickname definition exists,
     * the item's normal cache name is returned in lowercase.
     *
     * @param id The item id to get a nickname for.
     * @return A random nickname for the item, or the lowercase item name if no nickname definition exists.
     */
    public static String getNickname(int id) {
        ItemDefinition def = ItemDefinition.ALL.retrieve(id);
        int unnotedId = def.getUnnotedId().orElse(id);
        return ALL.get(unnotedId).map(it -> RandomUtils.random(it.nicknames)).orElse(def.getName().toLowerCase());
    }

    /**
     * Creates a new {@link ItemNicknameDefinition}.
     *
     * @param id The item id this nickname definition belongs to.
     * @param nicknames The nicknames that may be used for this item.
     */
    public ItemNicknameDefinition(int id, ImmutableList<String> nicknames) {
        this.id = id;
        this.nicknames = nicknames;
    }

    /**
     * Gets the item id this nickname definition belongs to.
     *
     * @return The item id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * Gets the nicknames that may be used for this item.
     *
     * @return The immutable nickname list.
     */
    public ImmutableList<String> getNicknames() {
        return nicknames;
    }
}