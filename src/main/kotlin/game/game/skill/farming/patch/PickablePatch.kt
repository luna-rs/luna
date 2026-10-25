package game.skill.farming.patch

import game.skill.farming.action.*
import game.skill.farming.fruitTree.*
import game.skill.farming.special.*

/**
 * A patch whose fully grown crop is picked one at a time and grows back, such as a [FruitTreePatch] or a
 * [CactusPatch]. See [game.skill.farming.action.PickFruitAction].
 *
 * @author hydrozoa
 */
interface PickablePatch {

    /**
     * @return True if there is something to pick.
     */
    fun canPick(): Boolean

    /**
     * Picks one.
     */
    fun pick()
}
