package com.artwork.mvc.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * FlexiDraw splash screen:
 *  - Background logo drawn with a "cover" fit (fills window; no side margins)
 *  - Less translucent watermark
 *  - Rounded progress bar with darker gradient + moving thumb
 *  - "loading..." text
 *  - Starts animating after a 2s delay, then opens the app
 *
 * Title color: solid BLACK (as requested)
 */
public class SplashScreen extends JWindow {

    private static final int  WIDTH   = 640;
    private static final int  HEIGHT  = 400;
    private static final int  DELAY_MS = 2000;   // 2s before fill starts
    private static final int  FILL_MS  = 900;    // fill duration

    private final SplashCanvas canvas = new SplashCanvas();
    private double progress = 0.0; // 0..1

    private SplashScreen() {
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setBackground(new Color(0, 0, 0, 0));
        setContentPane(canvas);
    }

    /** Show the splash, then run {@code onDone} after animation completes. */
    public static void showSplashThen(Runnable onDone) {
        SplashScreen s = new SplashScreen();
        s.setVisible(true);

        new Timer(DELAY_MS, e -> {
            ((Timer) e.getSource()).stop();

            final int fps = 60;
            final int step = 1000 / fps;
            final int totalSteps = Math.max(1, FILL_MS / step);
            final double delta = 1.0 / totalSteps;

            Timer fill = new Timer(step, null);
            fill.addActionListener((ActionEvent ev) -> {
                s.progress = Math.min(1.0, s.progress + delta);
                s.canvas.setProgress(s.progress);
                if (s.progress >= 1.0) {
                    ((Timer) ev.getSource()).stop();
                    s.dispose();
                    if (onDone != null) onDone.run();
                }
            });
            fill.start();
        }).start();
    }

    // ----------------------------------------------------------------

    private static final class SplashCanvas extends JPanel {
        private BufferedImage logoOriginal;  // original logo
        private double progress = 0.0;

        SplashCanvas() {
            setOpaque(true);
            setBackground(new Color(245, 245, 247));

            // Load original (unscaled) logo once
            try {
                var url = getClass().getResource("/images/projectLogo.png");
                if (url != null) {
                    logoOriginal = ImageIO.read(url);
                }
            } catch (Exception ignored) {}
        }

        void setProgress(double p) {
            progress = Math.max(0, Math.min(1, p));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int w = getWidth(), h = getHeight();

            // Background base
            g2.setColor(new Color(245, 245, 247));
            g2.fillRect(0, 0, w, h);

            // Watermark logo (COVER fit + less translucent)
            if (logoOriginal != null) {
                int lw = logoOriginal.getWidth();
                int lh = logoOriginal.getHeight();
                if (lw > 0 && lh > 0) {
                    double scale = Math.max(w / (double) lw, h / (double) lh); // cover
                    int dw = (int) Math.ceil(lw * scale);
                    int dh = (int) Math.ceil(lh * scale);
                    int x = (w - dw) / 2;
                    int y = (h - dh) / 2;

                    Composite old = g2.getComposite();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f)); // translucent
                    g2.drawImage(logoOriginal, x, y, dw, dh, null);
                    g2.setComposite(old);
                }
            }

            // Title (now solid BLACK)
            String title = "FlexiDraw";
            Font titleFont = getFont().deriveFont(Font.BOLD, 42f);
            g2.setFont(titleFont);
            FontMetrics tfm = g2.getFontMetrics();
            int titleW = tfm.stringWidth(title);
            int titleX = (w - titleW) / 2;
            int titleY = (int) (h * 0.30);

            g2.setColor(Color.BLACK);
            g2.drawString(title, titleX, titleY);

            // Progress bar geometry
            int barW = Math.min(420, (int) (w * 0.82));
            int barH = 22;
            int arc  = barH; // rounded ends
            int bx = (w - barW) / 2;
            int by = (int) (h * 0.60);

            // Outline
            var outer = new RoundRectangle2D.Float(bx, by, barW, barH, arc, arc);
            g2.setColor(new Color(40, 40, 40));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(outer);

            // Inner fill area
            int inset = 3;
            int ix = bx + inset;
            int iy = by + inset;
            int iw = barW - inset * 2;
            int ih = barH - inset * 2;
            var inner = new RoundRectangle2D.Float(ix, iy, iw, ih, arc - inset, arc - inset);

            // Clip and fill (darker gradient)
            Shape oldClip = g2.getClip();
            g2.setClip(inner);
            Paint fillGrad = new GradientPaint(ix, 0, new Color(0xC8C8C8),
                    ix + iw, 0, new Color(0x4B4B4B), false);
            g2.setPaint(fillGrad);
            int filledW = (int) Math.round(iw * progress);
            g2.fillRect(ix, iy, filledW, ih);

            // Moving thumb
            int thumbD = ih;
            int thumbR = thumbD / 2;
            int thumbX = ix + Math.max(thumbR, Math.min(iw - thumbR, (int) Math.round(iw * progress))) - thumbR;
            int thumbY = iy;
            g2.setColor(new Color(0x2D2F33));
            g2.fill(new Ellipse2D.Float(thumbX, thumbY, thumbD, thumbD));

            g2.setClip(oldClip);

            // "loading..." text
            String loading = "loading...";
            Font loadFont = getFont().deriveFont(Font.PLAIN, 20f);
            g2.setFont(loadFont);
            FontMetrics lfm = g2.getFontMetrics();
            int lwText = lfm.stringWidth(loading);
            int lx = (w - lwText) / 2;
            int ly = by + barH + 36;
            g2.setColor(new Color(55, 55, 60));
            g2.drawString(loading, lx, ly);

            g2.dispose();
        }
    }
}