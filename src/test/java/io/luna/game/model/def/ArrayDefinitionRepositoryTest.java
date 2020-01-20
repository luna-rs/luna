package io.luna.game.model.def;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArrayDefinitionRepositoryTest {

    private ArrayDefinitionRepository<MockDefinition> repository;

    @Test
    void testGetByIndex() {
        repository = new ArrayDefinitionRepository<>(1);
        repository.put(0, new MockDefinition(1));

        assertTrue(repository.get(0).isPresent()); // get def by index
    }

    @Test
    void testLookupById() {
        repository = new ArrayDefinitionRepository<>(1);
        repository.put(0, new MockDefinition(1));

        assertTrue(repository.lookup(def -> def.getId() == 1).isPresent()); // get def by id
    }

    @Test
    void testPut_withDuplicateValue() {
        repository = new ArrayDefinitionRepository<>(2);

        var mock = new MockDefinition(1);
        repository.put(0, mock); // insert into index 0
        repository.put(1, mock); // insert into index 1

        var first = repository.get(0);
        var second = repository.get(1);

        assertEquals(first, second);
    }
}