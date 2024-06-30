package io.luna.game.model.mob.controller;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A model representing a key to generate and access {@link PlayerController} instances.
 *
 * @param <T> The {@link PlayerController} this key is for.
 * @author lare96
 */
public final class ControllerKey<T extends PlayerController> {

    /**
     * A set of all occupied key names.
     */
    private static final Set<String> usedNames = Sets.newConcurrentHashSet();

    /**
     * A list of all {@link PlayerLocationController} based keys.
     */
    private static final List<ControllerKey<? extends PlayerLocationController>> locationKeys = new ArrayList<>();

    /**
     * @return An unmodifiable list all {@link PlayerLocationController} based keys.
     */
    public static List<ControllerKey<? extends PlayerLocationController>> getLocationKeys() {
        return Collections.unmodifiableList(locationKeys);
    }

    /**
     * Creates a new {@link ControllerKey} for type {@link T}.
     *
     * @param controllerType The type of controller to create a key for.
     * @param controllerSupplier Generate a new controller.
     * @param <T> The type of controller to create a key for.
     * @return The new key.
     */
    public static <T extends PlayerController> ControllerKey<T> of(Class<T> controllerType, Supplier<T> controllerSupplier) {
        String name = controllerType.getName();
        if (usedNames.add(name)) {
            ControllerKey<T> newKey = new ControllerKey<>(name);
            T newController = controllerSupplier.get();
            newKey.controller = newController;
            if (newController instanceof PlayerLocationController) {
                locationKeys.add((ControllerKey<? extends PlayerLocationController>) newKey);
            }
            return newKey;
        }
        throw new IllegalArgumentException("Only one ControllerKey per Controller allowed.");
    }

    /**
     * The name of this key.
     */
    private final String name;

    /**
     * The controller instance attached to this key.
     */
    private T controller;

    /**
     * Creates a new {@link ControllerKey}.
     *
     * @param name The name of this key.
     */
    private ControllerKey(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ControllerKey<?> that = (ControllerKey<?>) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * @return The name of this key.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The controller instance attached to this key.
     */
    public T getController() {
        return controller;
    }
}
