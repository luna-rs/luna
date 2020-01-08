package api.area

import io.luna.game.model.mob.Player

/**
 * An [AreaReceiver] implementation for standalone areas.
 *
 * @author lare96
 */
class ListeningAreaReceiver : AreaReceiver() {

    /**
     * The enter listener.
     */
    var enter: (Player.() -> Unit)? = null

    /**
     * The exit listener.
     */
    var exit: (Player.() -> Unit)? = null

    /**
     * The move listener.
     */
    var move: (Player.() -> Unit)? = null
}