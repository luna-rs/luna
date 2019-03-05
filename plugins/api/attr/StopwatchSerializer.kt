package api.attr

import com.google.gson.JsonElement
import io.luna.game.model.mob.attr.AttributeSerializer
import io.luna.util.GsonUtils
import java.util.concurrent.TimeUnit

/**
 * A simple {@link Attribute} implementation for {@link Stopwatch} instances.
 */
class StopwatchSerializer(initialDuration: Long) : AttributeSerializer<Stopwatch> {

    override fun read(inData: JsonElement) = Stopwatch(inData.asLong)

    override fun write(outData: Stopwatch): JsonElement =
        GsonUtils.toJsonTree(outData.getDuration(TimeUnit.NANOSECONDS))

    override fun valueType() = Stopwatch::class
}