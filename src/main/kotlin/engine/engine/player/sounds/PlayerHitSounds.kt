package engine.player.sounds

import game.player.Sound
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Hit
import io.luna.game.model.mob.block.Hit.HitType
import io.luna.net.msg.out.SoundMessageWriter

/**
 * Plays player pain and armour-block sounds when a hit is applied.
 *
 * Positive damage, including poison, uses the appearance gender's pain sounds. A blocked hit uses the shield's
 * configured sound, then the chest item's sound, then the unarmed fallback. Sounds are sent only to the victim
 * with a twenty-client-cycle delay, matching Lost City's damage_self and human_hit_sound procedures.
 *
 * Equipment mappings come from LostCityRS/Content revision 289, commit
 * 8efdb336fac31d0d0ad1e1c2912544e972ef6b98. Items without an explicit mapping use the fallback policy;
 * sounds are not inferred from item names or equipment bonuses.
 *
 * @author lare96
 */
object PlayerHitSounds {

    /** Male pain sounds selected with equal probability. */
    private val maleHitSounds = listOf(Sound.HUMAN_HIT2, Sound.HUMAN_HIT3, Sound.HUMAN_HIT4)

    /** Female pain sounds selected with equal probability. */
    private val femaleHitSounds = listOf(Sound.FEMALE_HIT, Sound.FEMALE_HIT2)

    /**
     * Explicit shield and chest defence sounds available in Luna's equipment definitions.
     * Some Sound constants still have historical names; numeric ids match the source synth table.
     */
    private val defenceSounds = mapOf(
        1101 to Sound.SOUND_784, // iron_chainbody: blade3_mail (784).
        1103 to Sound.SOUND_782, // bronze_chainbody: big_bar_mail (782).
        1105 to Sound.SOUND_783, // steel_chainbody: blade2_mail (783).
        1107 to Sound.STEEL_MAIL, // black_chainbody: steel_mail (788).
        1109 to Sound.SOUND_787, // mithril_chainbody: steel2_mail (787).
        1111 to Sound.SOUND_781, // adamant_chainbody: sword_hit2_mail (781).
        1113 to Sound.SOUND_789, // rune_chainbody: swordclash3_mail (789).
        1115 to Sound.BLADE3, // iron_platebody: blade3 (412).
        1117 to Sound.BIG_BAR, // bronze_platebody: big_bar (411).
        1119 to Sound.BLADE2, // steel_platebody: blade2 (408).
        1121 to Sound.STEEL, // mithril_platebody: steel (409).
        1123 to Sound.STEEL2, // adamant_platebody: steel2 (405).
        1125 to Sound.SWORD_HIT2, // black_platebody: sword_hit2 (406).
        1127 to Sound.SOUND_410, // rune_platebody: swordclash3 (410).
        1129 to Sound.LEATHER_HIT, // leather_armour: leather_hit (795).
        1131 to Sound.LEATHER_HIT, // hardleather_body: leather_hit (795).
        1133 to Sound.LEATHER_HIT, // studded_body: leather_hit (795).
        1135 to Sound.LEATHER_HIT, // dragonhide_body: leather_hit (795).
        1171 to Sound.WOOD_ARMOUR3_1, // wooden_shield: wood_armour3_1 (761).
        1173 to Sound.BRASSCLASH, // bronze_sq_shield: brassclash (415).
        1175 to Sound.SOUND_786, // iron_sq_shield: brassclash2 (786).
        1177 to Sound.BRASSCLASH2, // steel_sq_shield: blade4 (414).
        1179 to Sound.SOUND_785, // black_sq_shield: blade5 (785).
        1181 to Sound.SWORDCLASH2, // mithril_sq_shield: swordclash2 (407).
        1183 to Sound.SOUND_790, // adamant_sq_shield: swordclash5 (790).
        1185 to Sound.SOUND_791, // rune_sq_shield: swordclash6 (791).
        1187 to Sound.SOUND_791, // dragon_sq_shield: swordclash6 (791).
        1189 to Sound.BRASSCLASH, // bronze_kiteshield: brassclash (415).
        1191 to Sound.SOUND_786, // iron_kiteshield: brassclash2 (786).
        1193 to Sound.BRASSCLASH2, // steel_kiteshield: blade4 (414).
        1195 to Sound.SOUND_785, // black_kiteshield: blade5 (785).
        1197 to Sound.SWORDCLASH2, // mithril_kiteshield: swordclash2 (407).
        1199 to Sound.SOUND_790, // adamant_kiteshield: swordclash5 (790).
        1201 to Sound.SOUND_791, // rune_kiteshield: swordclash6 (791).
        1540 to Sound.SOUND_786, // antidragonbreathshield: brassclash2 (786).
        2499 to Sound.LEATHER_HIT, // blue_dragonhide_body: leather_hit (795).
        2501 to Sound.LEATHER_HIT, // red_dragonhide_body: leather_hit (795).
        2503 to Sound.LEATHER_HIT, // black_dragonhide_body: leather_hit (795).
        2583 to Sound.SWORD_HIT2, // black_platebody_trim: sword_hit2 (406).
        2589 to Sound.SOUND_785, // black_kiteshield_trim: blade5 (785).
        2591 to Sound.SWORD_HIT2, // black_platebody_gold: sword_hit2 (406).
        2597 to Sound.SOUND_785, // black_kiteshield_gold: blade5 (785).
        2599 to Sound.SWORD_HIT2, // adamant_platebody_trim: sword_hit2 (406).
        2607 to Sound.SWORD_HIT2, // adamant_platebody_gold: sword_hit2 (406).
        2615 to Sound.SOUND_410, // rune_platebody_gold: swordclash3 (410).
        2621 to Sound.SOUND_791, // rune_kiteshield_gold: swordclash6 (791).
        2623 to Sound.SOUND_410, // rune_platebody_trim: swordclash3 (410).
        2629 to Sound.SOUND_791, // rune_kiteshield_trim: swordclash6 (791).
        2653 to Sound.SOUND_410, // rune_platebody_zamorak: swordclash3 (410).
        2659 to Sound.SOUND_791, // rune_kiteshield_zamorak: swordclash6 (791).
        2661 to Sound.SOUND_410, // rune_platebody_saradomin: swordclash3 (410).
        2667 to Sound.SOUND_791, // rune_kiteshield_saradomin: swordclash6 (791).
        2669 to Sound.SOUND_410, // rune_platebody_guthix: swordclash3 (410).
        2675 to Sound.SOUND_791, // rune_kiteshield_guthix: swordclash6 (791).
        2890 to Sound.BRASSCLASH2, // elemental_shield: blade4 (414).
        3122 to Sound.STONE_ARMOUR1, // granite_shield: stone_armour1 (756).
        3140 to Sound.SWORDCLASH4_MAIL, // dragon_chainbody: swordclash4_mail (792).
        3387 to Sound.WOOD_ARMOUR3_1, // splitbark_body: wood_armour3_1 (761).
        3758 to Sound.WOOD_ARMOUR3_1, // viking_shield: wood_armour3_1 (761).
    )

    /**
     * Plays the sound for an applied hit, preserving silence for zero-damage poison or disease hits.
     *
     * @param player The player receiving the hit.
     * @param hit The hit after health clamping and hitsplat-type normalization.
     */
    fun play(player: Player, hit: Hit) {
        val sound = when {
            hit.damage > 0 -> if (player.appearance.isMale) maleHitSounds.random() else femaleHitSounds.random()
            hit.type == HitType.BLOCKED -> defenceSounds[player.equipment.computeIdForIndex(Equipment.SHIELD)]
                ?: defenceSounds[player.equipment.computeIdForIndex(Equipment.CHEST)]
                ?: Sound.HUMAN_BLOCK_1
            else -> return
        }
        player.queue(SoundMessageWriter(sound.id, 1, 20))
    }
}
