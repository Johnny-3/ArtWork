package com.artwork.mvc.controller;

import com.artwork.mvc.model.DrawingModel;
import com.artwork.mvc.model.Line;
import com.artwork.mvc.view.DrawingPanel;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class DrawingController {
    private final DrawingModel model;
    private final DrawingPanel view;
    private Point lastPoint;

    public DrawingController(DrawingModel model, DrawingPanel view) {
        this.model = model;
        this.view = view;
        attachListeners();
    }

    private void attachListeners() {
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (model.isEraserMode()) {
                    model.eraseAt(e.getPoint());
                    view.setEraserPosition(e.getPoint());
                    view.repaint();
                } else {
                    lastPoint = e.getPoint();
                }
            }
        });

        view.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (model.isEraserMode()) {
                    model.eraseAt(e.getPoint());
                    view.setEraserPosition(e.getPoint());
                    view.repaint();
                } else {
                    Point newPoint = e.getPoint();
                    Line line = new Line(lastPoint, newPoint, model.getCurrentColor(), model.getStrokeWidth());
                    model.addLine(line);
                    lastPoint = newPoint;
                    view.repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (model.isEraserMode()) {
                    view.setEraserPosition(e.getPoint());
                    view.repaint();
                }
            }
        });
    }
}
