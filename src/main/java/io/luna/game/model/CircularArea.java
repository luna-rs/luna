package io.luna.game.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

import java.util.concurrent.ThreadLocalRandom;

public class CircularArea extends Area {
    private final int centerX;
    private final int centerY;
    private final int radius;

    public CircularArea(int centerX, int centerY, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;

        if (radius < 1) {
            throw new IllegalArgumentException("Circular radius cannot be less than 1");
        }

        this.radius = radius;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(centerX, centerY, radius);
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

        return Objects.equal(centerX, ca.centerX) &&
                Objects.equal(centerY, ca.centerY) &&
                Objects.equal(radius, ca.radius);
    }

    @Override
    public boolean contains(Position position) {
        final Position pos = new Position(centerX, centerY);
        return pos.isWithinDistance(position, radius);
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
        for (int x = centerX - radius; x < centerX + radius; x++) {
            for (int y = centerY - radius; y < centerY + radius; y++) {
                int dx = x - centerX;
                int dy = y - centerY;
                if (Math.pow(dx, 2) * Math.pow(dy, 2) <= radiusSquared) {
                    set.add(new Position(x, y));
                }
            }
        }
        return set.build();
    }
}