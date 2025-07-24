package com.artwork.mvc.view;

import javax.swing.*;
import java.awt.*;

/**
 * Simple square icon painted with the given color.
 */
public class ColorIcon implements Icon {
    private final Color color;
    private final int size;

    public ColorIcon(Color color, int size) {
        this.color = color;
        this.size = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(color);
        g.fillRect(x, y, size, size);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, size - 1, size - 1);
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
