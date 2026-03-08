package engine.combat.weapon

import api.predef.*
import io.luna.game.model.def.WeaponStyleDefinition

val AUTOCAST_BUTTONS = setOf(1093, 1094, 1097)

// TODO Add autocasting support, which is handled differently.
val buttons = HashSet<Int>(WeaponStyleDefinition.ALL.size)
for (style in WeaponStyleDefinition.ALL.values) {
    if (style.button == -1) {
        continue
    }
    buttons.add(style.button)
}
for (id in buttons) {
    button(id) {
        plr.combat.weapon.changeStyle(id)
    }
}

/* // TODO legacy autocasting code from Asteria 3.0, useful for IDs
  case 24017:
            case 7212:
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                break
            case 1093:
            case 1094:
            case 1097:
                if (player.autocast) {
                    player.castSpell = null
                    player.autocastSpell = null
                    player.autocast = false
                    player.messages.sendByteState(108, 0)
                } else if (!player.autocast) {
                    if (player.getEquipment().getId(Equipment.WEAPON_SLOT) == 4675) {
                        if (player.getSpellbook() != Spellbook.ANCIENT) {
                            player.messages.sendMessage "You can only autocast ancient magics with this staff."
                            break
                        }

                        player.messages.sendSidebarInterface(0, 1689)
                    } else {
                        if (player.getSpellbook() != Spellbook.NORMAL) {
                            player.messages.sendMessage "You can only autocast standard magics with this staff."
                            break
                        }

                        player.messages.sendSidebarInterface(0, 1829)
                    }
                }
                break

            case 51133:
                player.autocastSpell = CombatSpells.SMOKE_RUSH.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51185:
                player.autocastSpell = CombatSpells.SHADOW_RUSH.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51091:
                player.autocastSpell = CombatSpells.BLOOD_RUSH.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 24018:
                player.autocastSpell = CombatSpells.ICE_RUSH.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51159:
                player.autocastSpell = CombatSpells.SMOKE_BURST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51211:
                player.autocastSpell = CombatSpells.SHADOW_BURST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51111:
                player.autocastSpell = CombatSpells.BLOOD_BURST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51069:
                player.autocastSpell = CombatSpells.ICE_BURST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51146:
                player.autocastSpell = CombatSpells.SMOKE_BLITZ.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51198:
                player.autocastSpell = CombatSpells.SHADOW_BLITZ.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51102:
                player.autocastSpell = CombatSpells.BLOOD_BLITZ.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51058:
                player.autocastSpell = CombatSpells.ICE_BLITZ.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51172:
                player.autocastSpell = CombatSpells.SMOKE_BARRAGE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51224:
                player.autocastSpell = CombatSpells.SHADOW_BARRAGE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51122:
                player.autocastSpell = CombatSpells.BLOOD_BARRAGE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 51080:
                player.autocastSpell = CombatSpells.ICE_BARRAGE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7038:
                player.autocastSpell = CombatSpells.WIND_STRIKE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7039:
                player.autocastSpell = CombatSpells.WATER_STRIKE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7040:
                player.autocastSpell = CombatSpells.EARTH_STRIKE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7041:
                player.autocastSpell = CombatSpells.FIRE_STRIKE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7042:
                player.autocastSpell = CombatSpells.WIND_BOLT.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7043:
                player.autocastSpell = CombatSpells.WATER_BOLT.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7044:
                player.autocastSpell = CombatSpells.EARTH_BOLT.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7045:
                player.autocastSpell = CombatSpells.FIRE_BOLT.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7046:
                player.autocastSpell = CombatSpells.WIND_BLAST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7047:
                player.autocastSpell = CombatSpells.WATER_BLAST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7048:
                player.autocastSpell = CombatSpells.EARTH_BLAST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7049:
                player.autocastSpell = CombatSpells.FIRE_BLAST.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7050:
                player.autocastSpell = CombatSpells.WIND_WAVE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7051:
                player.autocastSpell = CombatSpells.WATER_WAVE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7052:
                player.autocastSpell = CombatSpells.EARTH_WAVE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
            case 7053:
                player.autocastSpell = CombatSpells.FIRE_WAVE.getSpell()
                player.autocast = true
                player.messages.sendSidebarInterface(0, player.weapon.getId())
                player.messages.sendByteState(108, 3)
                break
 */