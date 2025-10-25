package api.attr.json

import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.attr.AttributeMap

/**
 * A type adapter that will enable [AttributeMap]s to be persisted as attributes.
 *
 * @author lare96
 */
object AttributeMapTypeAdapter : TypeAdapter<AttributeMap>() {

    override fun write(writer: JsonWriter, value: AttributeMap?) {
        val attributeList = if (value != null) value.save() else emptyMap()
        writer.jsonValue(Attribute.getGsonInstance().toJson(attributeList))
    }

    override fun read(reader: JsonReader?): AttributeMap {
        val map = AttributeMap()
        val readData = Attribute.getGsonInstance().fromJson<Map<String, Any>>(reader, object :
            TypeToken<Map<String, Any>>() {}.type)
        map.load(readData)
        return map
    }
}