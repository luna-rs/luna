package api.combat.player.dsl

/**
 * Defines a conditional player defence combat listener.
 *
 * A filter contains two parts:
 * - [filter], which decides whether this listener should handle the current defence event.
 * - [defence], which runs when the filter passes.
 *
 * Instances are registered by calling [then]. This keeps the DSL readable by allowing code to first define the
 * condition, then attach the defence logic that should run for matching combat data.
 *
 * @property listeners The backing listener collection this filter is registered into.
 * @property filter The condition that decides whether this filter should handle a defence combat event.
 * @author lare96
 */
class PlayerDefenceCombatFilter(
    val listeners: ArrayList<PlayerDefenceCombatFilter>,
    val filter: PlayerDefenceCombatDataReceiver.() -> Boolean) {

    /**
     * The defence action executed when [filter] returns `true`.
     *
     * This defaults to an empty action until [then] is called.
     */
    var defence: PlayerDefenceCombatDataReceiver.() -> Unit = { }

    /**
     * Assigns the defence action for this filter and registers it with the listener collection.
     *
     * @param defenceFunction The defence logic to execute when this filter matches.
     */
    fun then(defenceFunction: PlayerDefenceCombatDataReceiver.() -> Unit) {
        defence = defenceFunction
        listeners += this
    }
}