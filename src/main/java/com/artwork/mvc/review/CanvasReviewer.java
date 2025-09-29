package com.artwork.mvc.review;

import com.artwork.mvc.view.DrawingPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class CanvasReviewer {

    public static void reviewCanvas(DrawingPanel canvas, Component parent) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            String html;
            Exception fail;

            @Override protected Void doInBackground() {
                try {
                    ReviewServiceLauncher.ensureRunning();

                    // Snapshot
                    BufferedImage img = canvas.renderCanvasToImage();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(img, "png", baos);
                    byte[] png = baos.toByteArray();

                    // Multipart
                    String boundary = "----FlexiDrawBoundary" + UUID.randomUUID();
                    var body = buildMultipart(png, boundary);

                    // POST
                    String host = System.getProperty("flexidraw.review.host",
                            System.getenv().getOrDefault("FLEXIDRAW_REVIEW_HOST", "127.0.0.1"));
                    String port = System.getProperty("flexidraw.review.port",
                            System.getenv().getOrDefault("FLEXIDRAW_REVIEW_PORT", "8000"));
                    String url = "http://" + host + ":" + port + "/review";

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build();

                    HttpClient client = HttpClient.newHttpClient();
                    HttpResponse<String> resp =
                            client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    if (resp.statusCode() != 200) {
                        throw new IOException("Review server returned " + resp.statusCode() + ": " + resp.body());
                    }
                    // very small JSON: {"html": "..."}
                    String b = resp.body();
                    int i = b.indexOf("\"html\"");
                    int s = b.indexOf(':', i) + 1;
                    int firstQuote = b.indexOf('"', s);
                    int lastQuote = b.lastIndexOf('"');
                    html = (firstQuote >= 0 && lastQuote > firstQuote)
                            ? b.substring(firstQuote + 1, lastQuote).replace("\\n", "\n").replace("\\\"", "\"")
                            : b; // fallback raw
                } catch (Exception e) {
                    fail = e;
                }
                return null;
            }

            @Override protected void done() {
                if (fail != null) {
                    JOptionPane.showMessageDialog(parent,
                            "Review failed:\n" + fail.getClass().getName() + ": " + fail.getMessage(),
                            "AI Review", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // Show HTML in a dialog
                JEditorPane ep = new JEditorPane("text/html", html == null ? "(no content)" : html);
                ep.setEditable(false);
                JScrollPane sp = new JScrollPane(ep);
                sp.setPreferredSize(new Dimension(520, 420));
                JOptionPane.showMessageDialog(parent, sp, "AI Review", JOptionPane.PLAIN_MESSAGE);
            }
        };
        worker.execute();
    }

    private static byte[] buildMultipart(byte[] png, String boundary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var NL = "\r\n".getBytes(StandardCharsets.UTF_8);

        out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8)); out.write(NL);
        out.write("Content-Disposition: form-data; name=\"image\"; filename=\"canvas.png\"".getBytes(StandardCharsets.UTF_8)); out.write(NL);
        out.write("Content-Type: image/png".getBytes(StandardCharsets.UTF_8)); out.write(NL);
        out.write(NL);
        out.write(png); out.write(NL);
        out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8)); out.write(NL);
        return out.toByteArray();
    }
}