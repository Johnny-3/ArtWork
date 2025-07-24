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

        ControlPanel controlPanel = new ControlPanel(model, drawingPanel);
        ReviewPanel reviewPanel = new ReviewPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, drawingPanel);
        splitPane.setResizeWeight(0);
        splitPane.setDividerLocation(150);

        getContentPane().setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        add(reviewPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }
}
