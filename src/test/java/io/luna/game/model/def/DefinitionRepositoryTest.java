package io.luna.game.model.def;

import io.luna.game.model.def.DefinitionRepository.ArrayDefinitionRepository;
import io.luna.game.model.def.DefinitionRepository.MapDefinitionRepository;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A test that ensures that functions within {@link DefinitionRepository} are working correctly.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class DefinitionRepositoryTest {

    /**
     * Ensure that definitions cannot be added to locked array repositories.
     */
    @Test(expected = IllegalStateException.class)
    public void testArrayRepository() {
        var array = new ArrayDefinitionRepository<>(5);
        for (int index = 0; index < 5; index++) {
            array.storeDefinition(newDefinition(index));
        }
        array.lock();
        array.storeDefinition(newDefinition(0));
    }

    /**
     * Ensure that definitions cannot be added to locked map repositories.
     */
    @Test(expected = IllegalStateException.class)
    public void testMapRepository() {
        var map = new MapDefinitionRepository<>();
        for (int index = 0; index < 5; index++) {
            map.storeDefinition(newDefinition(index));
        }
        map.lock();
        map.storeDefinition(newDefinition(0));
    }

    /**
     * Instantiates a new mock definition.
     */
    private Definition newDefinition(int id) {
        var def = mock(Definition.class);
        when(def.getId()).thenReturn(id);
        return def;
    }
}