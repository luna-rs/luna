package world.player.item.banking.regularBank

import api.predef.*

/**
 * Holds utility functions related to banking.
 */
object Banking {

    /**
     * The mutable set of banking objects.
     */
    private val loadObjects = mutableSetOf<Int>()

    /**
     * The immutable set of banking objects.
     */
    val bankingObjects: Set<Int> = loadObjects

    /**
     * Loads all banking objects based on definitions from the cache.
     */
    internal fun loadBankingObjects() {
        for (gameObj in world.objects) {
            val name = gameObj.definition.name
            val actions = gameObj.definition.actions
            if ((name.equals("Bank booth") || name.equals("Bank chest")) &&
                (actions.contains("Open") || actions.contains("Use"))) {
                loadObjects += gameObj.id
            }
        }
    }
}