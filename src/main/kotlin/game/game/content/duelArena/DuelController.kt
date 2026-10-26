/*
 * Rules and interface identifiers adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
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
package game.content.duelArena

import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.event.impl.*
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Mob
import io.luna.game.model.item.Item
import io.luna.game.model.item.Equipment
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import engine.combat.prayer.CombatPrayer

/**
 * Enforces the agreed rules through the same controller checks used by real client actions.
 *
 * @author lare96
 */
class DuelController(private val player: Player, val duel: DuelArena.Duel) : PlayerController(player) {
    private val walkingLocked = player.walking.isLocked
    private val potions = Potion.entries.flatMap { listOf(it.fourDose, it.threeDose, it.twoDose, it.oneDose) }.toSet()
    private val specialButtons = setOf(7562, 7587, 7687, 7512, 7537, 7637, 7612, 7487, 8481, 7662, 7462, 7788, 12311)
    private fun rule(rule: DuelRules) = rule.enabled(duel.rules)

    override fun register() { process() }
    override fun combat(victim: Mob): Boolean = victim is Player && duel.fighting(player, victim)
    override fun teleport(action: TeleportAction): Boolean = false
    override fun logout(): Boolean { duel.finish(duel.other(player)); return true }
    override fun unregister() {
        player.walking.isLocked = walkingLocked
        if (!duel.ended) duel.finish(duel.other(player))
    }
    override fun move() { if (!duel.ended && !DuelArena.arena(player.position)) duel.finish(duel.other(player)) }
    override fun process() {
        player.walking.isLocked = walkingLocked || duel.countdown > 0 || rule(DuelRules.NO_MOVEMENT)
        if (player.walking.isLocked) { player.navigator.cancel(); player.walking.clear() }
        if (rule(DuelRules.NO_PRAYER)) player.combat.prayers.deactivateAll()
        if (rule(DuelRules.NO_SPECIAL)) player.combat.specialBar.forceOff()
    }
    override fun event(event: ControllableEvent): Boolean {
        if (event is WalkingEvent && (duel.countdown > 0 || rule(DuelRules.NO_MOVEMENT))) return false
        if (event is DropItemEvent || event is PlayerClickEvent.PlayerFourthClickEvent) return false
        if (event is ItemClickEvent.ItemFirstClickEvent) {
            if (rule(DuelRules.NO_POTIONS) && event.id in potions) return false
            if (rule(DuelRules.NO_FOOD) && event.id in Food.ID_TO_FOOD) return false
        }
        if (event is ButtonClickEvent) {
            if (rule(DuelRules.NO_PRAYER) && CombatPrayer.entries.any { it.button == event.id }) return false
            if (rule(DuelRules.NO_SPECIAL) && event.id in specialButtons) return false
        }
        if (rule(DuelRules.FLOWERS)) {
            if (event is EquipItemEvent && Item(event.itemId).equipDef?.index == Equipment.WEAPON && event.itemId !in 2460..2468) return false
            if (event is WidgetItemClickEvent && event.widgetId == 1688 && event.index == Equipment.WEAPON) return false
        }
        return true
    }
}
