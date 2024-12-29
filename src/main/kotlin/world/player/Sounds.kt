package world.player

enum class Sounds(vararg val ids: Int) {
    PICK_LOCK(37),
    SMELTING(352),
    PICKUP_ITEM(356),
    COOK_FOOD(357),
    BURN_LOG(374),
    LIGHT_FIRE(375),
    DROP_ITEM(376),
    FISH(379),
    BURY_BONE(380),
    UNARMED_BLOCK(406),
    PICKPOCKET_FAILED(458),
    SMITHING(468),
    CUT_TREE_1(471),
    CUT_TREE_2(472),
    TREE_FALLEN(473),
    CRAFT_RUNES(481),
    MINE_ROCK(432),
    PROSPECT_ORE(431),
    MINING_COMPLETED(429),
    TAKE_DAMAGE(816, 822, 823, 824), // todo remove vararg, play certain sound with certain dmg
    STRING_BOW(1311),
    BURN_LOG_QUICK(2584),

}