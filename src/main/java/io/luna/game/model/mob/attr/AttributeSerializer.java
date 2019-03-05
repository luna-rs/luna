package io.luna.game.model.mob.attr;

import com.google.gson.JsonElement;
import kotlin.reflect.KClass;

/**
 * @author lare96 <http://github.com/lare96>
 */
public interface AttributeSerializer<T> {
    T read(JsonElement data);

    JsonElement write(T data);

    KClass<T> valueType();
}