package io.luna.util;

/**
 * A static-utility class that contains functions for classes.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ClassUtils {

    /**
     * Ensures that {@code clazz} will be loaded by its {@link ClassLoader}.
     *
     * @param clazz The class to load.
     */
    public static void loadClass(Class<?> clazz) {
        try {
            Class.forName(clazz.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
