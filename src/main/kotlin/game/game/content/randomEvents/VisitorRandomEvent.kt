/*
 * Random-event behavior adapted from LostCityRS/Content (377-wip) and LostCityRS/Engine-TS,
 * engine commit 1d25566cb53e7af1b1cb18ade8af996316c19614.
 * MIT License
 *
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package game.content.randomEvents

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.combat.attack.MeleeCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import game.content.randomEvents.RandomEvents.Kind

/**
 * Lost City's personal gift visitors share following and owner-only rewards. The genie and Old Man
 * give three warnings before a failure teleport; the Drunken Dwarf gives four before throwing stones.
 * Friendly visitors follow across plane changes and long-distance movement while the owner's area permits it.
 * Logout detaches the NPC. Only source-approved visitors restart on login; the dwarf does not.
 *
 * @author lare96
 */
class VisitorRandomEvent(
    override val player: Player,
    override val npc: Npc,
    val kind: Kind = Kind.GENIE
) : RandomEventEncounter {
    override val resumeKey get() = kind.name
    override val blocksNewEvents get() = !answered && !(kind == Kind.DRUNKEN_DWARF && failing)

    /**
     * Whether the owner can still claim this visitor's gift. Once failure begins, a late click
     * cannot reverse a pending failure teleport. The dwarf can still be answered after becoming angry,
     * matching its source conversation; an already launched stone follows the normal impact lifecycle.
     */
    override val awaitingResponse get() = !answered && !leaving && (!failing || kind == Kind.DRUNKEN_DWARF)

    /**
     * Warning age, reward latch and terminal failure state belong only to this live encounter.
     */
    private var age = 0
    private var answered = false
    private var disappearing = 0
    private var failing = false
    private var failureAge = 0
    private var leaving = false

    /**
     * Gendered source address, shared by the greeting, warnings and reward dialogue.
     */
    private val address get() = if (player.appearance.isMale) "master" else "mistress"

    init {
        require(kind != Kind.SWARM && kind != Kind.STRANGE_PLANT) { "Swarms use their hostile encounter lifecycle." }
        npc.interact(player)
        npc.navigator.follow(player)
        if (kind != Kind.DRUNKEN_DWARF) smoke(npc.position)
        if (kind == Kind.GENIE) {
            player.playSound(Sound.GENIE_APPEAR)
            npc.speak("Greetings $address ${player.username}!")
        } else if (kind == Kind.MYSTERIOUS_OLD_MAN) {
            player.playSound(Sound.SMOKEPUFF)
            npc.animation(Animation(858))
            npc.speak("Greetings ${player.username}!")
        } else {
            npc.animation(Animation(105))
            npc.speak("'Ello der ${player.username}! *hic*")
        }
    }

    override fun tick(): Boolean {
        if (npc.state != EntityState.ACTIVE) return false
        if (answered) return ++disappearing < 2
        if (leaving) return ++disappearing < 2
        if (failing) {
            if (kind == Kind.DRUNKEN_DWARF) {
                if (npc.position.z != player.position.z ||
                    npc.combat.target == null && !npc.position.isWithinDistance(player.position, 3)) {
                    leaving = true
                    npc.combat.target = null
                    npc.navigator.cancel()
                    npc.speak("Aww, me is all alone.")
                } else if (npc.combat.target !== player) {
                    npc.combat.attack(player)
                }
                return true
            }
            // Player queues in Lost City wait for delays and interfaces before their teleport step.
            if (++failureAge > 1 && RandomEvents.canAct(player)) {
                smoke(player.position)
                player.playSound(Sound.SMOKEPUFF2)
                player.moveKeepingWindows(failureDestinations.random())
                return false
            }
            return true
        }
        if (npc.position.z != player.position.z || !npc.position.isWithinDistance(player.position, 15)) {
            val position = RandomEvents.nearbySquare(player) ?: return false
            npc.move(position)
            smoke(npc.position)
            player.playSound(Sound.SMOKEPUFF)
        }
        if (npc.navigator.currentTarget !== player) npc.navigator.follow(player)
        if (kind == Kind.DRUNKEN_DWARF) {
            if (++age % 10 != 0) return true
            val warning = when (age / 10) {
                1 -> 103 to "Aww comeon, talk to ikle me ${player.username}!"
                2 -> 105 to "Oi, are you der ${player.username}!"
                3 -> -1 to "Dunt ignore your mate, ${player.username}!"
                4 -> 103 to "I hates yoo ${player.username}!"
                else -> null
            }
            if (warning != null) {
                if (warning.first != -1) npc.animation(Animation(warning.first))
                npc.speak(warning.second)
            } else {
                failing = true
                RandomEvents.answered(this)
                npc.navigator.cancel()
                // The hostile source checks loss of its target, rather than returning to its original spawn.
                npc.combat.retreatPolicy = RetreatPolicy.NONE
                npc.combat.attack(player)
            }
            return true
        }
        if (++age % 20 != 0) return true
        when (age / 20) {
            1 -> {
                npc.animation(Animation(863))
                npc.speak(if (kind == Kind.GENIE) "Ehem... $address ${player.username}?"
                    else "ehem... Hello ${player.username}!")
            }
            2 -> {
                npc.animation(Animation(857))
                npc.speak(if (kind == Kind.GENIE) "Are you there, $address ${player.username}?"
                    else "Hello, are you there ${player.username}!")
            }
            3 -> {
                npc.animation(Animation(859))
                npc.speak(if (kind == Kind.GENIE) "You shouldn't ignore a Genie $address ${player.username}?"
                    else "It's really rude to ignore someone, ${player.username}!")
            }
            else -> {
                failing = true
                RandomEvents.answered(this)
                npc.animation(Animation(714))
                npc.speak(if (kind == Kind.GENIE) "No one ignores me!" else "No-one ignores me!")
            }
        }
        return true
    }

    override fun talk(visitor: Player) {
        if (kind == Kind.DRUNKEN_DWARF && visitor !== player) {
            visitor.newDialogue().npc(npc.id, "You're not my matey!").open()
            return
        }
        if (visitor !== player) {
            visitor.newDialogue().npc(npc.id,
                "Sorry, but I'm trying to talk to ${player.username}.",
                if (kind == Kind.GENIE) "However, they appear to be ignoring me..."
                else "However ${if (player.appearance.isMale) "he" else "she"} appears to be ignorning me...").open()
            return
        }
        if (!RandomEvents.owns(this) || leaving || failing && kind != Kind.DRUNKEN_DWARF) return
        if (answered) {
            player.newDialogue().npc(npc.id,
                when (kind) {
                    Kind.GENIE -> "Have a nice day, $address."
                    Kind.MYSTERIOUS_OLD_MAN -> "Goodbye."
                    Kind.DRUNKEN_DWARF -> "'Ave a good 'un buddy!"
                    Kind.SWARM, Kind.STRANGE_PLANT -> error("Swarms cannot be gift visitors.")
                }).open()
            return
        }
        answered = true
        RandomEvents.answered(this)
        // Source inv_add does not bank gifts. Overflow remains an owned ground reward.
        for (reward in kind.rewards()) {
            if (!player.inventory.add(reward)) world.addItem(reward, player.position, player)
        }
        npc.navigator.cancel()
        if (kind == Kind.DRUNKEN_DWARF) npc.combat.target = null
        if (kind != Kind.DRUNKEN_DWARF) npc.animation(Animation(863))
        if (kind == Kind.GENIE) {
            player.newDialogue().npc(npc.id,
                "Ah, so you are there $address. I'm so glad you",
                "summoned me. Please take this lamp, and make your",
                "wish!").open()
        } else if (kind == Kind.MYSTERIOUS_OLD_MAN) {
            player.newDialogue().npc(npc.id,
                "Ah, so you are there. I hoped you would talk to me,",
                "I get so lonely. Here, have a present!",
                "I must be going now though.").open()
        } else {
            player.newDialogue().npc(npc.id,
                "I 'new it were you matey!",
                "'Ere, have some ob the good stuff!").open()
        }
    }

    override fun close() {
        npc.navigator.cancel()
        if (kind == Kind.DRUNKEN_DWARF) npc.combat.target = null
        if (npc.state != EntityState.INACTIVE) {
            if (kind != Kind.DRUNKEN_DWARF) {
                smoke(npc.position)
                if (player.state == EntityState.ACTIVE) player.playSound(Sound.SMOKEPUFF2)
            }
            world.removeNpc(npc)
        }
    }

    /**
     * Supplies the dwarf's stone through the native combat pipeline. The source uses melee accuracy
     * and crush defence despite the projectile, with a four-tick attack interval and one-tick damage delay.
     */
    internal fun stoneAttack(): MeleeCombatAttack<Npc> {
        npc.combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        return object : MeleeCombatAttack<Npc>(npc, player, 928, 1, 4) {
            override fun getNpcAttackSound(): Int = -1

            override fun onAttack(damage: CombatDamage?): CombatDamage? =
                damage?.takeIf { kind == Kind.DRUNKEN_DWARF && failing && !answered && !leaving &&
                    RandomEvents.owns(this@VisitorRandomEvent) && npc.combat.target === player && npc.state == EntityState.ACTIVE &&
                    player.state == EntityState.ACTIVE && player.isAlive }

            override fun attack() {
                npc.animation(Animation(928))
                player.playSound(Sound.THROWN)
                val distance = maxOf(kotlin.math.abs(npc.x - player.x), kotlin.math.abs(npc.y - player.y))
                LocalProjectile.followEntity(ctx).setSourceEntity(npc).setTargetEntity(player)
                    .setId(33).setStartHeight(45).setEndHeight(40).setTiming(51, 56 + 5 * distance)
                    .setInitialSlope(25).setLaunchOffset(11).build().display()
                player.combat.impacts.schedule(1, {
                    player.overlays.closeWindows()
                    player.submitAction(CombatDamageAction(nextDamage, this, true))
                }, { })
            }
        }
    }

    /**
     * Displays a world effect that remains visible when its NPC disappears in the same tick.
     */
    private fun smoke(position: Position) {
        LocalGraphic(ctx, 86, 124, 0, position, ChunkUpdatableView.globalView()).display()
    }

    companion object {
        /**
         * Members failure destinations decoded from macro_event_fail_teleports_members.
         * The general source dispatcher selects the members table for Luna's members content world.
         */
        internal val failureDestinations: List<Position> = listOf(
            Position(3278, 3500, 1),
            Position(3097, 3268, 1),
            Position(2954, 3388, 2),
            Position(3221, 3474, 3),
            Position(3229, 3223, 1),
            Position(3097, 9567, 0),
            Position(3287, 3190, 2),
            Position(2964, 3213, 1),
            Position(2519, 3426, 1),
            Position(2700, 3403, 2),
            Position(2536, 3076, 1),
            Position(2434, 3346, 0),
            Position(2787, 3515, 0),
            Position(2616, 3292, 1),
            Position(2898, 3423, 1),
            Position(2708, 3472, 1),
            Position(2663, 3111, 0),
            Position(2651, 3163, 1),
            Position(2772, 3235, 0),
            Position(2613, 3208, 1),
            Position(3104, 3163, 2),
            Position(3218, 9618, 0),
            Position(3263, 3424, 0)
        )
    }
}
