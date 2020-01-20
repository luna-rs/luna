package io.luna.game.model.def;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefinitionRepository}.
 *
 * @author lare96 <http://github.org/lare96>
 */
final class DefinitionRepositoryTest {

    Definition def;

    @BeforeEach
    void initDefinition() {
        def = mock(Definition.class);
        when(def.getId()).thenReturn(1);
    }

    @Test
    void lockedArrayRepository() {
        var repository = new ArrayDefinitionRepository<>(0);
        repository.lock();
        assertThrows(IllegalStateException.class, () -> repository.storeDefinition(def));
    }

    @Test
    void lockedMapRepository() {
        var repository = new MapDefinitionRepository<>();
        repository.lock();
        assertThrows(IllegalStateException.class, () -> repository.storeDefinition(def));
    }
}