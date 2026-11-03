/*
 * Adapted from LostCityRS/Content scripts/quests/quest_zanaris and areas/area_lumbridge,
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
package game.content.lostCity

import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatRestriction

/**
 * The Dramen tree's level 101 guardian. Leaving, logout or timeout requires another fight, while a victory persists.
 *
 * @author lare96
 */
class DramenSpirit(val owner: Player, position: Position) : Npc(owner.context, LostCity.SPIRIT, position), CombatRestriction {
    init { respawnTicks = -1 }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun canFight(opponent: Mob): Boolean = opponent === owner && owner.state == EntityState.ACTIVE &&
        owner.isAlive && state == EntityState.ACTIVE && LostCity.stage(owner) == LostCity.SPOKEN_SHAMUS &&
        owner.z == z && owner.position.isWithinDistance(LostCity.SPIRIT_SPAWN, 15)

    override fun onSpawn() {
        var age = 0
        world.schedule(1) { task ->
            if (state != EntityState.ACTIVE) task.cancel()
            else if (!canFight(owner) || ++age >= 1000) {
                world.npcs.remove(this)
                task.cancel()
            }
        }
    }

    override fun onInactive() { LostCity.removed(this) }
}
