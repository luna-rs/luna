package engine.bot.event

import api.bot.zone.SubZone
import game.bot.scripts.BlastFurnaceBotScript
import game.bot.scripts.BlastFurnaceCrew
import game.bot.scripts.BlastFurnaceCrew.Role
import game.bot.scripts.BrimhavenArenaBotScript
import game.bot.scripts.FightCaveBotScript
import game.bot.scripts.GnomeBallBotScript
import game.bot.scripts.MageArenaBotScript
import game.bot.scripts.RangingGuildBotScript
import game.bot.scripts.skills.RoguesDenBotScript
import game.bot.scripts.skills.SkullballBotScript
import game.content.blastFurnace.BlastFurnace
import game.content.blastFurnace.BlastFurnaceWorkAction
import game.content.brimhavenArena.BrimhavenArena
import game.content.fightCave.FightCave
import game.content.fightCave.FightCaveController
import game.content.gnomeBall.GnomeBall
import game.content.mageArena.MageArena
import game.content.rangingGuild.ArcheryCompetition
import game.content.rangingGuild.ArcheryCompetition.count
import game.skill.agility.roguesDen.RoguesDen
import game.skill.agility.roguesDen.RoguesDen.denRun
import game.skill.agility.skullball.Skullball
import game.skill.smithing.BarType
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import kotlin.time.Duration.Companion.minutes

/**
 * Starts local minigame visits with the equipment and levels needed by their native bot scripts.
 *
 * @author lare96
 */
internal object SoloBotEvents {
    private val furnaceRoles = listOf(Role.SMELTER, Role.PEDALS, Role.PUMP, Role.STOVE, Role.REPAIR)

    val activities: List<BotEventActivity> = listOf(
        BotEventActivity(
            name = "Fight Cave",
            defaultMinutes = 60,
            spawn = { _, _ -> FightCave.OUTSIDE },
            prepare = { bot, _ ->
                bot.applyEventKit(
                    levels = mapOf(Skill.RANGED to 95, Skill.DEFENCE to 85, Skill.HITPOINTS to 94, Skill.PRAYER to 75),
                    gear = mapOf(
                        Equipment.WEAPON to Item(861),
                        Equipment.AMMUNITION to Item(892, FightCaveBotScript.AMMO_COUNT),
                        Equipment.CHEST to Item(2503),
                        Equipment.LEGS to Item(2497)
                    ),
                    supplies = listOf(Item(FightCaveBotScript.FOOD, FightCaveBotScript.FOOD_COUNT),
                        Item(FightCaveBotScript.PRAYER, FightCaveBotScript.POTION_COUNT))
                )
            },
            play = { bot, _ -> FightCaveBotScript(bot) },
            leave = { bot -> if (bot.controllers.primary is FightCaveController) FightCave.leave(bot) }
        ),
        BotEventActivity(
            name = "Mage Arena",
            defaultMinutes = 25,
            spawn = { _, _ -> MageArena.BANK },
            prepare = { bot, _ ->
                MageArena.clear(bot)
                MageArena.setStage(bot, 0)
                bot.varpManager.setAndSendValue(MageArena.God.SARADOMIN.counter, 0)
                bot.applyEventKit(
                    levels = mapOf(Skill.MAGIC to 90, Skill.DEFENCE to 75, Skill.HITPOINTS to 90, Skill.PRAYER to 70),
                    gear = mapOf(Equipment.WEAPON to Item(1381), Equipment.CHEST to Item(4091), Equipment.LEGS to Item(4093)),
                    supplies = listOf(Item(385, 16), Item(2434, 3), Item(554, 2000), Item(556, 1000),
                        Item(560, 300), Item(565, 300))
                )
            },
            play = { bot, _ -> MageArenaBotScript(bot) },
            leave = { bot ->
                MageArena.clear(bot)
                if (MageArena.arena(bot.position) || MageArena.chamber(bot.position)) bot.move(MageArena.BANK)
            }
        ),
        BotEventActivity(
            name = "Blast Furnace",
            defaultMinutes = 20,
            spawn = { _, _ -> BlastFurnace.ENTRY },
            prepare = { bot, _ ->
                bot.applyEventKit(
                    levels = mapOf(Skill.SMITHING to 70, Skill.STRENGTH to 60, Skill.FIREMAKING to 60,
                        Skill.AGILITY to 60, Skill.CRAFTING to 60, Skill.HITPOINTS to 70),
                    supplies = BlastFurnace.ingredients(BarType.STEEL).map { it.withAmount(it.amount * 8) } +
                        listOf(Item(BlastFurnace.WATER), Item(BlastFurnace.SPADE), Item(BlastFurnace.HAMMER))
                )
            },
            play = { bot, participant ->
                val preferred = furnaceRoles.getOrElse(participant.index) { Role.SMELTER }
                val role = if (preferred == Role.SMELTER || BlastFurnaceCrew.occupied(bot, preferred)) Role.SMELTER else preferred
                BlastFurnaceBotScript(bot, BarType.STEEL, role = role)
            },
            leave = { bot -> bot.actions.first(BlastFurnaceWorkAction::class.java)?.interrupt() }
        ),
        BotEventActivity(
            name = "Brimhaven Agility Arena",
            defaultMinutes = 15,
            spawn = { _, _ -> BrimhavenArena.ENTRANCE },
            prepare = { bot, _ ->
                bot.applyEventKit(levels = mapOf(Skill.AGILITY to 75, Skill.HITPOINTS to 65),
                    supplies = listOf(Item(379, 12), Item(995, 200)))
            },
            play = { bot, _ -> BrimhavenArenaBotScript(bot) },
            leave = { bot -> BrimhavenArena.leave(bot) }
        ),
        BotEventActivity(
            name = "Ranging Guild Archery",
            defaultMinutes = 10,
            spawn = { _, _ -> ArcheryCompetition.JUDGE },
            prepare = { bot, _ ->
                ArcheryCompetition.logout(bot)
                bot.count = 0
                bot.applyEventKit(levels = mapOf(Skill.RANGED to 75),
                    gear = mapOf(Equipment.WEAPON to Item(861)), supplies = listOf(Item(995, 200)))
            },
            play = { bot, _ -> RangingGuildBotScript(bot) },
            leave = { bot -> ArcheryCompetition.logout(bot) }
        ),
        BotEventActivity(
            name = "Skullball",
            defaultMinutes = 15,
            spawn = { _, _ -> SubZone.WEREWOLF_SKULLBALL.inside },
            prepare = { bot, _ -> bot.applyEventKit(levels = mapOf(Skill.AGILITY to 60)) },
            play = { bot, participant -> SkullballBotScript(bot, 15.minutes, participant.canStart) },
            leave = { bot -> Skullball.giveUp(bot) }
        ),
        BotEventActivity(
            name = "Gnome Ball",
            defaultMinutes = 15,
            spawn = { _, _ -> GnomeBall.EXIT },
            prepare = { bot, _ -> bot.applyEventKit(levels = mapOf(Skill.RANGED to 80, Skill.HITPOINTS to 65),
                supplies = listOf(Item(379, 8))) },
            play = { bot, _ -> GnomeBallBotScript(bot) },
            leave = { bot -> GnomeBall.leave(bot) }
        ),
        BotEventActivity(
            name = "Rogues' Den",
            defaultMinutes = 20,
            spawn = { _, _ -> RoguesDen.START },
            prepare = { bot, _ -> bot.applyEventKit(levels = mapOf(Skill.AGILITY to 90, Skill.THIEVING to 90),
                supplies = listOf(Item(RoguesDen.JEWEL))) },
            play = { bot, _ -> RoguesDenBotScript(bot, 15.minutes) },
            leave = { bot -> if (bot.denRun != null) RoguesDen.leave(bot, RoguesDen.Exit.LOGIN) }
        )
    )
}
