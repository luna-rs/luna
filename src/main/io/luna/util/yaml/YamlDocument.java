package io.luna.util.yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.common.collect.ImmutableMap;

public final class YamlDocument {

    private final Map<String, YamlObject> values = new LinkedHashMap<>();

    public YamlDocument(Map<String, Object> values) {
        values.forEach((k, v) -> values.put(k, new YamlObject(v)));
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public YamlObject get(String key) {
        YamlObject obj = values.get(key);
        if (obj == null) {
            throw new NoSuchElementException(key + " -> no YamlObject");
        }
        return obj;
    }

    public ImmutableMap<String, YamlObject> toImmutableMap() {
        return ImmutableMap.copyOf(values);
    }
}
