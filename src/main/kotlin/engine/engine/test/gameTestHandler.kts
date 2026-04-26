package engine.test

import api.attr.Attr
import api.combat.specialAttack.SpecialAttackHandler
import api.predef.*
import api.predef.ext.*
import game.bot.scripts.PkBotScript
import game.player.item.consume.food.Food
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.Luna
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.movement.wandering.SmartWanderingAction
import io.luna.game.model.mob.movement.wandering.WanderingFrequency
import io.luna.game.model.mob.overlay.NumberInput
import java.time.Duration
import java.util.concurrent.CompletableFuture

val COMBAT_FOOD = listOf(Food.MANTA_RAY, Food.SHARK, Food.SWORDFISH, Food.LOBSTER)
val POSSIBLE_WEAPONS = SpecialAttackHandler.getAllWeaponIds()
val BOT_NAME_FUNCTION: (Int) -> String = { "R0b0t02473$it" }

val botList = ArrayList<Bot>()
var currentType: GameTestType? = null
var currentTask: CompletableFuture<*>? = null
var Player.botSpawnCount by Attr.int()


fun startCombatTest(plr: Player) {
    fun loadInventory(bot: Bot) {
        val food = Item(COMBAT_FOOD.random().id, rand(5, 25))
        bot.inventory.add(food)
        bot.inventory[0] = if (randBoolean()) Item(1215) else Item(POSSIBLE_WEAPONS.random())
        bot.inventory[1] =
            if (bot.combat.weapon.isRanged) Item(2444) else Item(2440) // Ranging potion(4) : Super strength(4)
        bot.inventory[2] = Item(2442) // Super defence(4)
        bot.inventory[3] = Item(2434) // Prayer potion(4)
    }

    fun loadRegularEquipment(bot: Bot) {
        bot.randomizeEquipment { def ->
            def.index == Equipment.HANDS || def.index == Equipment.CAPE || def.index == Equipment.BOOTS ||
                    def.index == Equipment.RING || def.index == Equipment.AMMUNITION ||
                    def.index == Equipment.AMULET ||
                    def.requirements.map { it.level > 40 }.isNotEmpty()
        }
        if (randBoolean()) {
            bot.equipment.amulet = if (randBoolean()) Item(1704) else Item(6585) // Amulet of fury/glory.
        }
    }

    fun checkEquipment(bot: Bot) {
        if (bot.equipment.weapon?.equipDef?.isTwoHanded == true) {
            bot.equipment.shield = null
        }
        if (bot.combat.weapon.isRanged) {
            if (bot.combat.ammoDef.isAmmoless && bot.equipment.weapon?.itemDef?.isStackable == true) {
                bot.equipment.weapon = Item(bot.equipment.weapon!!.id, 100_000)
            } else if (bot.combat.ammoDef.isNeedsWeapon) {
                bot.equipment.weapon = Item(bot.combat.ammoDef.weapons.last())
                bot.equipment.ammo = Item(bot.combat.ammoDef.ammo.random(), 100_000)
            }
        }
    }

    fun loadMageEquipment(bot: Bot) {
        bot.inventory[1] = Item(2442) // Super defence(4)
        bot.inventory[2] = Item(3040) // Magic potion(4)
        if (rand().nextBoolean()) {
            if (rand().nextBoolean()) {
                bot.equipment.head = Item(7396) // Wizard hat (t)
                bot.equipment.chest = Item(7392) // Wizard robe (t)
                bot.equipment.legs = Item(7388) // Blue skirt (t)
            } else {
                bot.equipment.head = Item(7394) // Wizard hat (g)
                bot.equipment.chest = Item(7390) // Wizard robe (g)
                bot.equipment.legs = Item(7386) // Blue skirt (g)
            }
            bot.equipment.amulet = Item(6585) // Amulet of fury
            bot.equipment.ring = Item(6731) // Seers ring
            bot.equipment.feet = Item(2579) // Wizard boots
        } else {
            bot.equipment.head = Item(6109) // Ghostly hood
            bot.equipment.chest = Item(6107)
            bot.equipment.legs = Item(6108)
            bot.equipment.amulet = Item(1727) // Amulet of magic
            bot.equipment.ring = Item(6731) // Seers ring
            bot.equipment.cape = Item(6111) // Ghostly cloak
            bot.equipment.hands = Item(6110) // Ghostly gloves
            bot.equipment.feet = Item(6106) // Ghostly boots
        }

        bot.combat.magic.isAutocasting = true
        var spells: List<CombatSpell> = listOf()
        if (randBoolean()) {
            bot.equipment.weapon = Item(4675) // Ancient staff
            bot.spellbook = Spellbook.ANCIENT
            spells = listOf(CombatSpell.ICE_BARRAGE,
                            CombatSpell.ICE_BLITZ,
                            CombatSpell.BLOOD_BARRAGE)

        } else if (randBoolean()) {
            bot.equipment.weapon = if (randBoolean()) Item(6563) else Item(3054)
            bot.spellbook = Spellbook.REGULAR
            spells =
                if (bot.equipment.weapon!!.id == 6563) {
                    listOf(CombatSpell.EARTH_BLAST,
                           CombatSpell.EARTH_WAVE,
                           CombatSpell.WATER_WAVE,
                           CombatSpell.WATER_BLAST)
                } else {
                    listOf(CombatSpell.FIRE_BLAST,
                           CombatSpell.FIRE_WAVE,
                           CombatSpell.EARTH_WAVE,
                           CombatSpell.EARTH_BLAST)
                }
        } else {
            bot.spellbook = Spellbook.REGULAR
            if (randBoolean()) {
                bot.equipment.weapon = Item(4675)
                spells = listOf(CombatSpell.IBAN_BLAST)
            } else if (randBoolean()) {
                bot.equipment.weapon = Item(2415)
                spells = listOf(CombatSpell.SARADOMIN_STRIKE)
            } else if (randBoolean()) {
                bot.equipment.weapon = Item(2416)
                spells = listOf(CombatSpell.CLAWS_OF_GUTHIX)
            } else if (randBoolean()) {
                bot.equipment.weapon = Item(2417)
                spells = listOf(CombatSpell.FLAMES_OF_ZAMORAK)
            }
        }
        bot.combat.magic.autocastSpell = spells.random().def
    }

    plr.sendMessage("Starting combat test...")
    currentType = GameTestType.COMBAT
    repeat(plr.botSpawnCount) {
        val spawn = PkBotScript.PkArea.ALL.random().anchors.random() // Spawn to random PK area anchor.
        val bot = Bot.Builder(ctx).setUsername(BOT_NAME_FUNCTION(it)).setSpawnPosition(spawn).setTemporary().build()
        bot.login().thenRun {
            bot.equipment.clear()
            bot.inventory.clear()
            bot.randomizeAppearance()
            bot.maxSkills()
            if (rand(1 of 3)) {
                loadMageEquipment(bot)
            } else {
                loadRegularEquipment(bot)
            }
            checkEquipment(bot)
            loadInventory(bot)
            bot.scriptStack.push(PkBotScript(bot, Duration.ofHours(24)))
            botList += bot
        }
    }
    plr.sendMessage("Combat test is now running.")
}

fun startWanderingTest(plr: Player, radius: Int) {
    plr.sendMessage("Starting wandering test...")
    currentType = GameTestType.IDLE
    val worldArea = Area.of(Position(3080, 3250), radius)
    repeat(plr.botSpawnCount) {
        val spawn =
            if (rand(1 of 3)) Position(3080, 3250) // Middle of the world.
            else if (rand(1 of 3)) TeleportSpell.VALUES.random().destination // Random spellbook teleport destination.
            else Luna.settings().game().startingPosition() // ::home position.

        val bot =
            Bot.Builder(ctx).setSpawnPosition(spawn).setUsername(BOT_NAME_FUNCTION(it)).setTemporary()
                .build()
        bot.login().thenRun {
            bot.submitAction(SmartWanderingAction(bot, worldArea, if (randBoolean()) WanderingFrequency.RAPID
            else WanderingFrequency.FAST))
            botList += bot
        }
    }
    plr.sendMessage("Wandering test is now running.")
}

fun startIdleTest(plr: Player) {
    plr.sendMessage("Starting idle test...")
    currentType = GameTestType.IDLE
    val area = Area.of(Position(3080, 3250), 1500)
    repeat(plr.botSpawnCount) {
        val bot =
            Bot.Builder(ctx).setSpawnPosition(area.randomPosition()).setUsername(BOT_NAME_FUNCTION(it)).setTemporary()
                .build()
        bot.login().thenRun { botList += bot }
    }
    plr.sendMessage("Idle test is now running.")
}

fun stopTest(plr: Player) {
    currentTask?.cancel(true)
    for (bot in botList) {
        bot.forceLogout()
    }
    currentType = null
    currentTask = null
    plr.botSpawnCount = 0
}

fun combatDialogue(plr: Player) {
    val dialogue = plr.newDialogue().text("Okay, so you'd like to spawn ${plr.botSpawnCount} bots and make",
                                          "them fight in the wilderness?")
        .options("Yes", { plr.overlays.closeWindows(); startCombatTest(plr) },
                 "No", { initialDialogue(plr) })
    botInputDialogue(plr, dialogue)
}

fun wanderingDialogue(plr: Player) {

    // sent after the radius is input
    fun radiusDialogue(radius: Int) {
        plr.newDialogue().text("Okay, so you'd like to spawn ${plr.botSpawnCount} bots and make them",
                               "wander within a $radius tile radius?")
            .options("Yes", { plr.overlays.closeWindows(); startWanderingTest(plr, radius) },
                     "No", { initialDialogue(plr) }).open()
    }

    // sent after the count is input
    val dialogue =
        plr.newDialogue().text("Okay, so you'd like to spawn ${plr.botSpawnCount} bots and perform a wandering test.",
                               "How far should they wander? (max = 1500)").then {
            plr.overlays.open(object : NumberInput() {
                override fun input(player: Player, value: Int) {
                    radiusDialogue(value.coerceAtLeast(1500))
                }
            })
        }

    // send enter dialogue stack
    botInputDialogue(plr, dialogue)
}

fun idleDialogue(plr: Player) {
    val dialogue =
        plr.newDialogue().text("Okay, so you'd like to spawn ${plr.botSpawnCount} bots and perform an idle test?")
            .options("Yes", { plr.overlays.closeWindows(); startIdleTest(plr) },
                     "No", { initialDialogue(plr) })
    botInputDialogue(plr, dialogue)
}

fun botInputDialogue(plr: Player, builder: DialogueQueueBuilder) {
    plr.newDialogue().text("How many bots would you like to spawn?")
        .then {
            plr.overlays.open(object : NumberInput() {
                override fun input(player: Player, value: Int) {
                    plr.botSpawnCount = value
                    builder.open()
                    println("???")
                }
            })
        }.open()
}

fun initialDialogue(plr: Player) {
    plr.newDialogue().text("This is used to stress test Luna using bots processed by the server.",
                           "Which test would you like to perform?")
        .options("Combat test", { combatDialogue(plr) },
                 "Wandering test", { wanderingDialogue(plr) },
                 "Idle test", { idleDialogue(plr) }).open()
}

cmd("test") {
    if (currentType != null) {
        plr.newDialogue().text("There is currently a $currentType type test running.",
                               "Would you like to stop it?")
            .options("Yes", { stopTest(plr) },
                     "No", { plr.overlays.closeWindows() }).open()
    } else {
        initialDialogue(plr)
    }
}