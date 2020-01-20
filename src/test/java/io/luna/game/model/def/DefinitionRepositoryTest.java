package io.luna.game.model.def;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefinitionRepository}.
 *
 * @author lare96 <http://github.org/lare96>
 */
final class DefinitionRepositoryTest {

    @Test
    void testLock() {
        var repository = mock(DefinitionRepository.class, Mockito.CALLS_REAL_METHODS);
        repository.lock();

        var definition = new MockDefinition(1);
        assertThrows(IllegalStateException.class, () -> repository.storeDefinition(definition));
    }
}