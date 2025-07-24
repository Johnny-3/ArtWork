package com.artwork.mvc.view;

import javax.swing.*;
import java.awt.*;

public class ReviewPanel extends JPanel {
    public ReviewPanel() {
        setLayout(new FlowLayout(FlowLayout.CENTER));
        JButton reviewButton = new JButton("Review");
        add(reviewButton);
    }
}
