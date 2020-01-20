package io.luna.game.model.def;

/**
 * A mock definition for testing purposes.
 */
class MockDefinition implements Definition {

    private int id;

    MockDefinition(int id) {
        this.id = id;
    }

    /**
     * Returns the identifier of this definition.
     *
     * @return The identifier.
     */
    @Override
    public int getId() {
        return id;
    }
}
