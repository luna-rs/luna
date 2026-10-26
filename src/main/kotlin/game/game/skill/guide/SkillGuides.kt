package game.skill.guide

import game.skill.guide.data.agilityGuide
import game.skill.guide.data.attackGuide
import game.skill.guide.data.cookingGuide
import game.skill.guide.data.craftingGuide
import game.skill.guide.data.defenceGuide
import game.skill.guide.data.farmingGuide
import game.skill.guide.data.firemakingGuide
import game.skill.guide.data.fishingGuide
import game.skill.guide.data.fletchingGuide
import game.skill.guide.data.herbloreGuide
import game.skill.guide.data.hitpointsGuide
import game.skill.guide.data.magicGuide
import game.skill.guide.data.miningGuide
import game.skill.guide.data.prayerGuide
import game.skill.guide.data.rangedGuide
import game.skill.guide.data.runecraftingGuide
import game.skill.guide.data.slayerGuide
import game.skill.guide.data.smithingGuide
import game.skill.guide.data.strengthGuide
import game.skill.guide.data.thievingGuide
import game.skill.guide.data.woodcuttingGuide

/**
 * Maps the stats tab's skill buttons to their [SkillGuide]s.
 *
 * @author Hydrozoa
 */
object SkillGuides {

    /**
     * The guide opened by each skill button on the stats tab.
     */
    val BUTTONS: Map<Int, SkillGuide> by lazy {
        mapOf(
            8654 to attackGuide,
            8655 to hitpointsGuide,
            8656 to miningGuide,
            8657 to strengthGuide,
            8658 to agilityGuide,
            8659 to smithingGuide,
            8660 to defenceGuide,
            8661 to herbloreGuide,
            8662 to fishingGuide,
            8663 to rangedGuide,
            8664 to thievingGuide,
            8665 to cookingGuide,
            8666 to prayerGuide,
            8667 to craftingGuide,
            8668 to firemakingGuide,
            8669 to magicGuide,
            8670 to fletchingGuide,
            8671 to woodcuttingGuide,
            8672 to runecraftingGuide,
            12162 to slayerGuide,
            13928 to farmingGuide
        )
    }

    /**
     * Every guide, once each.
     */
    val ALL: List<SkillGuide> by lazy { BUTTONS.values.distinct() }
}
