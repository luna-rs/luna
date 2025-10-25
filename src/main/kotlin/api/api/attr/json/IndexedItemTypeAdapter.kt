package api.attr.json

import api.attr.Attr.readJsonMember
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.luna.game.model.item.DynamicIndexedItem
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.attr.AttributeMap

/**
 * A type adapter that will enable [IndexedItem]s to be persisted as attributes.
 *
 * @author lare96
 */
object IndexedItemTypeAdapter : TypeAdapter<IndexedItem>() {

    override fun write(writer: JsonWriter, value: IndexedItem) {
        writer.beginObject()
        writer.name("index").value(value.index)
        writer.name("id").value(value.id)
        writer.name("amount").value(value.amount)
        if (value is DynamicIndexedItem) {
            writer.name("attributes").jsonValue(Attribute.getGsonInstance().toJson(value.attributes))
        }
        writer.endObject()
    }

    override fun read(reader: JsonReader): IndexedItem {
        reader.beginObject()
        val index = readJsonMember(reader, "index") { it.nextInt() }
        val id = readJsonMember(reader, "id") { it.nextInt() }
        val amount = readJsonMember(reader, "amount") { it.nextInt() }
        if (reader.hasNext()) {
            val attributes = readJsonMember(reader, "attributes") {
                Attribute.getGsonInstance().fromJson<AttributeMap>(it, AttributeMap::class.java)
            }
            reader.endObject()
            return DynamicIndexedItem(index, id, attributes)
        }
        reader.endObject()
        return IndexedItem(index, id, amount)
    }
}