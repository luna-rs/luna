package world.player.item.consume.potion

import api.predef.*
import io.luna.game.model.mob.block.Hit
import io.luna.game.model.mob.Player

/**
 * An enum representing potions that can be consumed.
 */
enum class Potion(val fourDose: Int,
                  val threeDose: Int,
                  val twoDose: Int,
                  val oneDose: Int,
                  val effect: (Player) -> Unit) {
    ZAMORAK_BREW(fourDose = 2450,
                 threeDose = 189,
                 twoDose = 191,
                 oneDose = 193,
                 effect = { it.onZamorakBrew() }),
    SARADOMIN_BREW(fourDose = 6685,
                   threeDose = 6687,
                   twoDose = 6689,
                   oneDose = 6691,
                   effect = { it.onSaradominBrew() }),
    AGILITY_POTION(fourDose = 3032,
                   threeDose = 3034,
                   twoDose = 3036,
                   oneDose = 3038,
                   effect = { it.onSkillPotion(SKILL_AGILITY) }),
    FISHING_POTION(fourDose = 2438,
                   threeDose = 151,
                   twoDose = 153,
                   oneDose = 155,
                   effect = { it.onSkillPotion(SKILL_FISHING) }),
    RANGING_POTION(fourDose = 2444,
                   threeDose = 169,
                   twoDose = 171,
                   oneDose = 173,
                   effect = { it.onCombatPotion(SKILL_RANGED) }),
    MAGIC_POTION(fourDose = 3040,
                 threeDose = 3042,
                 twoDose = 3044,
                 oneDose = 3046,
                 effect = { it.onCombatPotion(SKILL_MAGIC) }),
    DEFENCE_POTION(fourDose = 2432,
                   threeDose = 133,
                   twoDose = 135,
                   oneDose = 137,
                   effect = { it.onCombatPotion(SKILL_DEFENCE) }),
    STRENGTH_POTION(fourDose = 113,
                    threeDose = 115,
                    twoDose = 117,
                    oneDose = 119,
                    effect = { it.onCombatPotion(SKILL_STRENGTH) }),
    ATTACK_POTION(fourDose = 2428,
                  threeDose = 121,
                  twoDose = 123,
                  oneDose = 125,
                  effect = { it.onCombatPotion(SKILL_ATTACK) }),
    SUPER_DEFENCE(fourDose = 2442,
                  threeDose = 163,
                  twoDose = 165,
                  oneDose = 167,
                  effect = { it.onCombatPotion(SKILL_DEFENCE, true) }),
    SUPER_ATTACK(fourDose = 2436,
                 threeDose = 145,
                 twoDose = 147,
                 oneDose = 149,
                 effect = { it.onCombatPotion(SKILL_ATTACK, true) }),
    SUPER_STRENGTH(fourDose = 2440,
                   threeDose = 157,
                   twoDose = 159,
                   oneDose = 161,
                   effect = { it.onCombatPotion(SKILL_STRENGTH, true) }),
    ENERGY_POTION(fourDose = 3008,
                  threeDose = 3010,
                  twoDose = 3012,
                  oneDose = 3014,
                  effect = { it.onEnergyPotion(false) }),
    SUPER_ENERGY(fourDose = 3016,
                 threeDose = 3018,
                 twoDose = 3020,
                 oneDose = 3022,
                 effect = { it.onEnergyPotion(true) }),
    ANTIPOISON_POTION(fourDose = 2446,
                      threeDose = 175,
                      twoDose = 177,
                      oneDose = 179,
                      effect = { it.onAntipoison() }),
    SUPER_ANTIPOISON(fourDose = 2448,
                     threeDose = 181,
                     twoDose = 183,
                     oneDose = 185,
                     effect = { it.onAntipoison(500) }),
    ANTIDOTE_PLUS(fourDose = 5943,
                  threeDose = 5945,
                  twoDose = 5947,
                  oneDose = 5949,
                  effect = { it.onAntipoison(1000) }),
    ANTIDOTE_PLUS_PLUS(fourDose = 5952,
                       threeDose = 5954,
                       twoDose = 5956,
                       oneDose = 5958,
                       effect = { it.onAntipoison(1200) }),
    PRAYER_POTION(fourDose = 2434,
                  threeDose = 139,
                  twoDose = 141,
                  oneDose = 143,
                  effect = { it.onPrayerPotion() }),
    ANTI_FIRE_POTION(fourDose = 2452,
                     threeDose = 2454,
                     twoDose = 2456,
                     oneDose = 2458,
                     effect = { it.onAntifirePotion() }),
    SUPER_RESTORE(fourDose = 3024,
                  threeDose = 3026,
                  twoDose = 3028,
                  oneDose = 3030,
                  effect = { it.onRestorePotion(true) });

    companion object {

        /**
         * Mappings of dose identifiers to [Potion] instances.
         */
        val DOSE_TO_POTION = HashMap<Int, Potion>().apply {
            for (potion in Potion.values()) {
                this[potion.fourDose] = potion
                this[potion.threeDose] = potion
                this[potion.twoDose] = potion
                this[potion.oneDose] = potion
            }
        }
    }

    /**
     * The formatted name.
     */
    val formattedName = itemName(fourDose).dropLast(3)

    /**
     * Computes and returns the next potion dose identifier.
     */
    fun getNextDose(current: Int) =
        when (current) {
            fourDose -> threeDose
            threeDose -> twoDose
            twoDose -> oneDose
            else -> null
        }

    /**
     * Computes and returns the remaining dose amount.
     */
    fun getDosesLeft(current: Int) =
        when (current) {
            fourDose -> 3
            threeDose -> 2
            twoDose -> 1
            oneDose -> 0
            else -> throw IllegalArgumentException("Invalid dose identifier <$current>.")
        }
}

/**
 * Invoked when a Zamorak brew is sipped.
 */
private fun Player.onZamorakBrew() {
    val removeHp = 2 + (0.10 * hitpoints.staticLevel).toInt()
    attack.addLevels(2 + (0.20 * attack.staticLevel).toInt(), true)
    strength.addLevels(2 + (0.12 * strength.staticLevel).toInt(), true)
    defence.removeLevels(2 + (0.10 * defence.staticLevel).toInt())
    damage(Hit(removeHp))
    prayer.addLevels((0.10 * prayer.staticLevel).toInt(), true)
}

/**
 * Invoked when a Saradomin brew is sipped.
 */
private fun Player.onSaradominBrew() {
    val removeHp = 2 + (0.15 * hitpoints.staticLevel).toInt()
    defence.addLevels(2 + (0.20 * defence.staticLevel).toInt(), true)
    damage(Hit(removeHp))
    attack.removeLevels((0.10 * attack.staticLevel).toInt())
    strength.removeLevels((0.10 * strength.staticLevel).toInt())
    magic.removeLevels((0.10 * magic.staticLevel).toInt())
    ranged.removeLevels((0.10 * ranged.staticLevel).toInt())
}

/**
 * Invoked when a potion with anti-poisoning properties is sipped.
 */
private fun Player.onAntipoison(immunityDuration: Int = -1) {
    // TODO Antipoison effects.
}

/**
 * Invoked when a prayer potion is sipped.
 */
private fun Player.onPrayerPotion() {
    val prayer = skill(SKILL_PRAYER)
    prayer.addLevels(7 + (prayer.staticLevel / 4), false)
}

/**
 * Invoked when a non-combat skill potion is sipped.
 */
private fun Player.onSkillPotion(skillId: Int) {
    val skill = skill(skillId)
    skill.addLevels(3, true)
}

/**
 * Invoked when a energy or super energy potion is sipped.
 */
private fun Player.onEnergyPotion(superPotion: Boolean) {
    val amount = if (superPotion) 20.0 else 10.0
    increaseRunEnergy(amount)
}

/**
 * Invoked when a restore or super restore potion is sipped.
 */
private fun Player.onRestorePotion(superPotion: Boolean) {
    fun boost(level: Int): Int {
        val amount = if (superPotion) 8 + (0.25 * level)
        else 10 + (0.30 * level)
        return amount.toInt()
    }

    skills.stream()
        .filter { it.id != SKILL_PRAYER && it.id != SKILL_HITPOINTS }
        .forEach { it.addLevels(boost(it.staticLevel), false) }

    if (superPotion) {
        // If super restore is being sipped, restore prayer as well.
        prayer.addLevels(8 + (prayer.staticLevel / 4), false)
    }
}

/**
 * Invoked when an anti-fire potion is sipped.
 */
private fun Player.onAntifirePotion() {
    // TODO Antifire effects.
}

/**
 * Invoked when a combat skill potion is sipped.
 */
private fun Player.onCombatPotion(skillId: Int, superPotion: Boolean = false) {
    fun boostAmount(level: Int) = if (superPotion) 5 + (0.15 * level)
    else 3 + (0.10 * level)

    val skill = skill(skillId)
    skill.addLevels(boostAmount(skill.staticLevel).toInt(), true)
}