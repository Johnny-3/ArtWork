package com.artwork.mvc.model;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Model holding drawing state (lines, color, stroke, shape selection, brush style,
 * eraser, and dark mode). Bitmap-based brushes and their undo/redo live in DrawingPanel.
 *
 * ShapeType enum must include: FREEHAND, CIRCLE, SQUARE, TRIANGLE.
 */
public class DrawingModel {

    // Vector freehand lines (legacy path strokes). Bitmap brushes are not stored here.
    private final List<Line> lines = new ArrayList<>();

    private Color currentColor = Color.BLACK;
    private int strokeWidth = 2;

    /** Currently selected drawing tool: FREEHAND or a shape. */
    private ShapeType currentShapeType = ShapeType.FREEHAND;

    /** When drawing shapes, whether to fill them. */
    private boolean shapeFill = false;

    /** Brush style used when currentShapeType == FREEHAND. */
    private BrushStyle brushStyle = BrushStyle.BASIC;

    // Eraser
    private boolean eraserMode = false;
    private int eraserRadius = 10;

    // Dark mode
    private boolean darkMode = false;

    // ============ LINES (public API for normal FREEHAND drawing) ============

    /**
     * Freehand segments are only accepted when NOT erasing and the current tool is FREEHAND.
     * Callers that try to add a segment in shape/eraser mode are ignored.
     */
    public void addLine(Line line) {
        if (eraserMode) return;
        if (currentShapeType != ShapeType.FREEHAND) return;
        lines.add(line);
    }

    public List<Line> getLines() {
        return new ArrayList<>(lines);
    }

    public void clear() {
        lines.clear();
    }

    // ============ DIRECT HELPERS (used by undo/redo only) ============

    /** Add a single line regardless of current tool/eraser state. */
    public void addLineDirect(Line l) {
        lines.add(l);
    }

    /** Add many lines regardless of current tool/eraser state. */
    public void addLinesDirect(Collection<Line> ls) {
        lines.addAll(ls);
    }

    /** Remove the first occurrence of each given line (by reference) regardless of state. */
    public void removeLinesDirect(Collection<Line> ls) {
        for (Line l : ls) {
            lines.remove(l);
        }
    }

    // ============ COLOR / STROKE ============

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

    // ============ SHAPE SELECTION ============

    public ShapeType getCurrentShapeType() {
        return currentShapeType;
    }

    public void setCurrentShapeType(ShapeType currentShapeType) {
        this.currentShapeType = currentShapeType;
    }

    public boolean isShapeFill() {
        return shapeFill;
    }

    public void setShapeFill(boolean shapeFill) {
        this.shapeFill = shapeFill;
    }

    // ============ BRUSH STYLE (FREEHAND) ============

    public BrushStyle getBrushStyle() {
        return brushStyle;
    }

    public void setBrushStyle(BrushStyle style) {
        this.brushStyle = (style == null) ? BrushStyle.BASIC : style;
    }

    // ============ ERASER ============

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

    // -- Erase session tracking for undo/redo of vector lines --

    private boolean eraseSessionActive = false;
    private final List<Line> erasedThisSession = new ArrayList<>();

    /** Call at mousePressed when entering eraser drag. */
    public void beginEraseSession() {
        eraseSessionActive = true;
        erasedThisSession.clear();
    }

    /**
     * Call at mouseReleased after an eraser drag.
     * Returns the lines removed during the session (in the order they were removed).
     */
    public List<Line> endEraseSession() {
        eraseSessionActive = false;
        return new ArrayList<>(erasedThisSession);
    }

    /**
     * Remove any line segments within {@code eraserRadius} pixels of point p.
     * If an erase session is active, removed lines are recorded for undo/redo.
     */
    public void eraseAt(Point p) {
        Iterator<Line> it = lines.iterator();
        while (it.hasNext()) {
            Line l = it.next();
            double dist = Line2D.ptSegDist(
                    l.getStart().x, l.getStart().y,
                    l.getEnd().x,   l.getEnd().y,
                    p.x,            p.y
            );
            if (dist <= eraserRadius) {
                it.remove();
                if (eraseSessionActive) erasedThisSession.add(l);
            }
        }
    }

    // ============ DARK MODE ============

    public boolean isDarkMode() {
        return darkMode;
    }

    /** Toggle dark mode: complement legacy vector lines and the current drawing color. */
    public void toggleDarkMode() {
        darkMode = !darkMode;
        complementAllLines();
        currentColor = complementColor(currentColor);
    }

    private void complementAllLines() {
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            lines.set(i, new Line(
                    l.getStart(),
                    l.getEnd(),
                    complementColor(l.getColor()),
                    l.getStrokeWidth()
            ));
        }
    }

    public static Color complementColor(Color c) {
        return new Color(255 - c.getRed(), 255 - c.getGreen(), 255 - c.getBlue());
    }
}