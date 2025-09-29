package com.artwork.mvc.view;

import com.artwork.mvc.review.ReviewServiceLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bottom bar with centered "Review" button and bottom-left profile icon.
 * When "Review" is pressed, we snapshot the canvas and POST it (as PNG)
 * to the AI review service. The service returns HTML we show in a dialog.
 *
 * Provide the live canvas via {@link #bindCanvas(DrawingPanel)} in MainFrame.
 *
 * Configure endpoint with:
 *   -Dflexidraw.review.url=http://127.0.0.1:8000/review
 * or env var:
 *   FLEXIDRAW_REVIEW_URL=http://127.0.0.1:8000/review
 * Defaults to http://127.0.0.1:8000/review
 */
public class ReviewPanel extends JPanel {

    // replace the static REVIEW_ENDPOINT field with:
    private static String defaultEndpoint() {
        String host = System.getProperty(
                "flexidraw.review.host",
                System.getenv().getOrDefault("FLEXIDRAW_REVIEW_HOST", "127.0.0.1"));
        String port = System.getProperty(
                "flexidraw.review.port",
                System.getenv().getOrDefault("FLEXIDRAW_REVIEW_PORT", "8000"));
        return "http://" + host + ":" + port + "/review";
    }

    private static final String REVIEW_ENDPOINT =
            System.getProperty(
                    "flexidraw.review.url",
                    System.getenv().getOrDefault("FLEXIDRAW_REVIEW_URL", defaultEndpoint())
            );

    private DrawingPanel canvas;     // injected from MainFrame
    private final JButton reviewButton;

    public ReviewPanel() {
        setPreferredSize(new Dimension(100, 120));
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();

        // Center "Review" button
        reviewButton = new JButton("Review");
        reviewButton.addActionListener(this::onReview);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        add(reviewButton, gbc);

        // Bottom-left profile icon (clickable)
        JLabel profile = new JLabel(loadScaledIcon("/images/profilePhoto.png", 28, 28));
        profile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        profile.setToolTipText("About Programmer");
        profile.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(ReviewPanel.this);
                new AboutProgrammerDialog(w).setVisible(true);
            }
        });

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;                  // bottom row
        gbc.weightx = 0.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.SOUTHWEST;
        gbc.insets = new Insets(0, 16, 16, 0); // near edges
        add(profile, gbc);
    }

    /** Called by MainFrame to provide the live canvas for snapshotting. */
    public void bindCanvas(DrawingPanel canvas) {
        this.canvas = canvas;
    }

    // ---------- Action ----------

    private void onReview(ActionEvent e) {
        if (canvas == null) {
            JOptionPane.showMessageDialog(this, "Canvas not ready.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        reviewButton.setEnabled(false);
        final JDialog progress = createProgressDialog();

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                // Ensure the local FastAPI review service is running (idempotent).
                try {
                    ReviewServiceLauncher.ensureRunning();
                } catch (Throwable t) {
                    // Don't fail immediately; surface in UI below.
                    throw new IOException("Could not start the review service: " + t.getMessage(), t);
                }

                // Snapshot canvas to PNG
                BufferedImage img = canvas.renderCanvasToImage();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", os);
                byte[] png = os.toByteArray();

                // Send to review service (multipart/form-data)
                try {
                    return postPngForHtml(png, "canvas.png");
                } catch (ConnectException ce) {
                    throw new IOException(
                            "Could not connect to review service at " + REVIEW_ENDPOINT
                                    + ". Is it blocked by a firewall or failed to start?", ce);
                }
            }

            @Override
            protected void done() {
                reviewButton.setEnabled(true);
                progress.dispose();

                String html;
                try {
                    html = get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ReviewPanel.this,
                            "Review failed:\n" + ex.getMessage(),
                            "AI Review", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (html == null || html.isBlank()) {
                    JOptionPane.showMessageDialog(ReviewPanel.this,
                            "No review returned.",
                            "AI Review", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Show HTML result
                JEditorPane pane = new JEditorPane("text/html", html);
                pane.setEditable(false);
                pane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
                JScrollPane scroll = new JScrollPane(pane);
                scroll.setPreferredSize(new Dimension(620, 420));
                JOptionPane.showMessageDialog(ReviewPanel.this, scroll,
                        "AI Review", JOptionPane.PLAIN_MESSAGE);
            }
        }.execute();

        progress.setVisible(true); // blocks until disposed
    }

    // ---------- HTTP helper ----------

    private String postPngForHtml(byte[] png, String filename) throws IOException, InterruptedException {
        String boundary = "----FlexiDrawBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, "image", filename, "image/png", png);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(REVIEW_ENDPOINT))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "FlexiDraw/1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Service error " + resp.statusCode() + ": " + resp.body());
        }

        // The service returns JSON with an "html" field.
        String json = resp.body();
        String html = extractJsonField(json, "html");
        return html != null ? html : json;
    }

    private static byte[] buildMultipartBody(
            String boundary, String fieldName, String filename,
            String contentType, byte[] content) {

        String headStr = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        byte[] head = headStr.getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] all = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(content, 0, all, head.length, content.length);
        System.arraycopy(tail, 0, all, head.length + content.length, tail.length);
        return all;
    }

    /** Very small, dependency-free extraction of a JSON string field. */
    private static String extractJsonField(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int j = firstQuote + 1; j < json.length(); j++) {
            char c = json.charAt(j);
            if (esc) { sb.append(c); esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

    private JDialog createProgressDialog() {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "Reviewing…", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        p.add(new JLabel("Analyzing your artwork with AI…"), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        p.add(bar, BorderLayout.CENTER);
        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(this);
        return d;
    }

    // ---------- icons ----------

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