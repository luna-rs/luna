package io.luna.util.yaml;

import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Represents a single document (in other words, a {@link Map}) within a
 * {@code YAML} file.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class YamlDocument implements Iterable<Entry<String, YamlObject>> {

    /**
     * Determines if this document is mutable. Mutable documents are used for
     * serialization while immutable documents are used for deserialization.
     */
    private final boolean mutable;

    /**
     * The {@link Map} of values in this document.
     */
    private final Map<String, YamlObject> values = new LinkedHashMap<>();

    /**
     * Creates a new empty mutable document, used for serialization.
     * 
     * @return The new mutable document.
     */
    public static YamlDocument mutable() {
        return new YamlDocument(true);
    }

    /**
     * Creates an immutable document with {@code values} as the contents, used
     * for deserialization.
     * 
     * @param values The values that will be put into this document.
     * @return The new immutable document.
     */
    public static YamlDocument immutable(Map<String, Object> values) {
        YamlDocument yml = new YamlDocument(false);
        values.forEach((k, v) -> yml.values.put(k, new YamlObject(v)));
        return yml;
    }

    /**
     * Creates a new {@link YamlDocument}.
     *
     * @param mutable Determines if this document is mutable.
     */
    private YamlDocument(boolean mutable) {
        this.mutable = mutable;
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
        checkState(mutable, "this YamlDocument is immutable");
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
     * @return A shallow, immutable, copy of the values in this document.
     */
    public ImmutableMap<String, YamlObject> toImmutableMap() {
        return ImmutableMap.copyOf(values);
    }

    /**
     * @return A shallow, mutable, copy of the values in this document.
     */
    public Map<String, YamlObject> toMutableMap() {
        return Maps.newLinkedHashMap(values);
    }

    /**
     * @return A shallow, mutable, copy of the values in this document that can
     *         be serialized with a {@link Yaml} instance.
     */
    public Map<String, Object> toSerializableMap() {
        Map<String, Object> serializable = new LinkedHashMap<>();
        values.entrySet().forEach(it -> serializable.put(it.getKey(), it.getValue().asObject()));
        return serializable;
    }
}
