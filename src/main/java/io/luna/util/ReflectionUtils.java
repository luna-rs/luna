package io.luna.util;

import java.lang.reflect.Field;

/**
 * A static-utility class that contains functions for manipulating the reflection API.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class ReflectionUtils {

    /**
     * Reflectively retrieves a static field from {@code fromClass}.
     *
     * @param fromClass The Class to retrieve the field of.
     * @param name The name of the field to retrieve.
     * @param fieldType The type of the field to retrieve.
     */
    public static <T> T getStaticField(Class<?> fromClass, String name, Class<T> fieldType) {
        try {
            Field field = fromClass.getDeclaredField(name);
            field.setAccessible(true);
            return fieldType.cast(field.get(null));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchFieldException e) {
            throw new TypeNotPresentException(name, e);
        }
    }

    /**
     * Reflectively retrieves an instanced field within {@code instance}.
     *
     * @param instance The Object instance to retrieve the field of.
     * @param name The name of the field to retrieve.
     * @param fieldType The type of the field to retrieve.
     */
    public static <T> T getField(Object instance, String name, Class<T> fieldType) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return fieldType.cast(field.get(instance));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchFieldException e) {
            throw new TypeNotPresentException(name, e);
        }
    }

    /**
     * Reflectively sets an instanced field within {@code instance}.
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