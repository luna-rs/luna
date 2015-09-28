package io.luna.util.yaml;

import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import com.google.common.collect.ImmutableMap;

public final class YamlDocument implements Iterable<Entry<String, YamlObject>> {

    private final boolean mutable;
    private final Map<String, YamlObject> values = new LinkedHashMap<>();

    public static YamlDocument mutable() {
        return new YamlDocument(true);
    }

    public static YamlDocument immutable(Map<String, Object> values) {
        YamlDocument yml = new YamlDocument(false);
        values.forEach((k, v) -> yml.values.put(k, new YamlObject(v)));
        return yml;
    }

    private YamlDocument(boolean mutable) {
        this.mutable = mutable;
    }

    @Override
    public Iterator<Entry<String, YamlObject>> iterator() {
        return values.entrySet().iterator();
    }

    public void add(String key, Object value) {
        checkState(mutable, "YamlDocument is immutable");
        values.put(key, new YamlObject(value));
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public YamlObject get(String key) {
        YamlObject obj = values.get(key);
        if (obj == null) {
            throw new NoSuchElementException("no YamlObject for key: " + key);
        }
        return obj;
    }

    public ImmutableMap<String, YamlObject> toImmutableMap() {
        return ImmutableMap.copyOf(values);
    }

}
