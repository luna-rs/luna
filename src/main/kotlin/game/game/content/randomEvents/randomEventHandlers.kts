package game.content.randomEvents

import api.predef.*
import api.drops.DropTableHandler
import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.combat.death.DeathHookHandler
import engine.bot.speech.BotReactions
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.action.impl.ConsumeAction
import api.predef.ext.ticks
import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import game.player.item.consume.food.Food.Companion.lastEat
import game.content.randomEvents.RandomEvents.Kind
import game.player.Sound
import game.content.duelArena.DuelArena
import game.content.duelArena.DuelRules
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.PlayerTickEvent

// Register persistent attribute keys while loading scripts, before any player files are read.
val randomEvents = RandomEvents

// Event forms use the source table; the quest Tree spirit (655) retains its separate static loot.
SkillGuardianRandomEvent.Kind.TREE_SPIRIT.npcIds.forEach { id ->
    DropTableHandler.createNpc(id) { tables += TreeSpiritDropTable }
}

on(LoginEvent::class) { randomEvents.login(plr) }
on(LogoutEvent::class) { randomEvents.logout(plr) }
on(PlayerTickEvent::class) {
    randomEvents.restore(plr)
    randomEvents.tick(plr)
}

Kind.entries.filter { it != Kind.SWARM }.forEach { kind ->
    npc1(kind.npcId) { randomEvents.talk(plr, targetNpc) }
}

combat(Kind.DRUNKEN_DWARF.npcId) {
    attack {
        val encounter = randomEvents.ownerOf(npc) as? VisitorRandomEvent
        if (encounter?.kind == Kind.DRUNKEN_DWARF && other === encounter.player) encounter.stoneAttack()
        else melee(launch = { null })
    }
}

combat(Kind.SWARM.npcId) {
    attack {
        npc.combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee {
            val encounter = randomEvents.ownerOf(npc) as? SwarmRandomEvent
            if (it != null && encounter?.canAttack(other) == true) {
                encounter.player.playSound(Sound.SWARM_ATTACK)
                it
            } else null
        }
    }
}

combat(408) {
    damageExperienceMultiplier = 25.0 / 1000.0
    attack {
        npc.combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee {
            val encounter = randomEvents.ownerOf(npc) as? StrangePlantRandomEvent
            if (it != null && encounter?.canFight(other) == true && npc.combat.target === other) {
                encounter.player.playSound(Sound.TRIFFID_ATTACK)
                it
            } else null
        }
    }
    defend {
        val encounter = randomEvents.ownerOf(npc) as? StrangePlantRandomEvent
        damage = if (encounter?.canFight(other) == true) damage?.cappedAt(3) else null
    }
}

combat(*SkillGuardianRandomEvent.NPC_IDS.toList().toIntArray()) {
    damageExperienceMultiplier = 25.0 / 1000.0
    attack {
        npc.combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee {
            val encounter = randomEvents.ownerOf(npc) as? SkillGuardianRandomEvent
            it?.takeIf { encounter?.canAttack(other) == true }
        }
    }
    defend {
        val encounter = randomEvents.ownerOf(npc) as? SkillGuardianRandomEvent
        if (encounter?.canFight(other) != true) {
            damage = null
            animationId = null
        }
    }
}

for (id in SkillGuardianRandomEvent.NPC_IDS) {
    DeathHookHandler.addNpcHook(id) {
        preDeath { victim.animation(Animation(victim.combatDef().deathAnimation, Animation.AnimationPriority.IMMUTABLE)) }
        death {
            val encounter = randomEvents.ownerOf(victim) as? SkillGuardianRandomEvent
            if (encounter?.claimDeath(source) == true) {
                if (source is Bot) BotReactions.reactToKill(source, victim)
                drop()
            }
            victim.move(Position(1, 1))
        }
        postDeath { }
    }
}

playerStopAttack { attack ->
    val target = attack.victim as? Npc ?: return@playerStopAttack false
    val allowed = when (target.id) {
        408 -> (randomEvents.ownerOf(target) as? StrangePlantRandomEvent)?.canFight(this) == true
        in SkillGuardianRandomEvent.NPC_IDS -> (randomEvents.ownerOf(target) as? SkillGuardianRandomEvent)?.canFight(this) == true
        else -> null
    }
    if (allowed != false) false
    else {
        sendMessage("It's not after you.")
        true
    }
}

/**
 * Consumes Strange fruit through the shared eating throttle and native inventory slot checks.
 * It restores thirty run energy, cures poison and supplies brief saved poison resistance without
 * reducing longer potion protection. This utility fruit is separate from healing-food selection.
 *
 * @author lare96
 */
fun consumeFruit(player: Player, slot: Int) {
    val duel = DuelArena.session(player)
    if (duel != null && duel.started && !duel.ended && DuelRules.NO_FOOD.enabled(duel.rules)) {
        player.sendMessage("Use of food has been turned off for this duel.")
        return
    }
    player.submitAction(object : ConsumeAction(player, player.lastEat, 2) {
        override fun execute() {
            if (!player.inventory.remove(slot, Item(464))) return
            player.sendMessage("You eat the strange fruit.")
            player.animation(Animation(829))
            player.playSound(Sound.EAT)
            player.increaseRunEnergy(30.0)
            player.status.remove(StatusEffectType.POISONED)
            player.status.add(BasicStatusEffect(player, StatusEffectType.FRUIT_ANTIPOISON, 30.ticks,
                                                persistent = true, refreshable = true))
            player.combat.addFoodDelay(3)
        }
    })
}

item1(464) { consumeFruit(plr, index) }

cmd("random_event", RIGHTS_ADMIN) {
    val name = args.firstOrNull()?.lowercase()
    val kind = when (name) {
        "plant" -> Kind.STRANGE_PLANT
        "swarm" -> Kind.SWARM
        "genie" -> Kind.GENIE
        "old_man" -> Kind.MYSTERIOUS_OLD_MAN
        "dwarf" -> Kind.DRUNKEN_DWARF
        else -> null
    }
    val guardian = when (name) {
        "river_troll" -> SkillGuardianRandomEvent.Kind.RIVER_TROLL
        "tree_spirit" -> SkillGuardianRandomEvent.Kind.TREE_SPIRIT
        else -> null
    }
    if (kind == null && guardian == null) {
        plr.sendMessage("Usage: ::random_event genie|old_man|dwarf|swarm|plant|river_troll|tree_spirit")
    } else if (!(if (guardian != null) randomEvents.startGuardian(plr, guardian) else randomEvents.startEncounter(plr, kind!!))) {
        plr.sendMessage("A random event cannot start here right now.")
    }
}
