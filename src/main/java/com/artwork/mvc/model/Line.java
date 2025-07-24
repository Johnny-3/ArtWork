package com.artwork.mvc.model;

import java.awt.Color;
import java.awt.Point;

public class Line {
    private final Point start;
    private final Point end;
    private final Color color;
    private final int strokeWidth;

    public Line(Point start, Point end, Color color, int strokeWidth) {
        this.start = start;
        this.end = end;
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    public Point getStart() {
        return start;
    }

    public Point getEnd() {
        return end;
    }

    public Color getColor() {
        return color;
    }

    public int getStrokeWidth() {
        return strokeWidth;
    }
}
