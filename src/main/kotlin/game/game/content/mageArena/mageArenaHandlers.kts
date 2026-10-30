package game.content.mageArena

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Shop

npc1(905) { MageArenaDialogue.kolodion(plr) }
npc1(904) { MageArenaDialogue.guardian(plr) }
npc3(904) { MageArenaDialogue.staffShop(plr) }
npc1(902) { MageArenaDialogue.gundai(plr) }
npc2(902) { plr.bank.open() }
npc1(903) { MageArenaDialogue.lundail(plr) }
npc3(903) { plr.overlays.open(io.luna.game.model.item.shop.ShopInterface(world, "Lundail's Arena-side Rune Shop.")) }
on(LogoutEvent::class) { MageArena.clear(plr) }

for (god in MageArena.God.entries) object1(god.statue) { MageArena.pray(plr, god) }

object1(2880) {
    if (plr.y > gameObject.position.y) {
        if (!MageArena.completed(plr)) plr.sendMessage("You cannot enter without the permission of Kolodion.")
        else if (!MageArena.equipmentAllowed(plr)) plr.sendMessage("You cannot enter the arena while carrying weapons or armour.")
        else { plr.move(plr.position.translate(0, -1)); plr.sendMessage("You pass through the mystical barrier.") }
    } else { plr.move(gameObject.position.translate(0, 1)); plr.sendMessage("You pass through the mystical barrier.") }
}

// Titles and pricing are from 377 NPC params. Counts are a Luna stock policy: the pinned source has no 377 inv list.
ShopHandler.create("Lundail's Arena-side Rune Shop.") {
    stockPricing = Shop.StockPricing(0.6, 0.03)
    sell { 556 x 1000; 555 x 1000; 557 x 1000; 554 x 1000; 558 x 1000; 559 x 1000; 562 x 100; 561 x 100; 560 x 100; 563 x 100; 565 x 100 }
    botAccess = { MageArena.cave(position) }
}
ShopHandler.create("Mage Arena Staffs.") {
    buy = BuyPolicy.NONE
    stockPricing = Shop.StockPricing(0.0, 0.02)
    sell { 2415 x 5; 2416 x 5; 2417 x 5 }
    botAccess = { MageArena.stage(this) >= MageArena.STAFF_GIVEN && MageArena.chamber(position) }
}

for (id in 907..914) combat(id) {
    attack {
        val close = world.collisionManager.reached(other, npc, InteractionPolicy.STANDARD_SIZE)
        val spell = MageArenaCombat.spell(npc.id, close)
        val animation = when (npc.id) { 908 -> 132; 909 -> 147; 910 -> 812; 911 -> 69; else -> 811 }
        object : MagicCombatAttack<io.luna.game.model.mob.Npc>(npc, other, Animation(animation), null, spell.def.projectile,
            spell.def.endGraphic, spell.def.endSound, spell, 5, 5) {
            override fun onAttack(damage: CombatDamage): CombatDamage? {
                val player = other as? Player
                if (npc.id in 912..914 && player != null && !MageArenaCombat.mayAttack(npc, player)) {
                    if (npc.combat.target === player) npc.combat.target = null
                    if (MageArenaCombat.matchingEquipment(npc, player) && kotlin.random.Random.nextInt(10) == 0) {
                        val name = when (npc.id) { 912 -> "Zamorak"; 913 -> "Saradomin"; else -> "Guthix" }
                        npc.speak("Hail $name!")
                    }
                    return null
                }
                return damage
            }
            override fun attack() {
                (other as? Player)?.let { MageArenaCombat.castEffect(it, spell, nextDamage.isAccurate) }
                super.attack()
            }
        }
    }
    defend {
        if (npc is MageArenaNpc && damage?.type != CombatDamageType.MAGIC) {
            damage = null
            if (other is Player) other.sendMessage("In my arena hand to hand combat is useless.")
        }
        if (npc.id in 912..914 && other is Player && !MageArena.completed(other)) {
            damage = null
            other.sendMessage("You are not ready to fight the battle mages.")
        }
    }
}

for (id in 907..911) DeathHookHandler.addNpcHook(id) {
    val boss = victim
    if (boss is MageArenaNpc) {
        preDeath { boss.animation(Animation(boss.combatDef().deathAnimation)) }
        death { MageArena.defeated(boss, source) }
    } else DeathHookHandler.defaultNpcHook?.invoke(this)
}
