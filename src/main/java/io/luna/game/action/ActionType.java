package io.luna.game.action;

import io.luna.game.model.mob.overlay.StandardInterface;

/**
 * An enum representing the different action types that can be associated with an {@link Action}. These types determine
 * how an action is processed.
 *
 * @author lare96
 * @see <a href="https://osrs-docs.com/docs/mechanics/queues/">Queues (osrs-docs)</a>
 */
public enum ActionType {

    /**
     * Removed from the queue if there are any strong scripts in the queue prior to the queue being processed; Can be
     * interrupted with {@link ActionQueue#interruptWeak()}.
     */
    WEAK,

    /**
     * Removes all weak scripts from the queue prior to being processed. Closes modal interface prior to executing.
     */
    STRONG,

    /**
     * Skipped in the execution block if the player has a {@link StandardInterface} open at the time.
     */
    NORMAL,

    /**
     * Always runs until {@link ActionQueue#interruptAll()} is called or the implementation decides to stop.
     */
    SOFT,
}
