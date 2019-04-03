package api.attr

import com.google.gson.JsonElement
import io.luna.game.model.mob.attr.AttributeSerializer
import io.luna.util.GsonUtils
import java.util.concurrent.TimeUnit

/**
 * A simple [AttributeSerializer] implementation for [Timer].
 */
object TimerSerializer : AttributeSerializer<Timer> {

    override fun read(inData: JsonElement) = Timer(inData.asLong)

    override fun write(outData: Timer): JsonElement =
        GsonUtils.toJsonTree(outData.getDuration(TimeUnit.NANOSECONDS))
}