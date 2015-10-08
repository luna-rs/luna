package io.luna.util.yaml;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.yaml.snakeyaml.Yaml;

/**
 * Represents a single document (in other words, a {@link LinkedHashMap}) within
 * a {@link YamlFile}.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class YamlDocument implements Iterable<Entry<String, YamlObject>> {

    /**
     * The {@link Map} of values in this document.
     */
    private final Map<String, YamlObject> values = new LinkedHashMap<>();

    /**
     * Creates a new {@link YamlDocument} initialized with {@code values}.
     *
     * @param values The document to initialize this {@code YamlDocument} with.
     */
    public YamlDocument(Map<String, Object> values) {
        for (Entry<String, Object> it : values.entrySet()) {
            add(it.getKey(), it.getValue());
        }
    }

    /**
     * Creates a new {@link YamlDocument}.
     */
    public YamlDocument() {

    }

    @Override
    public Iterator<Entry<String, YamlObject>> iterator() {
        return values.entrySet().iterator();
    }

    /**
     * Adds a new key-value pair into this document, if mutable.
     * 
     * @param key The key.
     * @param value The value.
     */
    public void add(String key, Object value) {
        values.put(key, new YamlObject(value));
    }

    /**
     * Determines if this document has a value for the specified key.
     * 
     * @param key The key to validate.
     * @return {@code true} if this document has a value, {@code false}
     *         otherwise.
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /**
     * Retrieves the value for {@code key}.
     * 
     * @param key The key to retrieve the value of.
     * @return The retrieved value, never {@code null}.
     */
    public YamlObject get(String key) {
        YamlObject obj = values.get(key);
        if (obj == null) {
            throw new NoSuchElementException("no YamlObject for key: " + key);
        }
        return obj;
    }

    /**
     * @return A shallow, mutable, copy of the values in this
     *         {@code YamlDocument} that can be serialized with a {@link Yaml}
     *         instance.
     */
    public LinkedHashMap<String, Object> toSerializableMap() {
        LinkedHashMap<String, Object> serializable = new LinkedHashMap<>();
        values.entrySet().forEach(it -> serializable.put(it.getKey(), it.getValue().asObject()));
        return serializable;
    }
}
