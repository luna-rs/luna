package api.attr.typeAdapters

import api.attr.Attr.readJsonMember
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.attr.AttributeMap


/**
 * A type adapter that will enable [Item]s to be persisted as attributes.
 *
 * @author lare96
 */
object ItemTypeAdapter : TypeAdapter<Item>() {

    override fun write(writer: JsonWriter, value: Item) {
        writer.beginObject()
        writer.name("id").value(value.id)
        writer.name("amount").value(value.amount)
        if (value is DynamicItem) {
            val jsonString = Attribute.getGsonInstance().toJson(value.attributes())
            writer.name("attributes").jsonValue(jsonString)
        }
        writer.endObject()
    }

    override fun read(reader: JsonReader): Item {
        reader.beginObject()
        val id = readJsonMember(reader, "id") { it.nextInt() }
        val amount = readJsonMember(reader, "amount") { it.nextInt() }
        if (reader.hasNext()) {
            val attributes = readJsonMember(reader, "attributes") {
                Attribute.getGsonInstance().fromJson<AttributeMap>(it, AttributeMap::class.java)
            }
            reader.endObject()
            return DynamicItem(id, attributes)
        }
        reader.endObject()
        return Item(id, amount)
    }
}