package com.artwork.mvc.view;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class AboutProgrammerDialog extends JDialog {

    public AboutProgrammerDialog(Window owner) {
        super(owner, "About Programmer", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        // Left: image (programmer.png)
        JLabel photo = new JLabel(loadScaledIcon("/images/programmer.png", 96, 96));
        photo.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));
        root.add(photo, BorderLayout.WEST);

        // Right: details
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(label("Name: ", "Parin Sharma"));
        info.add(Box.createVerticalStrut(6));
        info.add(label("School: ", "NUS High School"));
        info.add(Box.createVerticalStrut(6));
        info.add(label("Email: ", "h2310098@nushigh.edu.sg"));
        info.add(Box.createVerticalStrut(6));
        info.add(label("Favorite Brawler: ", "Cordelius"));
        root.add(info, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.add(close);
        root.add(south, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel label(String k, String v) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel key = new JLabel(k);
        key.setFont(key.getFont().deriveFont(Font.BOLD));
        JLabel val = new JLabel(" " + v);
        p.add(key);
        p.add(val);
        return p;
    }

    private ImageIcon loadScaledIcon(String resourcePath, int w, int h) {
        java.net.URL url = getClass().getResource(resourcePath);
        if (url == null) {
            Image empty = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            return new ImageIcon(empty);
        }
        Image img = new ImageIcon(url).getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }
}