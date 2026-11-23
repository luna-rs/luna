package engine.bot.event

import game.bot.scripts.BarrowsBotScript
import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.combat.PkBotScript.PkArea
import game.bot.scripts.combat.PkPatrolArea
import game.content.barrows.BarrowsAreaListener
import game.content.barrows.BarrowsEncounters
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonality
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPreference
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * Generated kits and native behavior for temporary Wilderness and Barrows participants.
 *
 * @author lare96
 */
internal object PkBarrowsEvents {
    private val patrolSlots = buildList<PkPatrolArea> {
        val areas = PkPatrolArea.staffed
        val total = areas.sumOf { it.share }
        repeat(total) { index ->
            add(areas.maxBy { area -> (index + 1) * area.share - total * count { it == area } })
        }
    }
    private val patrolSpawns = mapOf(
        PkPatrolArea.EDGEVILLE to PkArea.LOW_LEVEL.anchors.filter { PkPatrolArea.at(it) == PkPatrolArea.EDGEVILLE },
        PkPatrolArea.GREEN_DRAGONS to PkArea.GREEN_DRAGONS.anchors.filter { PkPatrolArea.at(it) == PkPatrolArea.GREEN_DRAGONS },
        PkPatrolArea.DEEP_WILDERNESS to PkArea.DEMONIC_RUINS.anchors
    )
    private val barrowsSpawns = BarrowsBotScript.mounds.values.toList()

    val activities: List<BotEventActivity> = listOf(
        BotEventActivity(
            name = "PKing", defaultMinutes = 30,
            spawn = { index, _ -> pkSpawn(index) },
            prepare = ::preparePk,
            play = { bot, participant ->
                if (participant.canStart()) PkBotScript.forEvent(bot, participant, patrolArea(participant.index)) else null
            },
            inMatch = { bot -> bot.inWilderness() && (bot.scriptStack.current() is CombatBotScript || bot.combat.inCombat()) },
            configure = { builder, context ->
                val manager = context.world.botManager.personalityManager
                val personality = BotPersonality.Builder(manager).template(PersonalityTemplateType.PKER).build()
                val preferences = BotPreference.Builder(manager, personality).setPk(BotPkPreference(
                    BotPkPreference.Purpose.VALUE, BotPkPreference.Styles.MELEE_RANGED, BotPkPreference.Build.MAIN)).build()
                builder.setPersonality(personality).setPreferences(preferences)
            }
        ),
        BotEventActivity(
            name = "Barrows", defaultMinutes = 30,
            spawn = { index, _ -> barrowsSpawns[index % barrowsSpawns.size] },
            prepare = ::prepareBarrows,
            play = { bot, participant ->
                if (participant.canStart()) BarrowsBotScript.forEvent(bot, participant) else null
            },
            leave = { bot ->
                if (BarrowsAreaListener.inside(bot.position)) BarrowsAreaListener.exit(bot)
                else BarrowsEncounters.clear(bot)
            }
        )
    )

    private fun patrolArea(index: Int): PkPatrolArea = patrolSlots[index % patrolSlots.size]

    private fun pkSpawn(index: Int): Position {
        val area = patrolArea(index)
        val anchors = patrolSpawns.getValue(area)
        val earlierInArea = index / patrolSlots.size * area.share +
            patrolSlots.take(index % patrolSlots.size).count { it == area }
        return anchors[earlierInArea % anchors.size]
    }

    private fun preparePk(bot: Bot, participant: BotEventParticipant) {
        val tier = when (patrolArea(participant.index)) {
            PkPatrolArea.EDGEVILLE -> participant.index % 3
            PkPatrolArea.GREEN_DRAGONS -> 1 + participant.index % 2
            else -> 2
        }
        bot.applyEventKit(
            levels = mapOf(
                Skill.ATTACK to 44 + 16 * tier, Skill.STRENGTH to 54 + 14 * tier,
                Skill.DEFENCE to 40 + 15 * tier, Skill.HITPOINTS to 55 + 14 * tier,
                Skill.RANGED to 54 + 13 * tier, Skill.PRAYER to listOf(31, 43, 70)[tier]
            ),
            gear = mapOf(
                Equipment.HEAD to Item(1163), Equipment.AMULET to Item(1731),
                Equipment.WEAPON to Item(if (tier == 2) 4151 else 1333),
                Equipment.CHEST to Item(1113), Equipment.LEGS to Item(1079),
                Equipment.AMMUNITION to Item(892, 250)
            ),
            supplies = listOf(Item(861), Item(385, 18), Item(2434, 3), Item(2440), Item(2444))
        )
    }

    private fun prepareBarrows(bot: Bot, participant: BotEventParticipant) {
        if (bot.position != participant.spawn) bot.move(participant.spawn)
        BarrowsEncounters.clear(bot)
        bot.varpManager.setAndSendValue(PersistentVarp.BARROWS, 0)
        bot.varpManager.setAndSendValue(PersistentVarp.BARROWS_KILLS, 0)
        bot.applyEventKit(
            levels = mapOf(
                Skill.ATTACK to 70, Skill.DEFENCE to 70, Skill.HITPOINTS to 80,
                Skill.RANGED to 78, Skill.PRAYER to 70, Skill.MAGIC to 78
            ),
            gear = mapOf(
                Equipment.WEAPON to Item(1387), Equipment.CHEST to Item(4091), Equipment.LEGS to Item(4093),
                Equipment.AMMUNITION to Item(892, 350)
            ),
            supplies = listOf(
                Item(BarrowsBotScript.SPADE), Item(861), Item(2503), Item(556, 2500), Item(565, 500),
                Item(385, 16), Item(2434, 5)
            )
        )
    }
}
