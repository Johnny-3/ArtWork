package com.artwork.mvc.view;

import com.artwork.mvc.model.DrawingModel;
import com.artwork.mvc.model.ShapeType;
import com.artwork.mvc.view.DrawingPanel;

import javax.swing.*;
import java.awt.*;

public class ControlPanel extends JPanel {
    private static final ShapeType[] SHAPES = { ShapeType.FREEHAND, ShapeType.RECTANGLE, ShapeType.OVAL };
    private final JButton[] shapeButtons = new JButton[SHAPES.length];

    private final JButton colorButton;
    private final DrawingModel model;

    public ControlPanel(DrawingModel model, DrawingPanel drawingPanel) {
        this.model = model;
        setLayout(new FlowLayout(FlowLayout.LEFT));

        colorButton = new JButton();
        colorButton.setPreferredSize(new Dimension(20, 20));
        colorButton.setOpaque(true);
        colorButton.setBorderPainted(false);
        colorButton.setFocusPainted(false);
        colorButton.setBackground(model.getCurrentColor());
        colorButton.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Choose Color", model.getCurrentColor());
            if (chosen != null) {
                model.setCurrentColor(chosen);
                colorButton.setBackground(chosen);
            }
        });
        add(colorButton);

        // shape selection buttons
        for (int i = 0; i < SHAPES.length; i++) {
            ShapeType type = SHAPES[i];
            JButton sbtn = new JButton(type.name().substring(0, 1));
            sbtn.setFocusPainted(false);
            sbtn.putClientProperty("shape", type);
            sbtn.addActionListener(e -> setCurrentShapeType((ShapeType) ((JButton) e.getSource()).getClientProperty("shape")));
            shapeButtons[i] = sbtn;
            add(sbtn);
        }

        JButton eraser = new JButton("E");
        eraser.setFocusPainted(false);
        eraser.addActionListener(e -> {
            boolean enable = !model.isEraserMode();
            model.setEraserMode(enable);
            if (!enable) {
                drawingPanel.setEraserPosition(null);
                drawingPanel.repaint();
            }
        });
        add(eraser);

        JSlider eraserSlider = new JSlider(5, 50, model.getEraserRadius());
        eraserSlider.addChangeListener(e -> model.setEraserRadius(eraserSlider.getValue()));
        add(new JLabel("Erase Radius:"));
        add(eraserSlider);

        JSlider slider = new JSlider(1, 10, model.getStrokeWidth());
        slider.addChangeListener(e -> model.setStrokeWidth(slider.getValue()));
        add(new JLabel("Thickness:"));
        add(slider);

        JCheckBox dark = new JCheckBox("Dark");
        dark.addActionListener(e -> {
            model.toggleDarkMode();
            updateColorButton();
            drawingPanel.setBackground(model.isDarkMode() ? Color.BLACK : Color.WHITE);
            drawingPanel.repaint();
        });
        add(dark);

        updateColorButton();
        updateShapeButtonColors();
    }

    private void updateColorButton() {
        colorButton.setBackground(model.getCurrentColor());
    }

    private void setCurrentShapeType(ShapeType type) {
        model.setCurrentShapeType(type);
        updateShapeButtonColors();
    }

    private void updateShapeButtonColors() {
        for (int i = 0; i < SHAPES.length; i++) {
            ShapeType t = SHAPES[i];
            JButton btn = shapeButtons[i];
            btn.setBackground(t == model.getCurrentShapeType() ? Color.LIGHT_GRAY : null);
        }
    }
}
