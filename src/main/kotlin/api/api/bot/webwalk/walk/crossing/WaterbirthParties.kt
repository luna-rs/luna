package api.bot.webwalk.walk.crossing

import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import java.util.IdentityHashMap

/**
 * Joins existing adventurers for one cooperative passage. It never creates or controls another bot.
 * Runtime membership is discarded on cancellation, completion or a bounded expiry; player supplies remain native.
 *
 * @author lare96
 */
internal object WaterbirthParties {
    /**
     * Each participant drives its own movement; both roles finish in the same central room.
     *
     * @author lare96
     */
    enum class Phase { NORTH_PADS, MIDDLE_PADS, CORRIDORS, SUPPORTS, ARRIVAL }

    /**
     * The two participants and their observed native passage checkpoints.
     *
     * @author lare96
     */
    class Party(val north: Bot, val deadline: Long) {
        var middle: Bot? = null
        var phase = Phase.NORTH_PADS
        var failed = false
        val atProps = HashSet<Bot>()
        val arrived = HashSet<Bot>()
        fun role(bot: Bot): Int = if (bot === north) 0 else { require(bot === middle); 1 }
        fun advance(expected: Phase, next: Phase) { if (phase == expected) phase = next }
    }

    private val worlds = IdentityHashMap<World, MutableList<Party>>()

    fun join(world: World, bot: Bot, now: Long): Party {
        val parties = worlds.getOrPut(world) { ArrayList() }
        parties.removeIf { it.failed || it.deadline <= now || it.arrived.size == 2 }
        parties.firstOrNull { it.north === bot || it.middle === bot }?.let { return it }
        val waiting = parties.firstOrNull { it.middle == null && it.north !== bot }
        if (waiting != null) { waiting.middle = bot; return waiting }
        return Party(bot, now + 180_000).also { parties += it }
    }

    fun leave(world: World, party: Party, bot: Bot, success: Boolean) {
        if (success) party.arrived += bot else party.failed = true
        if (party.failed || party.arrived.size == 2) {
            worlds[world]?.remove(party)
            if (worlds[world].isNullOrEmpty()) worlds.remove(world)
        }
    }
}
