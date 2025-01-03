package io.luna.game.model.map;

import com.google.common.base.Objects;
import io.luna.game.model.Location;
import io.luna.game.model.Position;
import io.luna.game.model.Region;

public final class DynamicMapSpace implements Location {
    // TODO documentation

    private final Region main;
    private final Region padding;

    public DynamicMapSpace(Region main) {
        this.main = main;
        padding = new Region(main.getX() + 1, main.getY());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicMapSpace)) return false;
        DynamicMapSpace space = (DynamicMapSpace) o;
        return Objects.equal(main, space.main) && Objects.equal(padding, space.padding);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(main, padding);
    }

    @Override
    public boolean contains(Position position) {
        return main.contains(position) || padding.contains(position);
    }

    public boolean isVisibleTo(DynamicMapSpace other) {
        return main.isWithinDistance(other.main, 1) ||
                main.isWithinDistance(other.padding, 1) ||
                padding.isWithinDistance(other.main, 1) ||
                padding.isWithinDistance(other.padding, 1);
    }

    public Region getMain() {
        return main;
    }

    public Region getPadding() {
        return padding;
    }

}
