package io.luna.game.model.chunk;

import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A test that ensures that functions within the {@link ChunkRepository} class are working correctly.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ChunkRepositoryTest {

    /**
     * Test adding duplicate entities to a repository.
     */
    @Test(expected = IllegalStateException.class)
    public void testAdd() {
        ChunkRepository repository = new ChunkRepository();
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(EntityType.PLAYER);

        repository.add(entity);
        repository.add(entity);
    }

    /**
     * Test removing non-existent entities from a repository.
     */
    @Test(expected = IllegalStateException.class)
    public void testRemove() {
        ChunkRepository repository = new ChunkRepository();
        Entity entity = mock(Entity.class);
        when(entity.getType()).thenReturn(EntityType.PLAYER);

        repository.remove(entity);
    }

    /**
     * Test initializing map for each type.
     */
    @Test
    public void testInitialization() {
        ChunkRepository repository = new ChunkRepository();
        for (EntityType type : EntityType.ALL) {
            assertNotNull(repository.setOf(type));
        }
    }
}