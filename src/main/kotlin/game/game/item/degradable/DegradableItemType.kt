package game.item.degradable

/**
 * Identifies the supported degradation systems for degradable items.
 *
 * Each enum value represents a distinct degradation behavior category used when resolving how an item loses charges
 * or advances through its degradation chain.
 *
 * @author lare96
 */
enum class DegradableItemType {

    /**
     * Degradation type used for Crystal bows.
     */
    CRYSTAL_BOW,

    /**
     * Degradation type used for Crystal shields.
     */
    CRYSTAL_SHIELD,

    /**
     * Degradation type used for Barrows equipment.
     */
    BARROWS
}