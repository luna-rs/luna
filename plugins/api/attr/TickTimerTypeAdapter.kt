package api.attr

import api.predef.*
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.luna.util.TickTimer

/**
 * A simple [TypeAdapter] implementation for [TickTimer].
 *
 * @author lare96
 */
object TickTimerTypeAdapter : TypeAdapter<TickTimer>() {

    override fun write(writer: JsonWriter?, data: TickTimer?) {
        writer?.name("snapshot")?.value(data?.durationTicks)
    }

    override fun read(reader: JsonReader?): TickTimer =
        TickTimer(world, reader?.nextLong()!!)
}