package io.luna.util.yaml;

public abstract class YamlSerializer<T> {

    public abstract YamlDocument toYaml(T obj);
}
