package io.luna.game.model.mob.attr;

import com.google.gson.JsonElement;

/**
 * A model providing implementations a means of directing how data is saved for complex objects.
 *
 * @author lare96 <http://github.com/lare96>
 */
public interface AttributeSerializer<T> {

    /**
     * Read (deserialize) the attribute.
     *
     * @param data The data to read.
     * @return The deserialized data.
     */
    T read(JsonElement data);

    /**
     * Write (serialize) the attribute.
     *
     * @param data The data to write.
     * @return The serialized data.
     */
    JsonElement write(T data);
}