/**
 * An enum representing headgear that can be used to enter an [Altar].
 */
enum class Tiara(val id: Int, val config: Int) {
    AIR_TIARA(id = 5527,
              config = 1),
    MIND_TIARA(id = 5529,
               config = 2),
    WATER_TIARA(id = 5531,
                config = 4),
    BODY_TIARA(id = 5535,
               config = 8),
    EARTH_TIARA(id = 5537,
                config = 16),
    FIRE_TIARA(id = 5533,
               config = 31), // TODO test if 31? (probably 32)
    COSMIC_TIARA(id = 5539,
                 config = 64),
    NATURE_TIARA(id = 5543,
                 config = 128),
    CHAOS_TIARA(id = 5541,
                config = 256),
    LAW_TIARA(id = 5545,
              config = 512),
    DEATH_TIARA(id = 5547,
                config = 1024);

    companion object {

        /**
         * Mappings of [Tiara.id] to [Tiara] instances.
         */
        val ID_TO_TIARA = values().associateBy { it.id }
    }
}
