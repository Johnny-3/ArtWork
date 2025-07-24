package com.artwork.mvc.view;

import com.artwork.mvc.model.DrawingModel;
import com.artwork.mvc.view.DrawingPanel;
import com.artwork.mvc.view.ColorIcon;

import javax.swing.*;
import java.awt.*;

public class ControlPanel extends JPanel {
    private static final Color[] COLORS = {
            Color.BLACK, Color.BLUE, Color.RED, Color.GREEN,
            Color.ORANGE, Color.MAGENTA, Color.CYAN, Color.GRAY,
            Color.PINK, Color.YELLOW, Color.DARK_GRAY, Color.LIGHT_GRAY
    };

    private final JButton[] colorButtons = new JButton[COLORS.length];

    public ControlPanel(DrawingModel model, DrawingPanel drawingPanel) {
        setLayout(new FlowLayout(FlowLayout.LEFT));

        for (int i = 0; i < COLORS.length; i++) {
            Color color = COLORS[i];
            JButton btn = new JButton(new ColorIcon(color, 16));
            btn.setPreferredSize(new Dimension(20, 20));
            btn.setBackground(color);
            btn.setOpaque(true);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            final int idx = i;
            btn.addActionListener(e -> model.setCurrentColor(colorButtons[idx].getBackground()));
            colorButtons[i] = btn;
            add(btn);
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
            updateButtonColors(model);
            drawingPanel.setBackground(model.isDarkMode() ? Color.BLACK : Color.WHITE);
            drawingPanel.repaint();
        });
        add(dark);

        updateButtonColors(model);
    }

    private void updateButtonColors(DrawingModel model) {
        for (int i = 0; i < COLORS.length; i++) {
            Color base = COLORS[i];
            Color toUse = model.isDarkMode() ? DrawingModel.complementColor(base) : base;
            colorButtons[i].setBackground(toUse);
        }
    }
}
