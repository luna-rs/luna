package game.content.randomEvents

import api.predef.ext.scheduleOnce
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.task.Task

/**
 * Owns a bounded native random-event player suspension. Lost City's p_delay protects player input until its
 * continuation resumes; cleanup may release only the exact hard lock acquired by this sequence.
 *
 * @author lare96
 */
internal class RandomEventDelay private constructor(private val player: Player, private val lockRevision: Long) {
    private var closed = false
    private var watchdog: Task? = null

    companion object {
        /**
         * RuneScript p_delay(n) resumes after n+1 ticks, including a one-tick wait for zero.
         */
        fun projectileTicks(cycles: Int, divisor: Int): Int {
            require(cycles >= 0 && divisor > 0)
            return 1 + cycles / divisor
        }

        /**
         * Acquires one fresh hard lock without replacing an existing lock or retaining an inactive player.
         */
        fun start(player: Player, maximumTicks: Int): RandomEventDelay? {
            require(maximumTicks > 0)
            if (player.state != EntityState.ACTIVE || !player.isAlive || player.isLocked) return null
            player.lock()
            val lease = RandomEventDelay(player, player.actionLockRevision)
            val watchdog = object : Task(false, 1) {
                private var age = 0
                override fun execute() {
                    if (!lease.active() || ++age >= maximumTicks) lease.close()
                }
            }
            lease.watchdog = watchdog
            try {
                player.world.schedule(watchdog)
            } catch (failure: Throwable) {
                lease.close()
                throw failure
            }
            return lease
        }
    }

    /**
     * Identity is the lease itself; the revision is only an unlock-ownership guard, never a callback epoch.
     */
    fun active(): Boolean = !closed && player.state == EntityState.ACTIVE && player.isAlive &&
        player.isActionLocked && player.actionLockRevision == lockRevision

    /**
     * Runs one source continuation only while its original player/lock still belongs to this sequence.
     */
    fun after(ticks: Int, action: () -> Unit) {
        require(ticks > 0)
        if (closed) return
        try {
            player.world.scheduleOnce(ticks) {
                if (!active()) {
                    close()
                    return@scheduleOnce
                }
                try {
                    action()
                } catch (failure: Throwable) {
                    close()
                    throw failure
                }
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    /**
     * Finishes once, retaining any later replacement/death lock and cancelling the bounded watchdog.
     */
    fun close() {
        if (closed) return
        closed = true
        watchdog?.cancel()
        if (player.isActionLocked && player.actionLockRevision == lockRevision) player.unlock()
    }
}
