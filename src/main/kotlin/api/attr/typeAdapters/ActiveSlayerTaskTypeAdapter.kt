package api.attr.typeAdapters

import api.attr.Attr.readJsonMember
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import world.player.skill.slayer.ActiveSlayerTask
import world.player.skill.slayer.SlayerMaster
import world.player.skill.slayer.SlayerTaskType

/**
 * A type adapter that will enable [ActiveSlayerTask]s to be persisted as attributes.
 *
 * @author lare96
 */
object ActiveSlayerTaskTypeAdapter : TypeAdapter<ActiveSlayerTask?>() {

    override fun write(writer: JsonWriter, value: ActiveSlayerTask?) {
        if (value != null) {
            writer.beginObject()
            writer.name("task").value(value.task.name)
            writer.name("assignee").value(value.assignee.name)
            writer.name("remaining").value(value.remaining)
            writer.endObject()
        } else {
           writer.nullValue()
        }
    }

    override fun read(reader: JsonReader): ActiveSlayerTask? {
        if (reader.peek() == JsonToken.NULL) {
            return null
        }
        reader.beginObject()
        val task = readJsonMember(reader, "task") { SlayerTaskType.valueOf(it.nextString()) }
        val assignee = readJsonMember(reader, "assignee") { SlayerMaster.valueOf(reader.nextString()) }
        val remaining = readJsonMember(reader, "remaining") { it.nextInt() }
        reader.endObject()
        return ActiveSlayerTask(task, assignee, remaining)
    }
}