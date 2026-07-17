package game.player.command

import api.bot.script.DynamicBotScript
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.drops.DropTableHandler
import api.predef.*
import api.predef.ext.*
import engine.bot.coordinator.skill.SmithingScriptFactory
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.LogoutBotScript
import game.player.item.consume.food.Food
import game.skill.mining.Ore
import game.skill.smithing.BarType
import io.luna.Luna
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.item.Bank
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.block.Hit
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.BotLogManager
import io.luna.game.model.mob.bot.brain.BotBrain
import io.luna.game.model.mob.movement.wandering.SmartWanderingAction
import io.luna.game.model.mob.movement.wandering.WanderingFrequency
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.TextInput
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.SoundMessageWriter
import io.luna.util.CacheDumpUtils
import io.luna.util.NumberUtils
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

cmd("botscript", RIGHTS_DEV) {
    val name = getInputFrom(0)
    for (nextPlr in world.players) {
        if (nextPlr is Bot) {
            if (nextPlr.scriptStack.current()?.javaClass?.simpleName.equals(name, true)) {
                plr.sendMessage("Has script [$name] ${nextPlr.username}")
            }
        }
    }
}

cmd("forcescript", RIGHTS_DEV) {
    for (nextPlr in world.players) {
        if (nextPlr is Bot) {
            nextPlr.bank.add(Item(Ore.IRON.item, 250))
            nextPlr.bank.add(Item(Ore.COAL.item, 250))
            nextPlr.smithing.staticLevel = BarType.STEEL.level
            nextPlr.scriptStack.pushHead(SmithingScriptFactory.getSmeltingScript(nextPlr, nextPlr.mining.staticLevel))
        }
    }
}
cmd("forcetravel", RIGHTS_DEV) {
    val name = getInputFrom(0).trim().uppercase().replace(' ', '_')
    for (nextPlr in world.players) {
        if (nextPlr is Bot) {
            nextPlr.scriptStack.pushHead(object : DynamicBotScript(nextPlr) {
                override suspend fun run(): Boolean {
                    nextPlr.actionHandler.travelTo(SubZone.valueOf(name))
                    return true
                }

            })
        }
    }
}
cmd("setbotcount") {
    val amount = asInt(0)

}
cmd("goto", RIGHTS_DEV) {
    val name = getInputFrom(0).trim().uppercase().replace(' ', '_')
    for (zone in SubZone.entries) {
        if (zone.name == name) {
            plr.move(zone.inside)
            return@cmd
        }
    }
    for (zone in Zone.entries) {
        if (zone.name == name) {
            plr.move(zone.anchor)
            return@cmd
        }
    }
}
cmd("rockcrabs") {

}
cmd("zonebots", RIGHTS_DEV) {
    for (nextPlr in world.players) {
        if (nextPlr is Bot) {
            if (nextPlr.scriptStack.current() is ZonedBotScript) {
                plr.sendMessage("Has script [${nextPlr.scriptStack.current()?.javaClass?.simpleName}] ${nextPlr.username}")
            }
        }
    }
}

cmd("botstats", RIGHTS_DEV) {
    var (totalLevel, totalLevelUsername) = Pair<Int, String?>(0, null)
    var (combatLevel, combatLevelUsername) = Pair<Int, String?>(0, null)
    for (plr in world.players) {
        if (plr is Bot) {
            var newTotalLevel = 0
            for (skill in plr.skills.asIterable()) {
                newTotalLevel += skill.staticLevel
            }
            if (newTotalLevel > totalLevel) {
                totalLevel = newTotalLevel
                totalLevelUsername = plr.username
            }
            if (plr.combatLevel > combatLevel) {
                combatLevel = plr.combatLevel
                combatLevelUsername = plr.username
            }
        }
    }
    plr.sendMessage("Highest total level $totalLevel, by $totalLevelUsername")
    plr.sendMessage("Highest combat level $combatLevel, by $combatLevelUsername")
}

cmd("botskill", RIGHTS_DEV) {
    val id = asInt(0)
    var (level, levelUsername) = Pair<Int, String?>(0, null)
    for (plr in world.players) {
        if (plr is Bot) {
            val newLevel = plr.skill(id).staticLevel
            if (newLevel > level) {
                level = newLevel
                levelUsername = plr.username
            }
        }
    }
    plr.sendMessage("Highest level for skill [${Skill.getName(id)}] $level, by $levelUsername")
}

cmd("botrich", RIGHTS_DEV) {
    var (value, valueUsername) = Pair<Long, String?>(0, null)
    for (plr in world.players) {
        if (plr is Bot) {
            val newBankValue = plr.itemTracker.entrySet().sumOf {
                if (it.element == 995) {
                    it.count
                }
                val def = itemDef(it.element)
                if (!def.isTradeable || def.value < 1) {
                    0L
                } else {
                    (def.value * it.count).toLong()
                }
            }
            println(newBankValue) //todo test when one bot active with fiorcescipt
            if (newBankValue > value) {
                value = newBankValue
                valueUsername = plr.username
            }
        }
    }
    // 810K Rough estimate value of bot starting items.
    plr.sendMessage("Highest bank value ${NumberUtils.formatPrice(value - 810_000)}, by $valueUsername")
}

cmd("saveall", RIGHTS_DEV) {
    world.persistenceService.saveAll().thenRun { logger.info("All players saved.") }
}

cmd("die", RIGHTS_DEV) {
    plr.damage(120, Hit.HitType.NORMAL)
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
    plr.submitAction(SmartWanderingAction(plr,
                                          Area.of(plr.position,
                                                  250),
                                          WanderingFrequency.NORMAL))
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
                                                  Area.of(
                                                      Luna.settings()
                                                          .game()
                                                          .startingPosition(),
                                                      250),
                                                  RandomUtils.random(
                                                      array)))
        }
    }
}

cmd("spawnbots") {
    repeat(10) {

        val bot = Bot.Builder(ctx).setTemporary().setBrain(object : BotBrain() {
            override fun process(bot: Bot): BotCoordinator? {
                return null
            }
        }).setUsername("test$it").setTemporary().build()
        bot.speechStack.setDisableAll(true)
        bot.logManager.setStreamType(BotLogManager.BotStreamType.ALL)
        bot.login().thenRun {
            world.scheduleOnce(rand(2, 10)) {
                // FletchBotScript.testScript(bot)
            }
        }
    }
}

cmd("testscript") {
    repeat(1) {
        val bot = Bot.Builder(ctx).setTemporary().setUsername("test$it").setTemporary().build()
        bot.speechStack.setDisableAll(true)
        bot.logManager.setStreamType(BotLogManager.BotStreamType.ALL)
        bot.login().thenRun {
            bot.bank.add(Item(Food.ID_TO_FOOD.keys.random(), 50_000))
            world.scheduleOnce(5) {
                bot.scriptStack.pushHead(LogoutBotScript(bot))
                // todo test rune essence mine
                /*if (rand(3) == 0) {
                    bot.scriptStack.pushHead(MineBotScript(bot,
                                                           setOf(Ore.TIN, Ore.COPPER),
                                                           Duration.INFINITE,
                                                           mutableListOf(SubZone.VARROCK_SE_MINE)))
                } else if (randBoolean()) {
                    bot.thieving.staticLevel = 60
                    bot.scriptStack.pushHead(PickpocketBotScript(bot,
                                                                 setOf(ThievingNpcType.GUARD,
                                                                       ThievingNpcType.KNIGHT_OF_ARDOUGNE),
                                                                 Duration.INFINITE,
                                                                 mutableListOf(SubZone.ARDOUGNE_SQUARE_THIEVING)))
                } else {
                    bot.thieving.staticLevel = 75
                    bot.scriptStack.pushHead(StealBotScript(bot,
                                                            if (randBoolean()) setOf(ThievingStallType.GEM) else
                                                                setOf(ThievingStallType.GEM,
                                                                      ThievingStallType.SILVER,
                                                                      ThievingStallType.SILK,
                                                                      ThievingStallType.SPICE,
                                                                      ThievingStallType.FUR),
                                                            Duration.INFINITE,
                                                            mutableListOf(SubZone.ARDOUGNE_SQUARE_THIEVING)))
                }*/
            }
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
                                 .thenRunAsync({ plr.sendMessage("Done, deleted $value") },
                                               gameService.gameExecutor)
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
 * A command that spawns a non-player character.
 */
cmd("npcblock", RIGHTS_DEV) {
    val area = Area.of(plr.position, 2)
    for (pos in area.computePositions()) {
        val npc = Npc(ctx, asInt(0), pos)
        world.addNpc(npc)
    }
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

cmd("ok") {
    val bot = Bot.Builder(ctx).setUsername("elite111111")
        .setSpawnPosition(plr.position).build()
    bot.maxSkills()
    bot.login()

}
cmd("findnpc") {
    val name = getInputFrom(0)
    val choices = ArrayList<Position>()
    for (next in world.npcs) {
        if (next.def().name.equals(name, true)) {
            choices += next.position
        }
    }
    plr.move(choices.random())
}
cmd("findfight") {
    val choices = ArrayList<Position>()
    for (next in world.players) {
        if (next.combat.inCombat()) {
            choices += next.position
        }
    }
    plr.move(choices.random())
}
cmd("findbot") {
    val choices = ArrayList<Position>()
    for (next in world.players) {
        if (next.isBot && !next.isViewableFrom(plr)) {
            choices += next.position
        }
    }
    if (choices.isNotEmpty()) {
        plr.move(choices.random())
    } else {
        plr.sendMessage("No non-viewable bots to teleport to.")
    }
}

/**
 * Simulates drops for whichever table is implemented.
 */
cmd("roll", RIGHTS_DEV) {
    val npc = asInt(0)
    var times = asInt(1)
    if (times > 50_000)
        times = 50_000
    val npcName = NpcDefinition.ALL[npc].orElseThrow().name
    plr.overlays.open(object : Bank.DynamicBankInterface("'$npcName x $times'") {
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
            npc.animation(Animation(animationId, Animation.AnimationPriority.HIGH))
        }
    } else {
        npc.animation(Animation(animationId, Animation.AnimationPriority.HIGH))
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
