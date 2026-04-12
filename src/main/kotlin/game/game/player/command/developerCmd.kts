package game.player.command

import api.drops.DropTableHandler
import api.predef.*
import api.predef.ext.*
import io.luna.Luna
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.item.Bank.DynamicBankInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.TextInput
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.mob.wandering.SmartWanderingAction
import io.luna.game.model.mob.wandering.WanderingFrequency
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.SoundMessageWriter
import io.luna.util.CacheDumpUtils
import io.luna.util.RandomUtils
import java.lang.Boolean.parseBoolean


/**
 * A command that sends a client config.
 */
cmd("config", RIGHTS_DEV) {
    val id = asInt(0)
    val value = if (args.size == 1) 0 else asInt(1)
    plr.sendMessage("config[$id] = $value")
    plr.sendVarp(Varp(id, value))
}

/**
 * A command that re-dumps cache definitions.
 */
cmd("dumpcache", RIGHTS_DEV) {
    gameService.submit { CacheDumpUtils.dump() }
        .thenRunAsync({ plr.sendMessage("Cache dump complete.") }, gameService.gameExecutor)
    plr.sendMessage("Dumping cache data...")
}

/**
 * A command that creates and logs in bots. Arguments for amount and if their equipment should be randomized.
 */
cmd("bots", RIGHTS_DEV) {
    val username = "nhb"
    val count = if (args.isNotEmpty()) asInt(0) else 1
    val randomEquipment = if (args.size == 2) parseBoolean(args[1]) else false
    val array = WanderingFrequency.values()
    plr.submitAction(SmartWanderingAction(plr, Area.of(plr.position, 250), WanderingFrequency.NORMAL))
    repeat(count) {
        val bot = Bot.Builder(ctx).setUsername(username + it).build()
        bot.login().thenRun {
            bot.randomize()
            bot.submitAction(object : Action<Player>(bot, ActionType.SOFT, false, 5) {
                override fun run(): Boolean {
                    val npc = world.locator.findViewableNpcs(bot).firstOrNull()
                    if (!bot.combat.inCombat() && npc != null && npc.def().actions.contains("Attack") && npc.combatLevel > 0 && npc.isAlive) {
                        bot.combat.attack(npc)
                    }
                    delay = RandomUtils.inclusive(5, 25)
                    return false
                }
            })
            bot.submitAction(SmartWanderingAction(bot,
                                                  Area.of(Luna.settings().game().startingPosition(), 250),
                                                  RandomUtils.random(array)))
        }
    }
}

/**
 * Deletes a saved record of a player.
 */
cmd("delete", RIGHTS_DEV) {
    plr.overlays.open(object : TextInput() {
        override fun input(player: Player, value: String) {
            plr.newDialogue().text("Are you sure you wish to delete all records for '$value' ?")
                .options("Yes",
                         {
                             world.persistenceService.delete(value)
                                 .thenRunAsync({ plr.sendMessage("Done, deleted $value") }, gameService.gameExecutor)
                             plr.overlays.closeWindows()
                         },
                         "No",
                         { plr.overlays.closeWindows() }).open()
        }
    })
}

/**
 * A command that spawns a non-player character.
 */
cmd("npc", RIGHTS_DEV) {
    val npc = Npc(ctx, asInt(0), plr.position)
    world.addNpc(npc)
}

/**
 * A command that spawns an object.
 */
cmd("object", RIGHTS_DEV) {
    val pos = plr.position
    world.addObject(id = asInt(0),
                    x = pos.x,
                    y = pos.y,
                    z = pos.z,
                    type = ObjectType.ALL[asInt(1)]!!,
                    plr = plr)
}

/**
 * A command that spawns an object.
 */
cmd("obj", RIGHTS_DEV) {
    val pos = plr.position
    world.addObject(id = asInt(0),
                    x = pos.x,
                    y = pos.y,
                    z = pos.z,
                    type = ObjectType.DEFAULT,
                    plr = plr)
}

/**
 * Simulates drops for whichever table is implemented.
 */
cmd("roll", RIGHTS_DEV) {
    val npc = asInt(0)
    val times = asInt(1)
    val npcName = NpcDefinition.ALL[npc].orElseThrow().name
    plr.overlays.open(object : DynamicBankInterface("'$npcName x $times'") {
        override fun buildDisplayItems(player: Player?): ArrayList<Item> {
            val items = arrayListOf<Item>()
            val npcInstance = Npc(ctx, npc, plr.position)
            val table = DropTableHandler.getDropTable(npc)
            if (table != null) {
                repeat(times) {
                    items.addAll(table.roll(npcInstance, plr))
                }
            }
            return items
        }
    })
}

/**
 * A command that sends the current position.
 */
cmd("mypos", RIGHTS_DEV) {
    plr.sendMessage(plr.position)
    plr.sendMessage(plr.chunk)
    plr.sendMessage(plr.position.region)
    val topleftx = plr.position.x - 52
    val toplefty = plr.position.y - 52
    plr.sendMessage("Local pos: ${
        Position(plr.position.x - topleftx, plr.position.y - toplefty)
    }")
}

/**
 * A command that opens an interface.
 */
cmd("interface", RIGHTS_DEV) {
    val id = asInt(0)
    plr.overlays.open(StandardInterface(id))
}

/**
 * A command that plays a sound.
 */
cmd("sound", RIGHTS_DEV) {
    val id = asInt(0)
    plr.queue(SoundMessageWriter(id, 0, 0))
}

/**
 * A command that plays a graphic.
 */
cmd("graphic", RIGHTS_DEV) {
    val id = asInt(0)
    plr.graphic(Graphic(id, 100, 0))
}

/**
 * A command that plays an animation.
 */
cmd("animation", RIGHTS_DEV) {
    val id = asInt(0)
    plr.animation(Animation(id))
}

cmd("npcanim") {
    val npcId = asInt(0)
    val animationId = asInt(1)

    var npc = world.locator.findNearestNpc(plr) { it.id == npcId }
    if (npc == null) {
        npc = world.addNpc(npcId, plr.position.x, plr.position.y)
        world.scheduleOnce(5) {
            npc.animation(Animation(animationId, AnimationPriority.HIGH))
        }
    } else {
        npc.animation(Animation(animationId, AnimationPriority.HIGH))
    }
}

/**
 * A command that restores special attack energy fully.
 */
cmd("sa", RIGHTS_DEV) {
    plr.combat.specialBar.energy = 100
    plr.combat.specialBar.update()
}

/**
 * A command that resets all skill level boosts.
 */
cmd("resetboosts", RIGHTS_DEV) {
    plr.skills.resetAll()
}
