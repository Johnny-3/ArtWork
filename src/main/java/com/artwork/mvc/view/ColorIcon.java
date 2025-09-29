    package com.artwork.mvc.view;

    import javax.swing.*;
    import java.awt.*;
    import java.awt.geom.Ellipse2D;

    public class ColorIcon extends JComponent {
        private final Color color;
        private boolean selected;

        public ColorIcon(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(32, 32));
            setOpaque(false);

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) {
                    firePropertyChange("colorChosen", null, color);
                }
            });
        }

        public void setSelected(boolean sel) {
            selected = sel;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int margin = 3;
            int d = Math.min(getWidth(), getHeight()) - margin * 2;
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(margin, margin, d, d));

            if (selected) {
                g2.setStroke(new BasicStroke(3));
                g2.setColor(Color.WHITE);
                g2.draw(new Ellipse2D.Double(margin - 2, margin - 2, d + 4, d + 4));
            }
            g2.dispose();
        }
    }