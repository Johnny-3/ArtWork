package com.artwork.mvc.view;

import com.artwork.mvc.controller.DrawingController;
import com.artwork.mvc.model.DrawingModel;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    public MainFrame() {
        super("Art Work");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        DrawingModel model = new DrawingModel();
        DrawingPanel drawingPanel = new DrawingPanel(model);
        new DrawingController(model, drawingPanel);

        ControlPanel controlPanel = new ControlPanel(model);
        ReviewPanel reviewPanel = new ReviewPanel();

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(controlPanel, BorderLayout.WEST);
        topPanel.add(drawingPanel, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        add(topPanel, BorderLayout.CENTER);
        add(reviewPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }
}
