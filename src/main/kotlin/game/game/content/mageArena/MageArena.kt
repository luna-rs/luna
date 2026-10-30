/*
 * Adapted from LostCityRS/Content scripts/areas/area_mage_arena and skill_combat,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 * MIT License
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
 */
package game.content.mageArena

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.scheduleOnce
import game.player.Sound
import game.skill.magic.Staff
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * Persistent Kolodion progression, the three gods' rewards and spell training for revision 377.
 * Runtime encounters have one combat owner; leaving, death and logout keep only the last defeated form.
 * The pinned source unlocks spells on cast 99 (98 is its last-cast sentinel), including splashes.
 *
 * @author lare96
 */
object MageArena {
    const val COMPLETE = 6
    const val STAFF_GIVEN = 8
    const val SPELL_CASTS = 99
    val START = Position(3105, 3934)
    val BANK = Position(2539, 4712)
    val FINISH = Position(2540, 4716)
    val CHAMBER = Position(2509, 4689)
    val capes = setOf(2412, 2413, 2414)
    private var Player.activeBoss by Attr.nullableObj(MageArenaNpc::class)
    private var Player.pendingCape by Attr.nullableObj(GroundItem::class)

    /**
     * The cache identities, persistent training word and exact statue reward positions.
     *
     * @author lare96
     */
    enum class God(val statue: Int, val cape: Int, val staff: Int, val spell: CombatSpell,
                   val counter: PersistentVarp, val capeTile: Position) {
        SARADOMIN(2873, 2412, 2415, CombatSpell.SARADOMIN_STRIKE, PersistentVarp.SARADOMIN_CASTS, Position(2500, 4720)),
        GUTHIX(2875, 2413, 2416, CombatSpell.CLAWS_OF_GUTHIX, PersistentVarp.GUTHIX_CASTS, Position(2507, 4723)),
        ZAMORAK(2874, 2414, 2417, CombatSpell.FLAMES_OF_ZAMORAK, PersistentVarp.ZAMORAK_CASTS, Position(2516, 4720))
    }

    fun stage(player: Player): Int = player.varpManager.getValue(PersistentVarp.MAGE_ARENA).coerceIn(0, STAFF_GIVEN)
    fun setStage(player: Player, value: Int) = player.varpManager.setAndSendValue(PersistentVarp.MAGE_ARENA, value.coerceIn(0, STAFF_GIVEN))
    fun completed(player: Player): Boolean = stage(player) >= COMPLETE
    fun casts(player: Player, god: God): Int = player.varpManager.getValue(god.counter).coerceIn(0, SPELL_CASTS)
    fun encounter(player: Player): MageArenaNpc? = player.activeBoss?.takeIf { it.state == EntityState.ACTIVE }

    /**
     * Matches the source's four coordinate-pair rectangles, rather than the entire Wilderness map square.
     */
    fun arena(position: Position): Boolean = position.z == 0 && (
        position.x in 3084..3126 && position.y in 3919..3944 ||
        position.x in 3088..3123 && position.y in 3944..3948 ||
        position.x in 3093..3118 && position.y in 3949..3953 ||
        position.x in 3089..3121 && position.y in 3914..3918)

    fun cave(position: Position): Boolean = position.z == 0 && position.x in 2529..2547 && position.y in 4704..4727
    fun chamber(position: Position): Boolean = position.z == 0 && position.x in 2496..2522 && position.y in 4678..4727
    fun live(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive

    /**
     * Luna's equipment categories distinguish magical robes by their magic bonus. Unlike physical armour,
     * robes and staves are admitted. This is the supported equivalent of the reference's category exclusions;
     * its barrier comments explicitly identify the restriction text as reconstructed from RSC.
     */
    fun equipmentAllowed(player: Player): Boolean = (player.inventory.asSequence() + player.equipment.asSequence())
        .filterNotNull().all { item ->
            val definition = EquipmentDefinition.ALL.get(item.id).orElse(null)
            when (definition?.index) {
                Equipment.WEAPON -> item.id in Staff.ID_TO_STAFF || item.itemDef.name.contains("staff", true)
                Equipment.HEAD, Equipment.CHEST, Equipment.LEGS, Equipment.HANDS, Equipment.SHIELD -> definition.getBonus(3) > 0
                Equipment.AMMUNITION -> false
                else -> true
            }
        }

    fun canStart(player: Player): Boolean = live(player) && player.skill(Skill.MAGIC).staticLevel >= 60 && equipmentAllowed(player)

    /**
     * Starts or resumes only the saved undefeated form. A duplicate conversation cannot replace a live boss.
     */
    fun start(player: Player): Boolean {
        if (!cave(player.position) || completed(player) || !canStart(player)) return false
        if (encounter(player) != null) return false
        if (stage(player) == 0) setStage(player, 1)
        player.overlays.closeWindows()
        player.combat.target = null
        player.move(START)
        spawn(player)
        return true
    }

    private fun spawn(player: Player) {
        if (!live(player) || !arena(player.position) || stage(player) !in 1..5 || encounter(player) != null) return
        val size = if (stage(player) == 3 || stage(player) == 5) 3 else 1
        val position = (2..4).flatMap { radius ->
            listOf(player.position.translate(radius, 0), player.position.translate(-radius, 0),
                player.position.translate(0, radius), player.position.translate(0, -radius))
        }.firstOrNull { tile ->
            (0 until size).all { x -> (0 until size).all { y ->
                val part = tile.translate(x, y)
                arena(part) && !player.world.collisionManager.isBlocked(part, false)
            } }
        } ?: return
        val boss = MageArenaNpc(player, stage(player), position)
        player.activeBoss = boss
        if (!player.world.npcs.add(boss)) { player.activeBoss = null; return }
        boss.speak(if (boss.form == 1) "You must prove yourself... now!" else "Let us continue with our battle")
        boss.combat.attack(player)
    }

    /**
     * Advances once for the owner of the current form; ordinary NPC drops and respawn never run.
     */
    fun defeated(boss: MageArenaNpc, source: Mob?) {
        val player = boss.owner
        if (player.activeBoss !== boss || stage(player) != boss.form || !live(player) || !arena(player.position) || source !== player) {
            boss.world.npcs.remove(boss)
            return
        }
        setStage(player, boss.form + 1)
        player.combat.target = null
        boss.world.npcs.remove(boss)
        if (completed(player)) {
            player.move(FINISH)
            player.sendMessage("Well done, young adventurer; you truly are a worthy battle mage.")
            player.sendMessage("Step into the magic pool. It will take you to a chamber.")
        } else {
            player.world.scheduleOnce(3) {
                if (live(player) && arena(player.position) && encounter(player) == null) spawn(player)
            }
        }
    }

    fun finished(boss: MageArenaNpc) {
        if (boss.owner.activeBoss === boss) boss.owner.activeBoss = null
    }

    fun clear(player: Player) {
        player.activeBoss?.let { player.world.npcs.remove(it) }
        player.activeBoss = null
    }

    fun ownedGod(player: Player): God? = God.entries.firstOrNull { god ->
        player.inventory.contains(god.cape) || player.equipment.contains(god.cape) || player.bank.contains(god.cape)
    }

    fun canTakeCape(player: Player): Boolean = ownedGod(player) == null

    /**
     * Creates the cape on the floor, including when the inventory is full. Failed registration changes no stage.
     */
    fun pray(player: Player, god: God): Boolean {
        if (!live(player) || !completed(player) || !chamber(player.position)) return false
        if (!canTakeCape(player) || player.pendingCape?.state == EntityState.ACTIVE || God.entries.any { other ->
                player.world.items.findAll(other.capeTile).anyMatch {
                    it.id in capes && it.state == EntityState.ACTIVE && it.isVisibleTo(player)
                }
            }) {
            player.sendMessage("You kneel and chant to ${god.name.lowercase().replaceFirstChar { it.uppercase() }}...")
            player.sendMessage("... but there is no response.")
            return false
        }
        val item = GroundItem(player.context, god.cape, 1, god.capeTile, ChunkUpdatableView.localView(player))
        if (!player.world.items.register(item)) return false
        player.pendingCape = item
        if (stage(player) == COMPLETE) setStage(player, 7)
        player.animation(Animation(645))
        player.sendMessage("You feel a rush of energy charge through your veins. Suddenly a cape appears before you.")
        player.world.scheduleOnce(200) { if (item.state == EntityState.ACTIVE) player.world.items.unregister(item) }
        return true
    }

    /**
     * Gives the first matching staff once, falling back to an owned ground drop when the inventory is full.
     */
    fun claimStaff(player: Player): Boolean {
        if (!live(player) || stage(player) != 7 || !chamber(player.position)) return false
        val god = ownedGod(player) ?: return false
        if (!player.inventory.hasSpaceFor(Item(god.staff))) {
            val drop = GroundItem(player.context, god.staff, 1, player.position, ChunkUpdatableView.localView(player))
            if (!player.world.items.register(drop)) return false
        } else if (!player.inventory.add(Item(god.staff))) return false
        setStage(player, STAFF_GIVEN)
        player.sendMessage("The guardian hands you an ornate magic staff.")
        return true
    }

    /**
     * Stops locked god spells before their rune costs; other combat spells are untouched.
     */
    fun canCast(player: Player, spell: CombatSpell): Boolean {
        val god = God.entries.firstOrNull { it.spell == spell } ?: return true
        if (stage(player) < STAFF_GIVEN) {
            player.sendMessage("This spell can only be learnt in the Mage Arena.")
            return false
        }
        if (!arena(player.position) && casts(player, god) < SPELL_CASTS) {
            player.sendMessage("You are not yet experienced enough to use this spell outside the")
            player.sendMessage("Mage Arena.")
            return false
        }
        return true
    }

    /**
     * Called only after native rune removal. PvM casts, including splashes, train each god independently.
     */
    fun recordCast(player: Player, spell: CombatSpell, victim: Mob?) {
        val god = God.entries.firstOrNull { it.spell == spell } ?: return
        if (victim is Player || victim == null || !arena(player.position) || stage(player) < STAFF_GIVEN) return
        val old = casts(player, god)
        if (old >= SPELL_CASTS) return
        player.varpManager.setAndSendValue(god.counter, old + 1)
        if (old + 1 == SPELL_CASTS) player.sendMessage("You can now cast ${spell.formattedName} outside the Arena.")
    }

    fun canEquip(player: Player, id: Int): Boolean {
        val god = God.entries.firstOrNull { it.cape == id || it.staff == id } ?: return true
        val conflict = if (id == god.cape) player.equipment.computeIdForIndex(Equipment.WEAPON)
                       else player.equipment.computeIdForIndex(Equipment.CAPE)
        val other = God.entries.any { it != god && (it.cape == conflict || it.staff == conflict) }
        if (other) {
            player.sendMessage(if (id == god.staff) "You may not wield this staff whilst wearing the cape of another god."
                               else "You may not wear this cape whilst wielding the staff of another god.")
            player.sendMessage("The conflicting powers of staff and cape drive them apart.")
        }
        return !other
    }

    fun dropCape(player: Player, index: Int): Boolean {
        val item = player.inventory[index] ?: return false
        val god = God.entries.firstOrNull { it.cape == item.id } ?: return false
        player.inventory[index] = null
        player.playSound(Sound.PUT_DOWN)
        player.sendMessage("${god.name.lowercase().replaceFirstChar { it.uppercase() }} reclaims the cape as it touches the ground.")
        return true
    }
}
