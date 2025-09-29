package com.artwork.mvc.view;

import com.artwork.mvc.model.DrawingModel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * A slim toolbar that spans the full width at the top.
 *
 * Left:  [projectLogo.png]  "FlexiDraw" (gradient text)
 * Right: [Upload ▾ (split, no gap with seam)]  [Download]  [■(dark-mode toggle)]
 *
 * - Upload (left): opens image picker and replaces canvas background
 * - Upload ▾ menu: Remove background, Background color…
 * - Download: saves current canvas as PNG
 * - Dark-mode toggle: no text, white when off, black when on
 *
 * NOTE: When toggling dark mode we also ask the canvas to invert its
 * bitmap layers (shapes & special brushes) so they complement too.
 */
public class TopBarPanel extends JPanel {

    private final DrawingModel model;
    private final DrawingPanel canvas;

    public TopBarPanel(DrawingModel model, DrawingPanel canvas) {
        this.model = model;
        this.canvas = canvas;

        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        setBackground(new Color(245, 245, 247)); // subtle header feel

        // ---- Left side: logo + app name ----
        JLabel logo = createLogoLabel();
        logo.setAlignmentY(Component.CENTER_ALIGNMENT);
        add(logo);
        add(Box.createHorizontalStrut(10));

        GradientLabel appName = new GradientLabel("FlexiDraw");
        appName.setAlignmentY(Component.CENTER_ALIGNMENT);
        add(appName);

        // Spacer pushes the controls to the right
        add(Box.createHorizontalGlue());

        // ---- Right side: Upload (split), Download, Dark toggle ----
        JComponent uploadSplit = createUploadSplitButton();
        uploadSplit.setAlignmentY(Component.CENTER_ALIGNMENT);
        add(uploadSplit);
        add(Box.createHorizontalStrut(8));

        JButton downloadBtn = createDownloadButton();
        downloadBtn.setAlignmentY(Component.CENTER_ALIGNMENT);
        add(downloadBtn);
        add(Box.createHorizontalStrut(8));

        JToggleButton darkBtn = createDarkModeToggleButton();
        darkBtn.setAlignmentY(Component.CENTER_ALIGNMENT);
        add(darkBtn);
    }

    @Override
    public Dimension getPreferredSize() {
        // Fixed slim height; width is managed by frame.
        return new Dimension(super.getPreferredSize().width, 44);
    }

    // ======= Left: Logo + App Name =======

    private JLabel createLogoLabel() {
        Icon icon = loadIcon("/images/projectLogo.png", 24, 24);
        JLabel label = new JLabel(icon);
        if (icon == null) {
            label.setText(" ");
            label.setBorder(BorderFactory.createLineBorder(new Color(200,200,200), 1, true));
            label.setPreferredSize(new Dimension(24,24));
            label.setMinimumSize(new Dimension(24,24));
        }
        return label;
    }

    /** Gradient app label (Teal → Indigo — your choice #4). */
    private static class GradientLabel extends JComponent {
        private final String text;
        private final Font font = new Font("SansSerif", Font.BOLD, 18);
        private final Color c1 = new Color(0x00B3A4);
        private final Color c2 = new Color(0x4C5BD4);

        GradientLabel(String text) { this.text = text; }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font);
            return new Dimension(fm.stringWidth(text) + 6, fm.getHeight() + 2);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(font);

                FontMetrics fm = g2.getFontMetrics();
                int textW = fm.stringWidth(text);
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

                Paint p = new GradientPaint(0, 0, c1, Math.max(1, textW), 0, c2, true);
                g2.setPaint(p);
                g2.drawString(text, 0, y);
            } finally {
                g2.dispose();
            }
        }
    }

    // ======= Right: Controls =======

    /** Upload as a split button: left = action, right chevron opens a menu. No gap; thin seam visible. */
    private JComponent createUploadSplitButton() {
        final Color line = new Color(185, 185, 185);

        // Left portion: Upload action
        Icon uploadIcon = loadIcon("/images/uploadIcon.png", 18, 18);
        JButton main = new JButton("Upload", uploadIcon);
        main.setFocusable(false);
        main.setBackground(Color.WHITE);
        main.setOpaque(true);
        // 2px outer borders, 1px on the right to act as the seam
        main.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.MatteBorder(2, 2, 2, 1, line), // top,left,bottom,right
                BorderFactory.createEmptyBorder(6, 12, 6, 10)
        ));
        main.addActionListener(e -> canvas.promptAndReplaceBackgroundImage());

        // Right portion: chevron ▾ that opens the menu (no gap to the left)
        JButton arrow = new JButton("\u25BE"); // ▾
        arrow.setFocusable(false);
        arrow.setBackground(Color.WHITE);
        arrow.setOpaque(true);
        arrow.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.MatteBorder(2, 0, 2, 2, line), // left=0 so seam is 1px total
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        // Menu content (no "Upload image…" here to avoid duplication)
        final JPopupMenu menu = new JPopupMenu();

        JMenuItem miRemove = new JMenuItem("Remove background");
        miRemove.addActionListener(e -> {
            canvas.clearBackgroundImage();
            Color bg = model.isDarkMode() ? new Color(0x1F1F1F) : Color.WHITE;
            canvas.setBackground(bg);
            canvas.repaint();
        });
        menu.add(miRemove);

        JMenuItem miColor = new JMenuItem("Background color");
        miColor.addActionListener(e -> {
            Color start = canvas.getBackground();
            Color chosen = JColorChooser.showDialog(this, "Pick background color", start);
            if (chosen != null) {
                canvas.clearBackgroundImage();
                canvas.setBackground(chosen);
                canvas.repaint();
            }
        });
        menu.add(miColor);

        arrow.addActionListener(e -> menu.show(arrow, 0, arrow.getHeight()));
        // Convenience: right-click on main shows the menu
        main.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    menu.show(main, e.getX(), main.getHeight());
                }
            }
        });

        // Combine into a single-looking split control with *no spacer* between children
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.X_AXIS));
        wrap.setOpaque(false);
        wrap.add(main);
        wrap.add(arrow);
        return wrap;
    }

    private JButton createDownloadButton() {
        Icon icon = loadIcon("/images/downloadIcon.png", 18, 18);
        JButton b = new JButton("Download", icon);
        stylePillButton(b);
        b.addActionListener(e -> saveCanvasPng());
        return b;
    }

    /** Small, textless toggle: white when off, black when on. Also flips raster layers. */
    private JToggleButton createDarkModeToggleButton() {
        JToggleButton t = new JToggleButton();
        t.setToolTipText("Toggle Dark Mode");
        t.setFocusable(false);
        t.setOpaque(true);
        t.setContentAreaFilled(true);
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(185, 185, 185), 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        // Compact size similar to the old "..." button.
        t.setPreferredSize(new Dimension(40, 28));
        t.setMinimumSize(new Dimension(40, 28));
        t.setMaximumSize(new Dimension(40, 28));

        // Initial sync
        t.setSelected(model.isDarkMode());
        t.setBackground(model.isDarkMode() ? Color.BLACK : Color.WHITE);

        t.addActionListener(e -> {
            model.toggleDarkMode();                                 // complements vector lines + currentColor
            boolean on = model.isDarkMode();
            t.setBackground(on ? Color.BLACK : Color.WHITE);
            canvas.setBackground(on ? new Color(0x1F1F1F) : Color.WHITE);
            canvas.flipCompositeLayersForDarkMode();                // <-- NEW: invert shapes & special brushes
            canvas.repaint();
        });

        return t;
    }

    // ======= Shared helpers =======

    /** Light gray outline to match your style; shared by Download. */
    private void stylePillButton(JButton b) {
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setIconTextGap(8);
        b.setFocusable(false);
        b.setBackground(Color.WHITE);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(185, 185, 185), 2, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
    }

    private Icon loadIcon(String path, int w, int h) {
        java.net.URL url = getClass().getResource(path);
        if (url == null) return null;
        Image img = new ImageIcon(url).getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    // ======= Saving helper =======

    /** Renders the canvas to an ARGB image and saves it as PNG via a Save dialog. */
    private void saveCanvasPng() {
        int w = Math.max(1, canvas.getWidth());
        int h = Math.max(1, canvas.getHeight());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            canvas.paint(g2); // snapshot exactly what is drawn (background, shapes, lines)
        } finally {
            g2.dispose();
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save canvas as PNG");
        chooser.setAcceptAllFileFilterUsed(false);
        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter("PNG Image (*.png)", "png");
        chooser.addChoosableFileFilter(pngFilter);
        chooser.setFileFilter(pngFilter);
        chooser.setSelectedFile(new File("canvas.png"));

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getParentFile(), file.getName() + ".png");
        }

        try {
            ImageIO.write(img, "png", file);
            JOptionPane.showMessageDialog(this, "Saved to:\n" + file.getAbsolutePath(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save PNG:\n" + ex.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}