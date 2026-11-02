package engine.item.developerItem

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.ValueType
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.SkillUpdateMessageWriter
import io.netty.buffer.ByteBuf

/**
 * Displays a target's skill snapshot on the native skill interface and restores the viewer on close.
 * No server-side skill values are copied onto the viewer.
 *
 * @author lare96
 */
internal class DeveloperSkillsInterface(private val target: Player) : StandardInterface(3917) {
    override fun onOpen(player: Player) {
        for (skill in target.skills) player.queue(Snapshot(skill.id, skill.level, skill.experience.toInt()))
        player.sendMessage("${target.username}: total level ${target.skills.sumOf { it.staticLevel }}.")
    }

    override fun onClose(player: Player) {
        for (skill in player.skills) player.queue(SkillUpdateMessageWriter(skill.id))
    }

    /**
     * Uses the ordinary skill-update packet with immutable inspected values.
     *
     * @author lare96
     */
    private class Snapshot(val id: Int, val level: Int, val experience: Int) : GameMessageWriter() {
        override fun write(player: Player, buffer: ByteBuf): ByteMessage {
            val message = ByteMessage.message(49, buffer)
            message.put(id, ValueType.NEGATE)
            message.put(level)
            message.putInt(experience)
            return message
        }
    }
}
