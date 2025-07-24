package com.artwork.mvc.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class DrawingModel {
    private final List<Line> lines = new ArrayList<>();
    private Color currentColor = Color.BLACK;
    private int strokeWidth = 2;

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

    public void clear() {
        lines.clear();
    }
}
