package io.luna.math;


import io.luna.game.model.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vector2Test {

    @Test
    void testConstruction() {
        // Test basic construction
        Vector2 vec = new Vector2(3, 4);
        assertEquals(3, vec.getX());
        assertEquals(4, vec.getY());

        // Test construction from Position
        Position pos = new Position(5, 6);
        Vector2 fromPos = new Vector2(pos);
        assertEquals(5, fromPos.getX());
        assertEquals(6, fromPos.getY());
    }

    @Test
    void testAdd() {
        Vector2 vec1 = new Vector2(2, 3);
        Vector2 vec2 = new Vector2(4, 5);
        Vector2 result = vec1.add(vec2);

        assertEquals(6, result.getX()); // 2 + 4
        assertEquals(8, result.getY()); // 3 + 5
    }

    @Test
    void testSubtract() {
        Vector2 vec1 = new Vector2(10, 8);
        Vector2 vec2 = new Vector2(3, 5);
        Vector2 result = vec1.subtract(vec2);

        assertEquals(7, result.getX()); // 10 - 3
        assertEquals(3, result.getY()); // 8 - 5
    }


    @Test
    void testMax() {
        Vector2 vec1 = new Vector2(2, 5);
        Vector2 vec2 = new Vector2(4, 3);
        Vector2 result = vec2.max(vec1);

        assertEquals(4, result.getX()); // 4 > 2
        assertEquals(5, result.getY()); // 5 > 3
    }

    @Test
    void testMin() {
        Vector2 vec1 = new Vector2(8, 5);
        Vector2 vec2 = new Vector2(3, 7);
        Vector2 result = vec1.min(vec2);

        assertEquals(3, result.getX()); // 3 < 8
        assertEquals(5, result.getY()); // 5 < 7
    }

    @Test
    void testNormalize() {
        // Test with non-zero vector
        Vector2 vec = new Vector2(5, 12);
        Vector2 normalized = vec.normalize();

        // 5-12-13 triangle, so normalized should be (5/13, 12/13)
        assertEquals(1, normalized.getX()); // 5/13 = 0.38, ceiling to 1
        assertEquals(1, normalized.getY()); // 12/13 = 0.92, ceiling to 1
        // The normalized vector is (1,1) which is facing north-east ;)

        // Test with zero vector
        Vector2 zero = new Vector2(0, 0);
        Vector2 zeroNormalized = zero.normalize();
        assertEquals(0, zeroNormalized.getX());
        assertEquals(0, zeroNormalized.getY());
    }

    @Test
    void testDistanceTo() {
        Vector2 vec1 = new Vector2(1, 2);
        Vector2 vec2 = new Vector2(4, 6);
        Vector2 distance = vec1.distanceTo(vec2);

        assertEquals(3, distance.getX()); // 4 - 1
        assertEquals(4, distance.getY()); // 6 - 2
    }

    @Test
    void testEqualsAndHashCode() {
        Vector2 vec1 = new Vector2(1, 2);
        Vector2 vec2 = new Vector2(1, 2);
        Vector2 vec3 = new Vector2(3, 4);

        // Test equality
        assertEquals(vec1, vec2);
        assertNotEquals(vec1, vec3);

        // Test hash code consistency
        assertEquals(vec1.hashCode(), vec2.hashCode());
        assertNotEquals(vec1.hashCode(), vec3.hashCode());
    }

    @Test
    void testToString() {
        Vector2 vec = new Vector2(5, 10);
        String str = vec.toString();

        assertTrue(str.contains("x=5"));
        assertTrue(str.contains("y=10"));
    }
}

