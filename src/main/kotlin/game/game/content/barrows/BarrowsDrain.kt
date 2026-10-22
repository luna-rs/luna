package game.content.barrows

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import kotlin.random.Random

/**
 * The 30-tick crypt/tunnel prayer drain and defeated-brother head effect from barrows.rs2.
 * Widget order is Ahrim through Verac (4537..4542); item heads are paired normal/green (4761..4772).
 * Low-memory clients still lose prayer but do not receive the animated heads.
 *
 * @author lare96
 */
object BarrowsDrain {
    const val INTERVAL = 30

    fun drain(player: Player, random: Random = Random.Default) {
        if (!player.isAlive || !BarrowsAreaListener.inside(player.position)) return
        val defeated = BarrowsProgress.killedBrothers(player)
        val prayer = player.skill(Skill.PRAYER)
        prayer.level = (prayer.level - 8 - defeated.size).coerceAtLeast(0)
        if (prayer.level == 0) player.combat.prayers.deactivateAll()
        if (player.isLowMemory || defeated.isEmpty()) return
        val brother = defeated.random(random)
        val widget = 4537 + brother.ordinal
        val head = 4761 + brother.ordinal * 2 + if (player.position.z == 0) 1 else 0
        player.queue(WidgetAnimationMessageWriter(widget, -1))
        player.queue(WidgetItemModelMessageWriter(widget, 100, head))
        player.queue(WidgetAnimationMessageWriter(widget, 2085))
    }
}
