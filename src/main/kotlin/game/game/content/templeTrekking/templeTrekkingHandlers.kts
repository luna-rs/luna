package game.content.templeTrekking

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import game.content.templeTrekking.TempleTrekking.trek
import game.player.Sound
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack
import kotlin.random.Random

// Register the saved trek state before any player files are read.
val tokens = TempleTrekking
val backpack = TrekEvents

on(LoginEvent::class) { TempleTrekking.login(plr) }

/*
 * The villagers: in Burgh de Rott they talk about the trek; on the trek they chat, and take food before it.
 */
val TREK_LINES = listOf("Oh, I hate this trip, but it's better than staying in Burgh de Rott.",
                        "It's scary here...let's get going!",
                        "When are we going to get to Paterdomus?",
                        "We don't have all day to stand around chatting; let's press on.",
                        "Do you visit Paterdomus often? You seem to know the route well.")

fun chat(plr: Player, npc: Npc, villager: TrekVillager) {
    if (npc is TrekFollower) {
        if (npc.owner !== plr) return
        val line = if (npc.combat.target != null || plr.combat.target != null) "Can't you see I'm being attacked...?"
        else TREK_LINES.random()
        plr.newDialogue().npc(villager.npc, line).open()
    } else {
        TempleTrekking.talk(plr, villager)
    }
}

/**
 * Packs food into the villagers' shared backpack before a trek. During an event they won't take it.
 */
fun giveFood(plr: Player, npc: Npc, villager: TrekVillager, index: Int) {
    if (npc is TrekFollower || plr.trek != null) {
        plr.sendMessage("Nothing interesting happens.")
        return
    }
    val item = plr.inventory[index] ?: return
    val food = TrekEvents.food(plr)
    val space = TrekEvents.BACKPACK - food.size
    if (space <= 0) {
        plr.newDialogue().npc(villager.npc, "My backpack's full, thanks. I can't carry any more.").open()
        return
    }
    plr.newDialogue()
        .npc(villager.npc, "Ah, so you want to give me some supplies, eh? Let me just open me backpack.")
        .options("Give all of this food.", {
            val count = minOf(space, it.inventory.computeAmountForId(item.id))
            repeat(count) { _ -> it.inventory.remove(Item(item.id)) }
            val packed = TrekEvents.food(it)
            repeat(count) { _ -> packed += item.id }
            TrekEvents.setFood(it, packed)
            it.sendMessage("You put $count ${itemDef(item.id).name.lowercase()} into the backpack. It has room " +
                               "for ${TrekEvents.BACKPACK - packed.size} more.")
        }, "Not right now.", { it.newDialogue().player("Not right now actually.").open() })
        .open()
}

for (villager in TrekVillager.entries) {
    for (id in listOf(villager.parent, villager.npc)) {
        npc1(id) { chat(plr, targetNpc, villager) }
        for (food in TrekEvents.FOODS) {
            useItem(food).onNpc(id) { giveFood(plr, targetNpc, villager, usedItemIndex) }
        }
    }
}

/*
 * The route map.
 */
for (route in TrekRoute.entries) {
    button(route.button) { TempleTrekking.chooseRoute(plr, route) }
}
button(TempleTrekking.CLOSE_BUTTON) { TempleTrekking.closeMap(plr) }

/*
 * The ways out of an event room.
 */
// The paths lie on the edge of the swamp, so they're used from beside them.
object2(13832, { _, _ -> TrekPuzzles.NEXT_TO }) { TrekEvents.controller(plr)?.continueTrek() }
object2(13831, { _, _ -> TrekPuzzles.NEXT_TO }) { TrekEvents.controller(plr)?.evade() }
object2(13833, { _, _ -> TrekPuzzles.NEXT_TO }) { TrekEvents.controller(plr)?.escape() }
object4(13864) { TrekEvents.controller(plr)?.continueTrek() }
object5(13864) { TrekEvents.controller(plr)?.escape() }

/*
 * Monsters: kills count towards the trek, and swamp snakes leave corpses to skin.
 */
val SNAKES = listOf(3599, 3600, 3601)
val CORPSES = listOf(3603, 3604, 3605)
val SNAKE_HIDE = 7801
val MONSTERS = SNAKES + (3606..3622).toList() + Juvinates.IDS

for (id in MONSTERS) {
    DeathHookHandler.addNpcHook(id) {
        death {
            val monster = victim as? TrekMonster
            if (monster != null && !monster.beaten) {
                TrekEncounter.of(monster)?.killed(monster, source as? Player)
            }
            // A swamp snake's body stays where it fell, to be skinned.
            val index = SNAKES.indexOf(victim.id)
            if (index != -1) {
                val at = victim.position
                world.scheduleOnce(2) {
                    val corpse = Npc(ctx, CORPSES[index], at)
                    corpse.respawnTicks = -1
                    world.npcs.add(corpse)
                }
            }
        }
        DeathHookHandler.defaultNpcHook?.invoke(this)
    }
}

for (corpse in CORPSES) {
    useItem(TrekPuzzles.KNIFE).onNpc(corpse) {
        if (targetNpc.state != EntityState.ACTIVE) return@onNpc
        val hides = Random.nextInt(2, 6)
        world.removeNpc(targetNpc)
        plr.animation(Animation(1235))
        plr.playSound(Sound.TREK_SKIN_SNAKE)
        plr.giveItem(Item(SNAKE_HIDE, hides))
        plr.sendMessage("You skin the dead snake.")
    }
}

// Giant snails spit acid from range.
combat(3612, 3613, 3614) {
    attack {
        ranged(Graphic(627, 0, 0), { from, to ->
            LocalProjectile.followEntity(ctx).setSourceEntity(from).setTargetEntity(to).setId(628)
                .setStartHeight(30).setEndHeight(20).setTicksToStart(30).setTicksToEnd(55).setInitialSlope(10)
                .build()
        }, animationId = 3724, range = 7) {
            other.graphic(Graphic(629, 0, 60))
            it
        }
    }
}

/*
 * Ghasts: a filled druid pouch shows a hidden one.
 */
for (hidden in 3606..3608) {
    useItem(TrekEncounter.Ghasts.DRUID_POUCH).onNpc(hidden) {
        val ghast = targetNpc as? TrekMonster ?: return@onNpc
        (TrekEvents.controller(plr)?.encounter as? TrekEncounter.Ghasts)?.reveal(plr, ghast)
    }
}

/*
 * Juvinates: only silver hurts them, and Guthix balance works on one the rod holds.
 */
combat(*Juvinates.IDS.toIntArray()) {
    defend {
        val silver = (source as? PlayerMeleeCombatAttack)?.launchedWeaponId?.let { Juvinates.isSilver(it) } == true
        if (!silver) {
            damage = null
            if (other is Player) other.sendMessage("Your attack doesn't seem to hurt the vampyre.")
        }
    }
}
for (id in Juvinates.IDS) {
    for (balance in Juvinates.BALANCE) {
        useItem(balance).onNpc(id) { Juvinates.pour(plr, targetNpc, usedItemIndex) }
    }
}

/*
 * Abidor Crank.
 */
npc1(TrekEncounter.AbidorCrank.ABIDOR) {
    (TrekEvents.controller(plr)?.encounter as? TrekEncounter.AbidorCrank)?.talk(targetNpc)
}

/*
 * The bog.
 */
val BLADES = io.luna.game.model.def.ItemDefinition.ALL.filterNotNull().filter {
    val name = it.name.lowercase()
    !it.unnotedId.isPresent && !name.contains("pickaxe") &&
        listOf("knife", " axe", "battleaxe", "sword", "scimitar", "dagger", "whip", "sickle", "machete", "halberd")
            .any { blade -> name.contains(blade) || name == blade.trim() }
}.map { it.id() }

for (blade in BLADES) {
    useItem(blade).onObject(TrekPuzzles.SPINY_BUSH) {
        TrekPuzzles.puzzle<TrekPuzzles.Bog>(plr)?.cutBranches(plr)
    }
}
useItem(TrekPuzzles.BRANCH).onObject(TrekPuzzles.BOG, { _, _ -> TrekPuzzles.NEXT_TO }) {
    TrekPuzzles.puzzle<TrekPuzzles.Bog>(plr)?.prod(plr, gameObject, usedItemIndex)
}
object1(TrekPuzzles.BOG, { _, _ -> TrekPuzzles.NEXT_TO }) {
    TrekPuzzles.puzzle<TrekPuzzles.Bog>(plr)?.standOn(plr, gameObject)
}

/*
 * The river.
 */
object1(TrekPuzzles.BACKPACK) { TrekPuzzles.puzzle<TrekPuzzles.River>(plr)?.search(plr) }
for (tree in TrekPuzzles.VINE_TREES) {
    useItem(TrekPuzzles.KNIFE).onObject(tree) { TrekPuzzles.puzzle<TrekPuzzles.River>(plr)?.cutVine(plr, gameObject) }
}
useItem(TrekPuzzles.SHORT_VINE).onItem(TrekPuzzles.SHORT_VINE) {
    TrekPuzzles.puzzle<TrekPuzzles.River>(plr)?.twist(plr)
}
useItem(TrekPuzzles.LONG_VINE).onObject(TrekPuzzles.BIG_BRANCH, { _, _ -> TrekPuzzles.NEXT_TO }) {
    TrekPuzzles.puzzle<TrekPuzzles.River>(plr)?.lasso(plr, gameObject)
}
object2(TrekPuzzles.VINE_ON_BRANCH, { _, _ -> TrekPuzzles.NEXT_TO }) {
    TrekPuzzles.puzzle<TrekPuzzles.River>(plr)?.swing(plr)
}

/*
 * The broken bridge.
 */
for (stage in TrekPuzzles.BROKEN_BRIDGE..TrekPuzzles.FIXED_BRIDGE) {
    object1(stage) { plr.sendMessage(objectDef(gameObject.id).description) }
    if (stage == TrekPuzzles.FIXED_BRIDGE) continue
    for (log in TrekPuzzles.LOGS) {
        useItem(log).onObject(stage, { _, _ -> TrekPuzzles.NEXT_TO }) {
            TrekPuzzles.puzzle<TrekPuzzles.Bridge>(plr)?.mend(plr, gameObject, usedItemIndex)
        }
    }
}
object2(TrekPuzzles.FIXED_BRIDGE, { _, _ -> TrekPuzzles.NEXT_TO }) {
    TrekPuzzles.puzzle<TrekPuzzles.Bridge>(plr)?.walkAcross(plr)
}

/*
 * Rewards: Cornelius, the tokens and the tomes.
 */
npc1(TempleTrekking.CORNELIUS, { _, _ -> engine.bank.Banking.BANKER_POLICY }) { TrekRewards.talkToBanker(plr) }
for (token in TempleTrekking.TOKENS) {
    useItem(token).onNpc(TempleTrekking.CORNELIUS, { _, _ -> engine.bank.Banking.BANKER_POLICY }) {
        TrekRewards.offerExchange(plr)
    }
    item1(token) {
        val route = TrekRoute.forToken(id) ?: return@item1
        plr.sendMessage("A reward token for guiding a villager to Paterdomus by route ${route.level}. " +
                            "Cornelius at the Burgh de Rott bank exchanges it.")
    }
}
for (tome in TrekRewards.Tome.BY_ID.keys) {
    item1(tome) { TrekRewards.read(plr, id, index) }
}
