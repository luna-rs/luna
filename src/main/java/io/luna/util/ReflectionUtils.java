package io.luna.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

/**
 * A static-utility class that contains functions for manipulating the reflection API.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class ReflectionUtils {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Reflectively sets a field within {@code instance}.
     *
     * @param instance The Object instance to set the field of.
     * @param name The name of the field to set.
     * @param newValue The new value to set to the field.
     */
    public static void setField(Object instance, String name, Object newValue) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(instance, newValue);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchFieldException e) {
            throw new TypeNotPresentException(name, e);
        }
    }
}