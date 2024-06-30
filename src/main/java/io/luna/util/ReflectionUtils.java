package io.luna.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * A static-utility class that contains functions for manipulating the reflection API.
 *
 * @author lare96 
 */
public final class ReflectionUtils {

    /**
     * A {@link RuntimeException} implementation that acts as a non-checked version of
     * {@link ReflectiveOperationException}.
     */
    public static class ReflectionException extends RuntimeException {
        public ReflectionException(ReflectiveOperationException e) {
            super(e);
        }
    }

    /**
     * Reflectively retrieves a static field from {@code fromClass}.
     *
     * @param fromClass The Class to retrieve the field of.
     * @param name The name of the field to retrieve.
     * @param fieldType The type of the field to retrieve.
     */
    public static <T> T getStaticField(Class<?> fromClass, String name, Class<T> fieldType) throws ReflectionException {
        try {
            Field field = fromClass.getDeclaredField(name);
            field.setAccessible(true);
            return fieldType.cast(field.get(null));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ReflectionException(e);
        }
    }

    /**
     * Reflectively retrieves an instanced field within {@code instance}.
     *
     * @param instance The Object instance to retrieve the field of.
     * @param name The name of the field to retrieve.
     * @param fieldType The type of the field to retrieve.
     */
    public static <T> T getField(Object instance, String name, Class<T> fieldType) throws ReflectionException {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return fieldType.cast(field.get(instance));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ReflectionException(e);
        }
    }

    /**
     * Reflectively sets an instanced field within {@code instance}.
     *
     * @param instance The Object instance to set the field of.
     * @param name The name of the field to set.
     * @param newValue The new value to set to the field.
     */
    public static void setField(Object instance, String name, Object newValue) throws ReflectionException {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(instance, newValue);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new ReflectionException(e);
        }
    }

    /**
     * Reflectively creates a new instance of {@code className} using {@code parameters}.
     *
     * @param className The fully qualified class name.
     * @param parameters The parameters, if any.
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstanceOf(String className, Function<Class<T>, Constructor<T>> constructorFunction,
                                      Object... parameters) throws ClassCastException, ReflectionException {
        try {
            Class<?> forClass = Class.forName(className);
            Constructor<T> constructor = constructorFunction.apply((Class<T>) forClass);
            constructor.setAccessible(true);
            return constructor.newInstance(parameters);
        } catch (ReflectiveOperationException e) {
            throw new ReflectionException(e);
        }
    }
}