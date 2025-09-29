package com.artwork.mvc.view;

import com.artwork.mvc.model.BrushStyle;
import com.artwork.mvc.model.DrawingModel;
import com.artwork.mvc.model.ShapeType;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Left control rail:
 *  - Color swatches + thickness
 *  - Eraser + radius
 *  - More colors / Reset
 *  - Shape toggles
 *  - Brush-style toggles: Basic / Watercolor / Oil
 *  - Undo / Redo
 *
 * Behavior:
 *  - Brush buttons show darker border when selected, lighter when not.
 *  - Clicking an already-selected brush again deselects it.
 *  - Selecting any brush clears shape selection and vice-versa (mutually exclusive).
 *  - Selecting a shape/brush exits eraser mode.
 */
public class ControlPanel extends JPanel {
    private static final java.util.List<Color> PALETTE = Arrays.asList(
            Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.ORANGE, Color.MAGENTA,
            Color.CYAN, Color.PINK, Color.YELLOW, Color.GRAY, new Color(139, 69, 19), Color.WHITE
    );

    private static final Color SELECTED_OUTLINE = new Color(80, 130, 255);
    private static final Color NORMAL_OUTLINE   = new Color(80, 80, 80);

    private static final Color BRUSH_BORDER_OFF = new Color(185, 185, 185);
    private static final Color BRUSH_BORDER_ON  = new Color(110, 110, 110);

    private final DrawingModel model;
    private final DrawingPanel canvas;

    private final Map<Color, ColorIcon> iconByColor = new HashMap<>();
    private final JButton eraserBtn;
    private final JButton moreColoursBtn;
    private final JSlider thicknessSlider;
    private final JSlider eraserSlider;

    // Shape tool controls
    private ButtonGroup shapeGroup;
    private JToggleButton triBtn;
    private JToggleButton circBtn;
    private JToggleButton squareBtn;

    // Brush-style controls
    private JToggleButton brushBtn;
    private JToggleButton waterBtn;
    private JToggleButton oilBtn;
    private ButtonGroup brushGroup; // allows clearSelection() for brushes

    public ControlPanel(DrawingModel model, DrawingPanel canvas) {
        this.model = model;
        this.canvas = canvas;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // --- Swatches (2 rows) + thickness slider ---
        JComponent paletteGrid = buildColourGrid();
        paletteGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, paletteGrid.getPreferredSize().height));
        add(paletteGrid);

        add(Box.createVerticalStrut(12));

        thicknessSlider = new JSlider(SwingConstants.HORIZONTAL, 1, 15, model.getStrokeWidth());
        thicknessSlider.setPreferredSize(new Dimension(160, 40));
        thicknessSlider.addChangeListener(e -> model.setStrokeWidth(thicknessSlider.getValue()));
        thicknessSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, thicknessSlider.getPreferredSize().height));
        add(thicknessSlider);

        // --- Eraser button + radius slider ---
        add(Box.createVerticalStrut(16));

        ImageIcon eraserIcon = loadScaledIcon("/images/eraser.png", 50, 50);
        eraserBtn = new JButton(eraserIcon);
        eraserBtn.setFocusable(false);
        Dimension snug = new Dimension(eraserIcon.getIconWidth() + 12, eraserIcon.getIconHeight() + 12);
        eraserBtn.setPreferredSize(snug);
        eraserBtn.setMinimumSize(snug);
        eraserBtn.setMaximumSize(snug);
        eraserBtn.setContentAreaFilled(true);
        eraserBtn.setOpaque(true);
        eraserBtn.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true));
        eraserBtn.addActionListener(e -> toggleEraser());
        eraserBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(eraserBtn);

        eraserSlider = new JSlider(5, 50, model.getEraserRadius());
        eraserSlider.addChangeListener(e -> model.setEraserRadius(eraserSlider.getValue()));
        eraserSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, eraserSlider.getPreferredSize().height));
        add(eraserSlider);

        // --- More colors + Reset (compact row) ---
        add(Box.createVerticalStrut(16));
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        moreColoursBtn = new JButton("More colors");
        moreColoursBtn.setBackground(Color.LIGHT_GRAY);
        moreColoursBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Pick color", model.getCurrentColor());
            if (c != null) selectColour(c);
        });
        bottomBar.add(moreColoursBtn);

        JButton resetBtn = new JButton("Reset");
        resetBtn.setBackground(Color.LIGHT_GRAY);
        resetBtn.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to clear the canvas?\nThis action cannot be undone.",
                    "Confirm Reset",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice == JOptionPane.OK_OPTION) {
                model.clear();           // clears legacy vector lines
                canvas.clearCanvas();    // clears shape + paint layers + undo/redo + eraser cursor
                canvas.setEraserPosition(null);
                canvas.repaint();
            }
        });
        bottomBar.add(resetBtn);
        bottomBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, bottomBar.getPreferredSize().height));
        add(bottomBar);

        // --- Shape icon row (triangle • circle • square) ---
        add(Box.createVerticalStrut(30));
        shapeGroup = new ButtonGroup();

        triBtn    = shapeToggle(new ShapeIcon(ShapeType.TRIANGLE, 28, 28));
        circBtn   = shapeToggle(new ShapeIcon(ShapeType.CIRCLE,   28, 28));
        squareBtn = shapeToggle(new ShapeIcon(ShapeType.SQUARE,   28, 28));

        triBtn.setToolTipText("Triangle (filled)");
        circBtn.setToolTipText("Circle (filled)");
        squareBtn.setToolTipText("Square (filled)");

        hookShapeToggle(triBtn, ShapeType.TRIANGLE);
        hookShapeToggle(circBtn, ShapeType.CIRCLE);
        hookShapeToggle(squareBtn, ShapeType.SQUARE);

        shapeGroup.add(triBtn);
        shapeGroup.add(circBtn);
        shapeGroup.add(squareBtn);

        JPanel shapeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        shapeRow.add(triBtn);
        shapeRow.add(circBtn);
        shapeRow.add(squareBtn);
        shapeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, shapeRow.getPreferredSize().height));
        add(shapeRow);

        // --- Brush Style row ---
        add(Box.createVerticalStrut(18));
        JPanel brushRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        brushBtn  = strokeToggle(loadScaledIcon("/images/spikeStroke.png", 28, 28), "Basic brush");
        waterBtn  = strokeToggle(loadScaledIcon("/images/waterStroke.png", 28, 28), "Watercolor");
        oilBtn    = strokeToggle(loadScaledIcon("/images/oilStroke.png",   28, 28), "Oil paint");

        brushGroup = new ButtonGroup();
        brushGroup.add(brushBtn);
        brushGroup.add(waterBtn);
        brushGroup.add(oilBtn);

        hookBrushToggle(brushBtn, BrushStyle.BASIC);
        hookBrushToggle(waterBtn, BrushStyle.WATERCOLOR);
        hookBrushToggle(oilBtn,   BrushStyle.OIL);

        brushRow.add(brushBtn);
        brushRow.add(waterBtn);
        brushRow.add(oilBtn);
        brushRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, brushRow.getPreferredSize().height));
        add(brushRow);

        // --- Undo / Redo row (wired to canvas) ---
        add(Box.createVerticalStrut(20));
        JPanel historyRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        JButton undoBtn = makeGrayIconButton(loadScaledIcon("/images/undo.png", 28, 28));
        undoBtn.setToolTipText("Undo");
        undoBtn.addActionListener(e -> canvas.undo());
        JButton redoBtn = makeGrayIconButton(loadScaledIcon("/images/redo.png", 28, 28));
        redoBtn.setToolTipText("Redo");
        redoBtn.addActionListener(e -> canvas.redo());
        historyRow.add(undoBtn);
        historyRow.add(redoBtn);
        historyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, historyRow.getPreferredSize().height));
        add(historyRow);

        updateShapeIcons();   // initial paint
        updateBrushBorders(); // initial borders
    }

    // ===================== behavior helpers =====================

    private void hookShapeToggle(JToggleButton btn, ShapeType type) {
        // enable "deselect on second click"
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                btn.putClientProperty("toggleOff", btn.isSelected());
            }
        });

        btn.addActionListener(e -> {
            boolean toggleOff = Boolean.TRUE.equals(btn.getClientProperty("toggleOff"));
            btn.putClientProperty("toggleOff", Boolean.FALSE);

            if (toggleOff) {
                shapeGroup.clearSelection();
                updateShapeIcons();
                if (!model.isEraserMode()) {
                    model.setCurrentShapeType(ShapeType.FREEHAND);
                }
                updateBrushBorders();
                return;
            }

            // Selecting a shape cancels eraser & clears brush selection (mutually exclusive)
            if (model.isEraserMode()) {
                setEraserOffVisuals();
                model.setEraserMode(false);
            }
            clearBrushSelections(); // lightens brush borders
            model.setCurrentShapeType(type);
            model.setShapeFill(true); // filled shapes by default
            updateShapeIcons();
            updateBrushBorders();
        });
    }

    private void hookBrushToggle(JToggleButton btn, BrushStyle style) {
        // enable "deselect on second click"
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                btn.putClientProperty("toggleOff", btn.isSelected());
            }
        });

        btn.addActionListener(e -> {
            boolean toggleOff = Boolean.TRUE.equals(btn.getClientProperty("toggleOff"));
            btn.putClientProperty("toggleOff", Boolean.FALSE);

            if (toggleOff) {
                clearBrushSelections();      // visually unselect all
                model.setBrushStyle(null);   // indicate "no special brush" (plain freehand)
                if (!model.isEraserMode()) model.setCurrentShapeType(ShapeType.FREEHAND);
                return;
            }

            // Selecting a brush cancels shapes + eraser
            if (shapeGroup != null) {
                shapeGroup.clearSelection();
                updateShapeIcons();
            }
            if (model.isEraserMode()) {
                setEraserOffVisuals();
                model.setEraserMode(false);
            }

            brushGroup.setSelected(btn.getModel(), true);
            model.setBrushStyle(style);
            model.setCurrentShapeType(ShapeType.FREEHAND);
            updateBrushBorders();
        });
    }

    private void clearBrushSelections() {
        if (brushGroup != null) brushGroup.clearSelection();
        if (brushBtn  != null) brushBtn.setSelected(false);
        if (waterBtn  != null) waterBtn.setSelected(false);
        if (oilBtn    != null) oilBtn.setSelected(false);
        updateBrushBorders();
    }

    /** Darker border when a brush is selected; lighter when not. */
    private void updateBrushBorders() {
        setBrushBorder(brushBtn,  brushBtn != null && brushBtn.isSelected());
        setBrushBorder(waterBtn,  waterBtn != null && waterBtn.isSelected());
        setBrushBorder(oilBtn,    oilBtn != null && oilBtn.isSelected());
    }

    private void setBrushBorder(AbstractButton b, boolean selected) {
        if (b == null) return;
        Color line = selected ? BRUSH_BORDER_ON : BRUSH_BORDER_OFF;
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(line, 2, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        b.repaint();
    }

    /** Null-safe: returns false until shape buttons are created. */
    private boolean anyShapeSelected() {
        if (triBtn == null || circBtn == null || squareBtn == null) return false;
        return triBtn.isSelected() || circBtn.isSelected() || squareBtn.isSelected();
    }

    private void setEraserOffVisuals() {
        eraserBtn.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1, true));
        canvas.setEraserPosition(null);
        canvas.repaint();
    }

    private void updateShapeIcons() {
        if (triBtn != null) ((ShapeIcon) triBtn.getIcon()).setSelected(triBtn.isSelected());
        if (circBtn != null) ((ShapeIcon) circBtn.getIcon()).setSelected(circBtn.isSelected());
        if (squareBtn != null) ((ShapeIcon) squareBtn.getIcon()).setSelected(squareBtn.isSelected());
        if (triBtn != null) triBtn.repaint();
        if (circBtn != null) circBtn.repaint();
        if (squareBtn != null) squareBtn.repaint();
    }

    private JToggleButton shapeToggle(ShapeIcon icon) {
        JToggleButton b = new JToggleButton(icon);
        b.setFocusable(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        return b;
    }

    /** Pill-ish toggle used for brush style buttons. */
    private JToggleButton strokeToggle(Icon icon, String tooltip) {
        JToggleButton b = new JToggleButton(icon);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setContentAreaFilled(true);
        b.setBackground(Color.WHITE);
        // start with "off" border; selection will darken it
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BRUSH_BORDER_OFF, 2, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        return b;
    }

    // ===================== utility & UI bits =====================

    private ImageIcon loadScaledIcon(String resourcePath, int w, int h) {
        java.net.URL url = getClass().getResource(resourcePath);
        if (url == null) {
            Image empty = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            return new ImageIcon(empty);
        }
        Image img = new ImageIcon(url).getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    private JButton makeGrayIconButton(Icon icon) {
        JButton btn = new JButton(icon);
        btn.setFocusable(false);
        btn.setBackground(Color.LIGHT_GRAY);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        return btn;
    }

    private JComponent buildColourGrid() {
        JPanel grid = new JPanel(new GridLayout(2, 6, 4, 8));
        for (Color c : PALETTE) {
            ColorIcon ci = new ColorIcon(c);
            ci.addPropertyChangeListener("colorChosen", evt -> selectColour((Color) evt.getNewValue()));
            grid.add(ci);
            iconByColor.put(c, ci);
        }
        selectColour(model.getCurrentColor()); // highlight default
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    private void selectColour(Color c) {
        model.setCurrentColor(c);
        iconByColor.values().forEach(ic -> ic.setSelected(false));
        if (iconByColor.containsKey(c)) iconByColor.get(c).setSelected(true);

        // Exit shape/brush/eraser – go back to FREEHAND with no brush selected.
        if (shapeGroup != null) {
            shapeGroup.clearSelection();
            updateShapeIcons();
        }
        clearBrushSelections();
        model.setBrushStyle(null); // plain freehand if your model supports null here
        if (model.isEraserMode()) {
            model.setEraserMode(false);
            setEraserOffVisuals();
        }
        model.setCurrentShapeType(ShapeType.FREEHAND);
    }

    private void toggleEraser() {
        boolean on = !model.isEraserMode();
        model.setEraserMode(on);

        if (on) {
            if (shapeGroup != null) {
                shapeGroup.clearSelection();
                updateShapeIcons();
            }
            clearBrushSelections();
            model.setCurrentShapeType(ShapeType.FREEHAND);
            eraserBtn.setBorder(BorderFactory.createLineBorder(SELECTED_OUTLINE, 2, true));
        } else {
            setEraserOffVisuals();
            if (!anyShapeSelected()) {
                model.setCurrentShapeType(ShapeType.FREEHAND);
            }
        }
    }

    // ----------------- inner class for shape icons -----------------

    /** Icon that draws a shape. When selected, the shape outline is rendered in blue. */
    private static class ShapeIcon implements Icon {
        private final ShapeType type;
        private final int w, h;
        private boolean selected;

        ShapeIcon(ShapeType type, int w, int h) {
            this.type = type;
            this.w = w;
            this.h = h;
        }

        void setSelected(boolean sel) { this.selected = sel; }

        @Override public int getIconWidth()  { return w; }
        @Override public int getIconHeight() { return h; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);

                int pad = 4;
                int iw = w - 2*pad;
                int ih = h - 2*pad;
                Color stroke = selected ? SELECTED_OUTLINE : NORMAL_OUTLINE;

                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                switch (type) {
                    case CIRCLE -> g2.drawOval(pad, pad, iw, ih);
                    case SQUARE -> g2.drawRect(pad, pad, iw, ih);
                    case TRIANGLE -> {
                        Polygon p = new Polygon();
                        p.addPoint(pad + iw/2, pad);
                        p.addPoint(pad, pad + ih);
                        p.addPoint(pad + iw, pad + ih);
                        g2.drawPolygon(p);
                    }
                    default -> { /* FREEHAND not displayed */ }
                }
            } finally {
                g2.dispose();
            }
        }
    }
}