package com.artwork.mvc.view;

import com.artwork.mvc.model.DrawingModel;

import javax.swing.*;
import java.awt.*;

public class ControlPanel extends JPanel {
    private static final Color[] COLORS = {
            Color.BLACK, Color.BLUE, Color.RED, Color.GREEN,
            Color.ORANGE, Color.MAGENTA, Color.CYAN, Color.GRAY,
            Color.PINK, Color.YELLOW, Color.DARK_GRAY, Color.LIGHT_GRAY
    };

    public ControlPanel(DrawingModel model) {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        for (Color color : COLORS) {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(20, 20));
            btn.setBackground(color);
            btn.addActionListener(e -> model.setCurrentColor(color));
            add(btn);
        }
        JSlider slider = new JSlider(1, 10, model.getStrokeWidth());
        slider.addChangeListener(e -> model.setStrokeWidth(slider.getValue()));
        add(new JLabel("Thickness:"));
        add(slider);
    }
}
