package com.artwork.mvc.view;

import com.artwork.mvc.model.BrushStyle;
import com.artwork.mvc.model.DrawingModel;
import com.artwork.mvc.model.Line;
import com.artwork.mvc.model.ShapeType;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.awt.AlphaComposite;
import java.awt.RadialGradientPaint;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Canvas panel. Renders, in order:
 *   - Optional background image (scaled to panel)
 *   - Committed shapes layer (vector -> bitmap)
 *   - Paint layer (bitmap: brush / watercolor / oil)
 *   - Legacy vector freehand lines (if the model has any)
 *   - Live shape preview
 *   - Eraser preview ring
 *
 * Undo/Redo:
 *   - Bitmap brushes: one PaintCommit (plus any incidental vector lines) per drag.
 *   - Vector freehand (when brush style is null): one VectorStroke per drag.
 *   - Shapes: ShapeCommit with before/after shape layer.
 *   - Eraser: removes vector lines and punches holes in paint; both are undoable.
 */
public class DrawingPanel extends JPanel {

    private final DrawingModel model;

    // Optional background image.
    private BufferedImage backgroundImage;

    // Persistent layers.
    private BufferedImage shapeLayer; // committed shapes
    private BufferedImage paintLayer; // brush/watercolor/oil

    // Eraser preview.
    private Point eraserPosition;

    // Live rubber-band state for shapes.
    private boolean draggingShape = false;
    private Point shapeStart = null;
    private Point shapeEnd = null;

    // Freehand state
    private Point lastFreePoint = null;

    // Bitmap brush state
    private boolean paintingWithBrush = false;
    private BufferedImage paintStrokeBefore = null;

    // Vector stroke grouping
    private int vectorStartCount = -1;

    // Eraser: capture paint layer before the drag for undo.
    private BufferedImage erasePaintBefore = null;
    private boolean paintErasedThisDrag = false;

    // Undo/Redo stacks.
    private final Deque<CanvasOp> undoStack = new ArrayDeque<>();
    private final Deque<CanvasOp> redoStack = new ArrayDeque<>();


    public DrawingPanel(DrawingModel model) {
        this.model = model;
        setOpaque(true);
        setBackground(Color.WHITE);

        // Keep layers sized to panel; preserve contents on resize.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                ensureShapeLayerSized();
                ensurePaintLayerSized();
                repaint();
            }
        });

        // Mouse interactions.
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (model.isEraserMode()) {
                    model.beginEraseSession();
                    erasePaintBefore = copyImage(paintLayer);
                    paintErasedThisDrag = false;
                    return;
                }

                if (model.getCurrentShapeType() == ShapeType.FREEHAND) {
                    BrushStyle bs = model.getBrushStyle();
                    lastFreePoint = e.getPoint();
                    vectorStartCount = model.getLines().size();

                    if (bs == null) {
                        // Plain vector stroke (deselected brush button).
                        paintingWithBrush = false;
                        paintStrokeBefore = null;
                    } else {
                        // Bitmap brush stroke.
                        ensurePaintLayerSized();
                        paintingWithBrush = true;
                        paintStrokeBefore = copyImage(paintLayer);
                    }
                } else {
                    // Start shape rubber-banding.
                    draggingShape = true;
                    shapeStart = e.getPoint();
                    shapeEnd   = e.getPoint();
                    repaint();
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (model.isEraserMode()) {
                    List<Line> removed = model.endEraseSession();
                    if (paintErasedThisDrag || !removed.isEmpty()) {
                        BufferedImage after = copyImage(paintLayer);
                        pushAndClearRedo(new EraseMixedOp(removed, erasePaintBefore, after));
                    }
                    erasePaintBefore = null;
                    paintErasedThisDrag = false;
                    repaint();
                    return;
                }

                if (model.getCurrentShapeType() == ShapeType.FREEHAND) {
                    BrushStyle bs = model.getBrushStyle();
                    if (lastFreePoint != null) {
                        if (bs == null) {
                            // Finalize vector-only stroke (group segs for undo)
                            List<Line> all = model.getLines();
                            List<Line> segs = new ArrayList<>();
                            if (vectorStartCount >= 0 && vectorStartCount < all.size()) {
                                segs.addAll(all.subList(vectorStartCount, all.size()));
                            }
                            if (!segs.isEmpty()) {
                                pushAndClearRedo(new VectorStrokeOp(segs));
                            }
                        } else {
                            // Finalize bitmap brush stroke + any vector additions
                            BufferedImage after = copyImage(paintLayer);
                            List<Line> addedVectors = List.of();
                            List<Line> all = model.getLines();
                            if (vectorStartCount >= 0 && vectorStartCount <= all.size() - 1) {
                                addedVectors = new ArrayList<>(all.subList(vectorStartCount, all.size()));
                            }
                            pushAndClearRedo(new PaintAndVectorOp(paintStrokeBefore, after, addedVectors));
                        }
                    }

                    // Reset stroke state
                    paintingWithBrush = false;
                    paintStrokeBefore = null;
                    lastFreePoint = null;
                    vectorStartCount = -1;

                } else if (draggingShape) {
                    shapeEnd = e.getPoint();
                    BufferedImage before = copyImage(shapeLayer);
                    commitShapeToLayer(shapeStart, shapeEnd);
                    BufferedImage after = copyImage(shapeLayer);
                    pushAndClearRedo(new ShapeCommitOp(before, after));
                    draggingShape = false;
                    shapeStart = shapeEnd = null;
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                setEraserPosition(e.getPoint());
                if (model.isEraserMode()) repaint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                setEraserPosition(e.getPoint());

                if (model.isEraserMode()) {
                    // Erase vector lines.
                    model.eraseAt(e.getPoint());

                    // Erase paint layer transparently.
                    if (paintLayer != null) {
                        Graphics2D g2 = paintLayer.createGraphics();
                        try {
                            g2.setComposite(AlphaComposite.Clear);
                            int r = model.getEraserRadius();
                            g2.fillOval(e.getX() - r, e.getY() - r, r * 2, r * 2);
                            paintErasedThisDrag = true;
                        } finally {
                            g2.dispose();
                        }
                    }
                    repaint();
                    return;
                }

                if (model.getCurrentShapeType() == ShapeType.FREEHAND) {
                    if (lastFreePoint != null) {
                        BrushStyle bs = model.getBrushStyle();
                        if (bs == null) {
                            // Plain vector segment
                            Line seg = new Line(
                                    lastFreePoint,
                                    e.getPoint(),
                                    model.getCurrentColor(),
                                    model.getStrokeWidth()
                            );
                            model.addLine(seg);
                        } else {
                            // Bitmap brush engines
                            ensurePaintLayerSized();
                            switch (bs) {
                                case BASIC -> raggedBrushBetween(lastFreePoint, e.getPoint());
                                case WATERCOLOR -> watercolorBetween(lastFreePoint, e.getPoint());
                                case OIL -> oilBetween(lastFreePoint, e.getPoint());
                            }
                        }
                        lastFreePoint = e.getPoint();
                        repaint();
                    }
                } else if (draggingShape) {
                    shapeEnd = e.getPoint();
                    repaint();
                }
            }
        });
    }

    // ============ Public API used by other panels ============

    public void undo() {
        if (undoStack.isEmpty()) return;
        CanvasOp op = undoStack.pop();
        op.undo();
        redoStack.push(op);
        repaint();
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        CanvasOp op = redoStack.pop();
        op.redo();
        undoStack.push(op);
        repaint();
    }

    public void setEraserPosition(Point p) { this.eraserPosition = p; }

    /** Clears layers and eraser overlay, and clears undo/redo stacks. */
    public void clearCanvas() {
        clearShapeLayer();
        clearPaintLayer();
        setEraserPosition(null);
        undoStack.clear();
        redoStack.clear();
        // reset any in-progress counters
        paintingWithBrush = false;
        lastFreePoint = null;
        paintStrokeBefore = null;
        vectorStartCount = -1;
        repaint();
    }

    public void setBackgroundImage(BufferedImage img) {
        this.backgroundImage = img;
        repaint();
    }

    /** Remove background image and return to solid panel background. */
    public void clearBackgroundImage() {
        this.backgroundImage = null;
        repaint();
    }

    /**
     * Invert colors of shape and paint layers (used for Dark Mode toggles).
     * Leaves background image untouched. Vector lines are handled by the model.
     */
    public void flipCompositeLayersForDarkMode() {
        if (shapeLayer != null) invertArgbImageInPlace(shapeLayer);
        if (paintLayer != null) invertArgbImageInPlace(paintLayer);
        repaint();
    }

    /**
     * File-chooser that replaces the background image and clears current content.
     * Used by the Upload control in the top bar.
     */
    public void promptAndReplaceBackgroundImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose an image…");
        chooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter imgFilter = new FileNameExtensionFilter(
                "Image files (PNG, JPG, JPEG, GIF)", "png", "jpg", "jpeg", "gif");
        chooser.addChoosableFileFilter(imgFilter);
        chooser.setFileFilter(imgFilter);

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File f = chooser.getSelectedFile();
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                JOptionPane.showMessageDialog(this,
                        "That file doesn't look like a supported image.\nPlease pick a PNG, JPG/JPEG, or GIF.",
                        "Unsupported file",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            model.clear();         // legacy vector lines
            clearShapeLayer();     // shapes
            clearPaintLayer();     // paint
            setEraserPosition(null);
            setBackgroundImage(img);
            undoStack.clear();
            redoStack.clear();
            paintingWithBrush = false;
            lastFreePoint = null;
            paintStrokeBefore = null;
            vectorStartCount = -1;
            repaint();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't open that image:\n" + ex.getMessage(),
                    "Open failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(900, 600); }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Background image (optional)
        if (backgroundImage != null) {
            g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
        }

        ensureShapeLayerSized();
        ensurePaintLayerSized();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (shapeLayer != null) g2.drawImage(shapeLayer, 0, 0, null);
            if (paintLayer != null) g2.drawImage(paintLayer, 0, 0, null);

            // Legacy vector freehand (if any)
            for (Line l : model.getLines()) {
                g2.setColor(l.getColor());
                g2.setStroke(new BasicStroke(
                        l.getStrokeWidth(),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.drawLine(l.getStart().x, l.getStart().y, l.getEnd().x, l.getEnd().y);
            }

            // Live shape preview
            if (draggingShape && shapeStart != null && shapeEnd != null
                    && !model.isEraserMode()
                    && model.getCurrentShapeType() != ShapeType.FREEHAND) {

                Rectangle rect = rectFrom(shapeStart, shapeEnd);
                if (model.getCurrentShapeType() == ShapeType.CIRCLE
                        || model.getCurrentShapeType() == ShapeType.SQUARE) {
                    rect = squareFrom(shapeStart, shapeEnd);
                }

                g2.setStroke(new BasicStroke(Math.max(1f, (float) model.getStrokeWidth()),
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color c = model.getCurrentColor();
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 180));

                switch (model.getCurrentShapeType()) {
                    case CIRCLE -> {
                        if (model.isShapeFill()) g2.fillOval(rect.x, rect.y, rect.width, rect.height);
                        else                     g2.drawOval(rect.x, rect.y, rect.width, rect.height);
                    }
                    case SQUARE -> {
                        if (model.isShapeFill()) g2.fillRect(rect.x, rect.y, rect.width, rect.height);
                        else                     g2.drawRect(rect.x, rect.y, rect.width, rect.height);
                    }
                    case TRIANGLE -> {
                        Polygon tri = triangleIn(rect);
                        if (model.isShapeFill()) g2.fillPolygon(tri);
                        else                     g2.drawPolygon(tri);
                    }
                    default -> { /* FREEHAND: no preview */ }
                }
            }

            // Eraser ring
            if (eraserPosition != null && model.isEraserMode()) {
                int r = model.getEraserRadius();
                int d = r * 2;
                g2.setColor(new Color(0, 0, 0, 40));
                g2.fillOval(eraserPosition.x - r, eraserPosition.y - r, d, d);
                g2.setColor(new Color(0, 0, 0, 120));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(eraserPosition.x - r, eraserPosition.y - r, d, d);
            }
        } finally {
            g2.dispose();
        }
    }

    // ---- shape rendering helpers ----

    private void commitShapeToLayer(Point a, Point b) {
        ensureShapeLayerSized();
        if (shapeLayer == null) return;

        Rectangle rect = rectFrom(a, b);
        if (model.getCurrentShapeType() == ShapeType.CIRCLE
                || model.getCurrentShapeType() == ShapeType.SQUARE) {
            rect = squareFrom(a, b);
        }

        Graphics2D g2 = shapeLayer.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(Math.max(1f, (float) model.getStrokeWidth()),
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(model.getCurrentColor());

            switch (model.getCurrentShapeType()) {
                case CIRCLE -> {
                    if (model.isShapeFill()) g2.fillOval(rect.x, rect.y, rect.width, rect.height);
                    else                     g2.drawOval(rect.x, rect.y, rect.width, rect.height);
                }
                case SQUARE -> {
                    if (model.isShapeFill()) g2.fillRect(rect.x, rect.y, rect.width, rect.height);
                    else                     g2.drawRect(rect.x, rect.y, rect.width, rect.height);
                }
                case TRIANGLE -> {
                    Polygon tri = triangleIn(rect);
                    if (model.isShapeFill()) g2.fillPolygon(tri);
                    else                     g2.drawPolygon(tri);
                }
                default -> { /* FREEHAND/other: nothing to commit */ }
            }
        } finally {
            g2.dispose();
        }
    }

    private static Rectangle rectFrom(Point a, Point b) {
        int x = Math.min(a.x, b.x);
        int y = Math.min(a.y, b.y);
        int w = Math.abs(a.x - b.x);
        int h = Math.abs(a.y - b.y);
        return new Rectangle(x, y, w, h);
    }

    private static Rectangle squareFrom(Point start, Point end) {
        int dx = end.x - start.x;
        int dy = end.y - start.y;
        int side = Math.min(Math.abs(dx), Math.abs(dy));
        int x = dx < 0 ? start.x - side : start.x;
        int y = dy < 0 ? start.y - side : start.y;
        return new Rectangle(x, y, side, side);
    }

    private static Polygon triangleIn(Rectangle r) {
        int x1 = r.x, y1 = r.y;
        int x2 = r.x + r.width, y2 = r.y + r.height;

        int apexX = x1 + r.width / 2;
        int apexY = y1;
        int baseLx = x1, baseLy = y2;
        int baseRx = x2, baseRy = y2;

        Polygon p = new Polygon();
        p.addPoint(apexX, apexY);
        p.addPoint(baseLx, baseLy);
        p.addPoint(baseRx, baseRy);
        return p;
    }

    // ---- layer management ----

    private void ensureShapeLayerSized() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());

        if (shapeLayer == null) {
            shapeLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            return;
        }
        if (shapeLayer.getWidth() == w && shapeLayer.getHeight() == h) return;

        BufferedImage newLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = newLayer.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Src);
            g2.drawImage(shapeLayer, 0, 0, null);
        } finally {
            g2.dispose();
        }
        shapeLayer = newLayer;
    }

    private void ensurePaintLayerSized() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());

        if (paintLayer == null) {
            paintLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            return;
        }
        if (paintLayer.getWidth() == w && paintLayer.getHeight() == h) return;

        BufferedImage newLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = newLayer.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Src);
            g2.drawImage(paintLayer, 0, 0, null);
        } finally {
            g2.dispose();
        }
        paintLayer = newLayer;
    }

    private void clearShapeLayer() {
        if (shapeLayer == null) return;
        Graphics2D g2 = shapeLayer.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, shapeLayer.getWidth(), shapeLayer.getHeight());
        } finally {
            g2.dispose();
        }
    }

    private void clearPaintLayer() {
        if (paintLayer == null) return;
        Graphics2D g2 = paintLayer.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Clear);
            g2.fillRect(0, 0, paintLayer.getWidth(), paintLayer.getHeight());
        } finally {
            g2.dispose();
        }
    }

    private static BufferedImage copyImage(BufferedImage src) {
        if (src == null) return null;
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = copy.createGraphics();
        try {
            g2.setComposite(AlphaComposite.Src);
            g2.drawImage(src, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return copy;
    }

    /** Invert RGB channels in-place for non-transparent pixels. */
    private static void invertArgbImageInPlace(BufferedImage img) {
        final int w = img.getWidth();
        final int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) continue; // keep fully transparent pixels
                int r = 255 - ((argb >> 16) & 0xFF);
                int g = 255 - ((argb >> 8)  & 0xFF);
                int b = 255 - (argb & 0xFF);
                img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    // ===================== BRUSH ENGINES =====================

    /** Ragged “ink brush”: jittered core band with tapered spikes and occasional micro-gaps. */
    private void raggedBrushBetween(Point a, Point b) {
        if (paintLayer == null) return;

        double dx = b.x - a.x, dy = b.y - a.y;
        double dist = Math.hypot(dx, dy);
        if (dist == 0) return;

        double ux = dx / dist, uy = dy / dist;   // unit tangent
        double nx = -uy, ny = ux;                // unit normal

        int baseW = Math.max(2, model.getStrokeWidth() * 2);
        int step = Math.max(1, model.getStrokeWidth() / 2);
        int steps = Math.max(1, (int) (dist / step));

        Graphics2D g2 = paintLayer.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = model.getCurrentColor();

            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                double x = a.x + dx * t;
                double y = a.y + dy * t;

                float width = (float) (baseW * (0.85 + Math.random() * 0.4));
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(base);
                g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine((int) Math.round(x - ux), (int) Math.round(y - uy),
                        (int) Math.round(x + ux), (int) Math.round(y + uy));

                int spikes = (int) Math.max(1, width / 10);
                for (int s = 0; s < spikes; s++) {
                    if (Math.random() < 0.25) {
                        double side = (Math.random() < 0.5) ? 1 : -1;
                        double bx = x + nx * side * (width * 0.5 * (0.6 + Math.random() * 0.6));
                        double by = y + ny * side * (width * 0.5 * (0.6 + Math.random() * 0.6));
                        double len = width * (0.3 + Math.random() * 0.9);
                        double halfBase = Math.max(1, width * (0.05 + Math.random() * 0.12));

                        int ax = (int) Math.round(bx + nx * side * len);
                        int ay = (int) Math.round(by + ny * side * len);
                        int b1x = (int) Math.round(bx - ux * halfBase);
                        int b1y = (int) Math.round(by - uy * halfBase);
                        int b2x = (int) Math.round(bx + ux * halfBase);
                        int b2y = (int) Math.round(by + uy * halfBase);

                        Polygon spike = new Polygon();
                        spike.addPoint(ax, ay);
                        spike.addPoint(b1x, b1y);
                        spike.addPoint(b2x, b2y);
                        g2.fillPolygon(spike);
                    }
                }

                if (Math.random() < 0.05) {
                    int gap = (int) Math.max(1, width * 0.4);
                    g2.setComposite(AlphaComposite.Clear);
                    g2.fillOval((int) Math.round(x - gap / 2.0), (int) Math.round(y - gap / 2.0), gap, gap);
                    g2.setComposite(AlphaComposite.SrcOver);
                }
            }
        } finally {
            g2.dispose();
        }
    }

    /** Watercolor: soft radial dabs that build translucently. */
    private void watercolorBetween(Point a, Point b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double dist = Math.hypot(dx, dy);
        if (dist == 0) return;

        int step = Math.max(1, model.getStrokeWidth() / 2);
        int steps = Math.max(1, (int) (dist / step));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(a.x + dx * t);
            int y = (int) Math.round(a.y + dy * t);
            watercolorDab(x, y);
        }
    }

    private void watercolorDab(int cx, int cy) {
        Graphics2D g2 = paintLayer.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = model.getCurrentColor();

            double rBase = Math.max(4, model.getStrokeWidth() * 1.3);
            double r = rBase * (0.85 + Math.random() * 0.6);

            float alphaCenter = 0.16f;
            Color cCenter = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.round(alphaCenter * 255));
            Color cEdge   = new Color(base.getRed(), base.getGreen(), base.getBlue(), 0);

            RadialGradientPaint rgp = new RadialGradientPaint(
                    new Point(cx, cy), (float) r,
                    new float[]{0f, 1f},
                    new Color[]{cCenter, cEdge}
            );
            g2.setPaint(rgp);
            g2.setComposite(AlphaComposite.SrcOver);
            g2.fillOval((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
        } finally {
            g2.dispose();
        }
    }

    /** Oil: clearly translucent glaze — many low-alpha bristles plus very soft smears. */
    private void oilBetween(Point a, Point b) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double dist = Math.hypot(dx, dy);
        if (dist == 0) return;

        int step = Math.max(1, model.getStrokeWidth() / 2);
        int steps = Math.max(1, (int) (dist / step));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(a.x + dx * t);
            int y = (int) Math.round(a.y + dy * t);
            oilDab(x, y, a, b);
        }
    }

    // --- Translucent oil dab ---
    private void oilDab(int cx, int cy, Point from, Point to) {
        Graphics2D g2 = paintLayer.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = model.getCurrentColor();

            int w = Math.max(3, model.getStrokeWidth());
            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double len = Math.hypot(dx, dy);
            double ux = (len == 0) ? 1 : dx / len;  // tangent
            double uy = (len == 0) ? 0 : dy / len;
            double px = -uy, py = ux;               // normal

            // Semi-translucent bristles (10–20% alpha).
            int bristles = Math.max(5, w * 2);
            for (int i = 0; i < bristles; i++) {
                double spread = (Math.random() - 0.5) * w * 1.2;
                int bx = (int) Math.round(cx + px * spread);
                int by = (int) Math.round(cy + py * spread);

                double seg = w * (0.7 + Math.random() * 1.0);
                int ex = (int) Math.round(bx + ux * seg);
                int ey = (int) Math.round(by + uy * seg);

                float a = (float) (0.10 + Math.random() * 0.10); // 10–20%
                int dr = (int) Math.round((Math.random() - 0.5) * 12);
                int dg = (int) Math.round((Math.random() - 0.5) * 12);
                int db = (int) Math.round((Math.random() - 0.5) * 12);
                Color jitter = new Color(
                        clamp(base.getRed() + dr),
                        clamp(base.getGreen() + dg),
                        clamp(base.getBlue() + db)
                );
                Color c = blendTowardWhite(jitter, 0.10f); // light glaze

                float sw = (float) Math.max(0.8, (0.5 + Math.random() * 1.2) * (w / 3f));
                g2.setComposite(AlphaComposite.SrcOver.derive(a));
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (a * 255)));
                g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(bx, by, ex, ey);
            }

            // Very soft smear (~10% alpha) to hint at blending.
            if (Math.random() < 0.25) {
                int rw = (int) Math.max(4, w * 1.8);
                int rh = (int) Math.max(3, w * 1.2);
                g2.setComposite(AlphaComposite.SrcOver.derive(0.10f));
                Color smear = blendTowardWhite(base, 0.12f);
                g2.setColor(new Color(smear.getRed(), smear.getGreen(), smear.getBlue(), (int) (0.10f * 255)));
                g2.fillOval(cx - rw / 2, cy - rh / 2, rw, rh);
            }
        } finally {
            g2.dispose();
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static Color blendTowardWhite(Color c, float t) {
        int r = clamp((int) Math.round(c.getRed()   * (1 - t) + 255 * t));
        int g = clamp((int) Math.round(c.getGreen() * (1 - t) + 255 * t));
        int b = clamp((int) Math.round(c.getBlue()  * (1 - t) + 255 * t));
        return new Color(r, g, b);
    }

    // ---- Undo/Redo infrastructure ----

    private void pushAndClearRedo(CanvasOp op) {
        undoStack.push(op);
        redoStack.clear();
    }

    private interface CanvasOp {
        void undo();
        void redo();
    }

    private class ShapeCommitOp implements CanvasOp {
        private final BufferedImage before, after;
        ShapeCommitOp(BufferedImage before, BufferedImage after) { this.before = before; this.after = after; }
        @Override public void undo() { shapeLayer = (before == null) ? null : copyImage(before); }
        @Override public void redo() { shapeLayer = (after  == null) ? null : copyImage(after);  }
    }

    private class PaintCommitOp implements CanvasOp {
        private final BufferedImage before, after;
        PaintCommitOp(BufferedImage before, BufferedImage after) { this.before = before; this.after = after; }
        @Override public void undo() { paintLayer = (before == null) ? null : copyImage(before); }
        @Override public void redo() { paintLayer = (after  == null) ? null : copyImage(after);  }
    }

    /** Vector-only freehand stroke grouped as one undoable op. */
    private class VectorStrokeOp implements CanvasOp {
        private final List<Line> segments;
        VectorStrokeOp(List<Line> segments) { this.segments = new ArrayList<>(segments); }
        @Override public void undo() { model.removeLinesDirect(segments); }
        @Override public void redo() { model.addLinesDirect(segments); }
    }

    /**
     * A brush drag that also (possibly) created vector segments via other code.
     * Undo removes those vector lines AND restores the paint layer snapshot.
     */
    private class PaintAndVectorOp implements CanvasOp {
        private final BufferedImage before, after;
        private final List<Line> vectorAdded; // lines added during the brush drag

        PaintAndVectorOp(BufferedImage before, BufferedImage after, List<Line> vectorAdded) {
            this.before = before;
            this.after = after;
            this.vectorAdded = new ArrayList<>(vectorAdded);
        }

        @Override public void undo() {
            if (!vectorAdded.isEmpty()) model.removeLinesDirect(vectorAdded);
            paintLayer = (before == null) ? null : copyImage(before);
        }

        @Override public void redo() {
            if (!vectorAdded.isEmpty()) model.addLinesDirect(vectorAdded);
            paintLayer = (after == null) ? null : copyImage(after);
        }
    }

    /** Eraser drag that removed vector lines and/or modified the paint layer. */
    private class EraseMixedOp implements CanvasOp {
        private final List<Line> removed;
        private final BufferedImage paintBefore, paintAfter;
        EraseMixedOp(List<Line> removed, BufferedImage paintBefore, BufferedImage paintAfter) {
            this.removed = new ArrayList<>(removed);
            this.paintBefore = paintBefore;
            this.paintAfter  = paintAfter;
        }
        @Override public void undo() {
            for (Line l : removed) model.addLineDirect(l);
            paintLayer = (paintBefore == null) ? null : copyImage(paintBefore);
        }
        @Override public void redo() {
            model.removeLinesDirect(removed);
            paintLayer = (paintAfter  == null) ? null : copyImage(paintAfter);
        }
    }

    /** Render exactly what's on the canvas to an ARGB image. */
    public BufferedImage renderCanvasToImage() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        try {
            this.paint(g2);
        } finally {
            g2.dispose();
        }
        return out;
    }
}