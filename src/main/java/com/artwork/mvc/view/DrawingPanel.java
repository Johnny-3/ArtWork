package com.artwork.mvc.view;

import com.artwork.mvc.model.DrawingModel;
import com.artwork.mvc.model.Line;

import javax.swing.*;
import java.awt.*;

public class DrawingPanel extends JPanel {
    private final DrawingModel model;

    public DrawingPanel(DrawingModel model) {
        this.model = model;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(400, 400));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        for (Line line : model.getLines()) {
            g2.setColor(line.getColor());
            g2.setStroke(new BasicStroke(line.getStrokeWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Point s = line.getStart();
            Point e = line.getEnd();
            g2.drawLine(s.x, s.y, e.x, e.y);
        }
    }
}
