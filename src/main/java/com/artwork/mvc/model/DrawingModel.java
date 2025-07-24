package com.artwork.mvc.model;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.artwork.mvc.model.ShapeType;

/**
 * Model storing the lines and current drawing state.
 */

public class DrawingModel {
    private final List<Line> lines = new ArrayList<>();
    private Color currentColor = Color.BLACK;
    private int strokeWidth = 2;

    /** Currently selected drawing shape. */
    private ShapeType currentShapeType = ShapeType.FREEHAND;

    private boolean eraserMode = false;
    private int eraserRadius = 10;
    private boolean darkMode = false;

    public void addLine(Line line) {
        lines.add(line);
    }

    public List<Line> getLines() {
        return new ArrayList<>(lines);
    }

    public Color getCurrentColor() {
        return currentColor;
    }

    public void setCurrentColor(Color currentColor) {
        this.currentColor = currentColor;
    }

    public int getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeWidth(int strokeWidth) {
        this.strokeWidth = strokeWidth;
    }

    public ShapeType getCurrentShapeType() {
        return currentShapeType;
    }

    public void setCurrentShapeType(ShapeType currentShapeType) {
        this.currentShapeType = currentShapeType;
    }

    public void clear() {
        lines.clear();
    }

    public boolean isEraserMode() {
        return eraserMode;
    }

    public void setEraserMode(boolean eraserMode) {
        this.eraserMode = eraserMode;
    }

    public int getEraserRadius() {
        return eraserRadius;
    }

    public void setEraserRadius(int eraserRadius) {
        this.eraserRadius = eraserRadius;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void toggleDarkMode() {
        darkMode = !darkMode;
        complementAllLines();
        currentColor = complementColor(currentColor);
    }

    public void eraseAt(Point p) {
        Iterator<Line> it = lines.iterator();
        while (it.hasNext()) {
            Line l = it.next();
            double dist = Line2D.ptSegDist(l.getStart().x, l.getStart().y,
                    l.getEnd().x, l.getEnd().y, p.x, p.y);
            if (dist <= eraserRadius) {
                it.remove();
            }
        }
    }

    private void complementAllLines() {
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            lines.set(i, new Line(l.getStart(), l.getEnd(),
                    complementColor(l.getColor()), l.getStrokeWidth()));
        }
    }

    public static Color complementColor(Color c) {
        return new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue());
    }
}
