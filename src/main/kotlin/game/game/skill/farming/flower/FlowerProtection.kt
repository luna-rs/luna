package game.skill.farming.flower

import game.skill.farming.allotment.*

/**
 * What a flower patch can hold that protects the allotments of its farming area from disease: a fully grown flower or
 * a scarecrow. Which allotment crops each one protects is listed by [AllotmentSeed.flowerProtection].
 *
 * @author hydrozoa
 */
enum class FlowerProtection {

    /**
     * A fully grown marigold, protecting potatoes, onions and tomatoes.
     */
    MARIGOLD,

    /**
     * A fully grown rosemary, protecting cabbages.
     */
    ROSEMARY,

    /**
     * A fully grown nasturtium, protecting watermelons.
     */
    NASTURTIUM,

    /**
     * A scarecrow, protecting sweetcorn.
     */
    SCARECROW
}
