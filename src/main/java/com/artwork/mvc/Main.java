package com.artwork.mvc;

import com.artwork.mvc.view.MainFrame;
import com.artwork.mvc.view.SplashScreen;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // (Optional) Use the system look & feel for nicer controls
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {}

        SwingUtilities.invokeLater(() ->
                SplashScreen.showSplashThen(() -> {
                    MainFrame frame = new MainFrame();
                    frame.setVisible(true);
                })
        );
    }
}