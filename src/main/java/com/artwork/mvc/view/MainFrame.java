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
        reviewPanel.bindCanvas(drawingPanel);     // <-- bind canvas so ReviewPanel can snapshot

        // Top bar with Upload / Download / Dark toggle
        TopBarPanel topBar = new TopBarPanel(model, drawingPanel);

        JSplitPane splitPane =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, drawingPanel);
        splitPane.setResizeWeight(0);          // keep control panel fixed
        splitPane.setDividerLocation(0.20);    // roughly 20% controls, 80% canvas

        getContentPane().setLayout(new BorderLayout());
        add(topBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(reviewPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }
}