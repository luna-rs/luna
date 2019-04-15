package api.attr

import api.predef.*
import com.google.gson.JsonElement
import io.luna.game.model.mob.attr.AttributeSerializer
import io.luna.util.GsonUtils
import io.luna.util.TickTimer

/**
 * A simple [AttributeSerializer] implementation for [TickTimer].
 */
object TickTimerSerializer : AttributeSerializer<TickTimer> {

    override fun read(inData: JsonElement) = TickTimer(world, inData.asLong)

    override fun write(outData: TickTimer): JsonElement =
        GsonUtils.toJsonTree(outData.durationTicks)
}