package io.luna.game.model.area;

import io.luna.game.model.Position;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PolygonAreaTest {

    //basic contains
    @Test
    public void testPointInsideSimplePolygon() {
        List<Point> vertices = Arrays.asList(
                new Point(0, 0),
                new Point(10, 0),
                new Point(10, 10),
                new Point(0, 10)
        );
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        assertTrue(area.contains(new Position(5, 5)));
        assertTrue(oldArea.contains(new Position(5, 5)));
    }

    @Test
    public void testPointOutsideSimplePolygon() {
        List<Point> vertices = Arrays.asList(
                new Point(0, 0),
                new Point(10, 0),
                new Point(10, 10),
                new Point(0, 10)
        );
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        assertFalse(area.contains(new Position(11, 5)));
        assertFalse(oldArea.contains(new Position(11, 5)));
    }

    //edge/vertex cases
    @Test
    public void testPointOnEdge() {
        List<Point> vertices = Arrays.asList(
                new Point(0, 0),
                new Point(10, 0),
                new Point(10, 10),
                new Point(0, 10)
        );
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        //on the bottom edge
        assertTrue(area.contains(new Position(5, 0)));
        assertTrue(oldArea.contains(new Position(5, 0)));
    }

    @Test
    public void testPointOnVertex() {
        List<Point> vertices = Arrays.asList(
                new Point(0, 0),
                new Point(10, 0),
                new Point(10, 10),
                new Point(0, 10)
        );
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        //exactly on a corner
        assertTrue(area.contains(new Position(0, 0)));
        assertTrue(oldArea.contains(new Position(0, 0)));
    }

    //invalid polygons
    @Test
    public void testDegeneratePolygonLine() {
        List<Point> vertices = Arrays.asList(
                new Point(0, 0),
                new Point(10, 0)
        );
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        assertFalse(area.contains(new Position(5, 0)));
        assertFalse(oldArea.contains(new Position(5, 0)));
    }

    @Test
    public void testDegeneratePolygonPoint() {
        List<Point> vertices = Collections.singletonList(new Point(0, 0));
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        assertFalse(area.contains(new Position(0, 0)));
        assertFalse(oldArea.contains(new Position(0, 0)));
    }

    //nonconvex
    @Test
    public void testNonConvexPolygon() {
        List<Point> vertices = Arrays.asList(
                new Point(0, 0),
                new Point(5, 5),
                new Point(10, 0),
                new Point(10, 10),
                new Point(0, 10)
        );
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        //inside the "dent"
        assertFalse(area.contains(new Position(5, 2)));
        assertFalse(oldArea.contains(new Position(5, 2)));

        //inside the main area
        assertTrue(area.contains(new Position(5, 8)));
        assertTrue(oldArea.contains(new Position(5, 8)));
    }

    //bb
    @Test
    public void testBoundingBoxExclusion() {
        List<Point> vertices = Arrays.asList(
                new Point(0, 0),
                new Point(4, 0),
                new Point(4, 3),
                new Point(0, 3)
        );
        PolygonArea area = new PolygonArea(vertices);
        OldPolygonArea oldArea = new OldPolygonArea(vertices);

        //outside bounding box
        assertFalse(area.contains(new Position(5, 5)));
        assertFalse(oldArea.contains(new Position(5, 5)));
    }
}
