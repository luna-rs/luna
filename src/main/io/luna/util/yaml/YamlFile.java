package io.luna.util.yaml;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.Iterables;

/**
 * An {@link Iterable} of {@link YamlDocument}s effectively representing a
 * {@code YAML} file.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class YamlFile implements Iterable<YamlDocument> {

    /**
     * An {@link ArrayList} containing the {@link YamlDocument}s within a
     * {@code YamlFile}.
     */
    private final List<YamlDocument> documents = new ArrayList<>();

    /**
     * Creates a new {@link YamlFile}.
     *
     * @param documents The {@link YamlDocument}s contained within this
     *        {@code YamlFile}.
     */
    public YamlFile(Iterable<YamlDocument> documents) {
        Iterables.addAll(this.documents, documents);
    }

    /**
     * Creates a new {@link YamlFile}.
     */
    public YamlFile() {

    }

    /**
     * Adds {@code document} to this {@code YamlFile}.
     * 
     * @param document The {@link YamlDocument} to add.
     */
    public void add(YamlDocument document) {
        documents.add(document);
    }

    @Override
    public Iterator<YamlDocument> iterator() {
        return documents.iterator();
    }

    /**
     * @return {@code true} if the contents of this {@code YamlFile} only
     *         consist of one {@link YamlDocument}.
     */
    public boolean isSingleton() {
        return documents.size() == 1;
    }

    /**
     * @return A shallow, mutable, copy of the values in this {@code YamlFile}
     *         that can be serialized with a {@link Yaml} instance.
     */
    public ArrayList<LinkedHashMap<String, Object>> toSerializableList() {
        ArrayList<LinkedHashMap<String, Object>> rawList = new ArrayList<>();
        for(YamlDocument yml : this) {
            rawList.add(yml.toSerializableMap());
        }
        return rawList;
    }
}
