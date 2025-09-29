package com.artwork.mvc.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ColorWheel extends JComponent {
    private Color selectedColor = Color.BLACK;

    public ColorWheel() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Color c = getColorAtPoint(e.getX(), e.getY());
                if (c != null) {
                    Color old = selectedColor;
                    selectedColor = c;
                    firePropertyChange("selectedColor", old, selectedColor);
                    repaint();
                }
            }
        });
    }

    public Color getSelectedColor() {
        return selectedColor;
    }

    private Color getColorAtPoint(int x, int y) {
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int dx = x - cx;
        int dy = y - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        int r = Math.min(cx, cy);
        if (dist > r) return null;
        float hue = (float) (Math.atan2(dy, dx) / (2 * Math.PI));
        if (hue < 0) hue += 1f;
        float sat = (float) (dist / r);
        return Color.getHSBColor(hue, sat, 1f);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int r = Math.min(getWidth(), getHeight()) / 2;
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                double dist = Math.sqrt(x * x + y * y);
                if (dist <= r) {
                    float hue = (float) (Math.atan2(y, x) / (2 * Math.PI));
                    if (hue < 0) hue += 1f;
                    float sat = (float) (dist / r);
                    g.setColor(Color.getHSBColor(hue, sat, 1f));
                    g.drawLine(cx + x, cy + y, cx + x, cy + y);
                }
            }
        }
        // draw selector
        g.setColor(Color.BLACK);
        int sx = cx + (int) (Math.cos(selectedHue()) * selectedSat() * r);
        int sy = cy + (int) (Math.sin(selectedHue()) * selectedSat() * r);
        g.drawOval(sx - 3, sy - 3, 6, 6);
    }

    private double selectedHue() {
        float[] hsb = Color.RGBtoHSB(selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), null);
        return hsb[0] * 2 * Math.PI;
    }

    private float selectedSat() {
        float[] hsb = Color.RGBtoHSB(selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue(), null);
        return hsb[1];
    }
}
