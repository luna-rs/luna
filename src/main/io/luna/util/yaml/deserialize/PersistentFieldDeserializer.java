package io.luna.util.yaml.deserialize;

import io.luna.game.model.mobile.PlayerSerializer;
import io.luna.game.model.mobile.PlayerSerializer.PersistentField;
import io.luna.util.yaml.YamlDeserializer;
import io.luna.util.yaml.YamlDocument;

import java.util.List;

/**
 * A {@link YamlDeserializer} implementation that deserializes {@code YAML}
 * files into {@link PersistentField}s.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class PersistentFieldDeserializer extends YamlDeserializer<PersistentField> {

    /**
     * Creates a new {@link PersistentFieldDeserializer}.
     */
    public PersistentFieldDeserializer() {
        super("./data/persistence.yml");
    }

    @Override
    public PersistentField deserialize(YamlDocument yml) throws Exception {
        String fieldName = yml.get("field_name").asString();
        return new PersistentField(fieldName);
    }

    @Override
    public void onComplete(List<PersistentField> list) {
        list.forEach(PlayerSerializer.PERSISTENT_FIELDS::add);
    }
}
