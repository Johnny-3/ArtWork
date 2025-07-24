package com.artwork.mvc.model;

/**
 * Enumeration of drawing shapes supported by the application.
 */
public enum ShapeType {
    /** Freehand drawing using continuous lines. */
    FREEHAND,
    /** Axis-aligned rectangle drawn from drag start to end. */
    RECTANGLE,
    /** Ellipse drawn inside the drag bounding box. */
    OVAL
}
