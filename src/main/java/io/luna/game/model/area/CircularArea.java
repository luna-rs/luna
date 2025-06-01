package io.luna.game.model.area;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.Position;

import java.util.concurrent.ThreadLocalRandom;

public class CircularArea extends Area {
    
    private final Position center;
    private final int radius;

    public CircularArea(int centerX, int centerY, int radius) {
        this.center = new Position(centerX, centerY);

        if (radius < 1) {
            throw new IllegalArgumentException("Circular radius cannot be less than 1");
        }

        this.radius = radius;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(center.getX(), center.getY(), radius);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (this == o) { // shares same address in memory
            return true;
        }

        if (!(o instanceof CircularArea)) {
            return false;
        }
        CircularArea ca = (CircularArea) o;

        return Objects.equal(center.getX(), ca.center.getX()) &&
                Objects.equal(center.getY(), ca.center.getY()) &&
                Objects.equal(radius, ca.radius);
    }

    @Override
    public boolean contains(Position position) {
        return center.isWithinDistance(position, radius);
    }

    @Override
    public int size() {
        return (int) (Math.PI * radius * radius);
    }

    @Override
    public Position randomPosition() {
        ImmutableSet<Position> positions = computePositionSet();
        int random = ThreadLocalRandom.current().nextInt(positions.size());
        return positions.asList().get(random);
    }

    @Override
    ImmutableSet<Position> computePositionSet() {
        ImmutableSet.Builder<Position> set = ImmutableSet.builder();
        int radiusSquared = radius * radius;

        // Pythagorean theorem works for circles too
        for (int x = center.getX() - radius; x < center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y < center.getY() + radius; y++) {
                int dx = x - center.getX();
                int dy = y - center.getY();
                if (Math.pow(dx, 2) * Math.pow(dy, 2) <= radiusSquared) {
                    set.add(new Position(x, y));
                }
            }
        }
        return set.build();
    }
}
