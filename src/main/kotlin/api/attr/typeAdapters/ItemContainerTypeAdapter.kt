package api.attr.typeAdapters

import api.attr.Attr.readJsonMember
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.ItemContainer.StackPolicy
import io.luna.game.model.mob.attr.Attribute

/**
 * A type adapter that will enable [ItemContainer]s to be persisted as attributes.
 *
 * @author lare96
 */
object ItemContainerTypeAdapter : TypeAdapter<ItemContainer>() {

    override fun write(writer: JsonWriter, value: ItemContainer) {
        writer.beginObject().name("capacity").value(value.capacity())
        writer.name("stack_policy").value(value.policy.name)
        writer.name("widget_id").value(value.primaryRefresh)
        writer.name("items").beginArray()
        for (item in value.toList()) {
            writer.jsonValue(Attribute.getGsonInstance().toJson(item))
        }
        writer.endArray().endObject()
    }

    override fun read(reader: JsonReader): ItemContainer {
        reader.beginObject()
        val capacity = readJsonMember(reader, "capacity") { it.nextInt() }
        val stackPolicy = readJsonMember(reader, "stack_policy") { StackPolicy.valueOf(reader.nextString()) }
        val widgetId = readJsonMember(reader, "widget_id") { it.nextInt() }
        val items = ItemContainer(capacity, stackPolicy, widgetId)
        reader.nextName()
        reader.beginArray()
        while (reader.hasNext()) {
            val item: IndexedItem = Attribute.getGsonInstance().fromJson(reader, IndexedItem::class.java)
            items.set(item.index, item.toItem())
        }
        reader.endArray()
        reader.endObject()
        return items
    }
}