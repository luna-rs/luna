package game.item.degradable

/**
 * Identifies the supported degradation systems for degradable items.
 *
 * Each enum value represents a specific degradable item chain used to determine how an item loses charges and which
 * stage it should advance to next.
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
     * Degradation type used for Ahrim's hood.
     */
    AHRIMS_HOOD,

    /**
     * Degradation type used for Ahrim's staff.
     */
    AHRIMS_STAFF,

    /**
     * Degradation type used for Ahrim's robetop.
     */
    AHRIMS_ROBETOP,

    /**
     * Degradation type used for Ahrim's robeskirt.
     */
    AHRIMS_ROBESKIRT,

    /**
     * Degradation type used for Dharok's helm.
     */
    DHAROKS_HELM,

    /**
     * Degradation type used for Dharok's greataxe.
     */
    DHAROKS_GREATAXE,

    /**
     * Degradation type used for Dharok's platebody.
     */
    DHAROKS_PLATEBODY,

    /**
     * Degradation type used for Dharok's platelegs.
     */
    DHAROKS_PLATELEGS,

    /**
     * Degradation type used for Guthan's helm.
     */
    GUTHANS_HELM,

    /**
     * Degradation type used for Guthan's warspear.
     */
    GUTHANS_WARSPEAR,

    /**
     * Degradation type used for Guthan's platebody.
     */
    GUTHANS_PLATEBODY,

    /**
     * Degradation type used for Guthan's chainskirt.
     */
    GUTHANS_CHAINSKIRT,

    /**
     * Degradation type used for Karil's coif.
     */
    KARILS_COIF,

    /**
     * Degradation type used for Karil's crossbow.
     */
    KARILS_CROSSBOW,

    /**
     * Degradation type used for Karil's leathertop.
     */
    KARILS_LEATHERTOP,

    /**
     * Degradation type used for Karil's leatherskirt.
     */
    KARILS_LEATHERSKIRT,

    /**
     * Degradation type used for Torag's helm.
     */
    TORAGS_HELM,

    /**
     * Degradation type used for Torag's hammers.
     */
    TORAGS_HAMMERS,

    /**
     * Degradation type used for Torag's platebody.
     */
    TORAGS_PLATEBODY,

    /**
     * Degradation type used for Torag's platelegs.
     */
    TORAGS_PLATELEGS,

    /**
     * Degradation type used for Verac's helm.
     */
    VERACS_HELM,

    /**
     * Degradation type used for Verac's flail.
     */
    VERACS_FLAIL,

    /**
     * Degradation type used for Verac's brassard.
     */
    VERACS_BRASSARD,

    /**
     * Degradation type used for Verac's plateskirt.
     */
    VERACS_PLATESKIRT;

    /**
     * @return `true` if this type is a Barrows degradation type, or `false` otherwise.
     */
    fun isBarrows(): Boolean {
        return when (this) {
            AHRIMS_HOOD,
            AHRIMS_STAFF,
            AHRIMS_ROBETOP,
            AHRIMS_ROBESKIRT,
            DHAROKS_HELM,
            DHAROKS_GREATAXE,
            DHAROKS_PLATEBODY,
            DHAROKS_PLATELEGS,
            GUTHANS_HELM,
            GUTHANS_WARSPEAR,
            GUTHANS_PLATEBODY,
            GUTHANS_CHAINSKIRT,
            KARILS_COIF,
            KARILS_CROSSBOW,
            KARILS_LEATHERTOP,
            KARILS_LEATHERSKIRT,
            TORAGS_HELM,
            TORAGS_HAMMERS,
            TORAGS_PLATEBODY,
            TORAGS_PLATELEGS,
            VERACS_HELM,
            VERACS_FLAIL,
            VERACS_BRASSARD,
            VERACS_PLATESKIRT -> true

            else -> false
        }
    }
}