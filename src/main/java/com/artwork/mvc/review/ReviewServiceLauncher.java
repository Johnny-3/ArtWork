package com.artwork.mvc.review;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Spawns the review_service (FastAPI + Uvicorn) if it's not already running,
 * then polls /healthz until it's up.
 *
 * Config (env or -D system properties):
 *  FLEXIDRAW_REVIEW_HOST / -Dflexidraw.review.host   (default 127.0.0.1)
 *  FLEXIDRAW_REVIEW_PORT / -Dflexidraw.review.port   (default 8000)
 *  FLEXIDRAW_REVIEW_DIR  / -Dflexidraw.review.dir    (default: projectDir/review_service)
 *  FLEXIDRAW_PYTHON      / -Dflexidraw.python        (default: venv python if found, else python3/python)
 *  OPENAI_API_KEY        / -Dopenai.key              (forwarded into child env)
 */
public final class ReviewServiceLauncher {
    private static volatile Process proc;  // single instance

    private ReviewServiceLauncher() {}

    public static synchronized void ensureRunning() throws IOException {
        String host = sysOrEnv("flexidraw.review.host", "FLEXIDRAW_REVIEW_HOST", "127.0.0.1");
        int port = Integer.parseInt(sysOrEnv("flexidraw.review.port", "FLEXIDRAW_REVIEW_PORT", "8000"));
        String baseUrl = "http://" + host + ":" + port;

        // Already up?
        if (isHealthy(baseUrl, 1000)) return;

        // If we previously spawned and it died, clean it.
        if (proc != null && !proc.isAlive()) proc = null;

        if (proc == null) {
            // Working dir: …/review_service (default)
            File serviceDir = new File(sysOrEnv("flexidraw.review.dir", "FLEXIDRAW_REVIEW_DIR",
                    new File(System.getProperty("user.dir"), "review_service").getAbsolutePath()));

            if (!serviceDir.isDirectory()) {
                throw new IOException("review_service folder not found: " + serviceDir.getAbsolutePath());
            }

            // Pick python: prefer venv python
            String py = sysOrEnv("flexidraw.python", "FLEXIDRAW_PYTHON", null);
            if (py == null) {
                File venvUnix = new File(serviceDir, ".venv/bin/python");
                File venvWin  = new File(serviceDir, ".venv/Scripts/python.exe");
                if (venvUnix.isFile() && venvUnix.canExecute()) {
                    py = venvUnix.getAbsolutePath();
                } else if (venvWin.isFile() && venvWin.canExecute()) {
                    py = venvWin.getAbsolutePath();
                } else {
                    // fallbacks
                    py = isWindows() ? "python" : "python3";
                }
            }

            ProcessBuilder pb = new ProcessBuilder(
                    py, "-m", "uvicorn", "main:app",
                    "--host", host, "--port", String.valueOf(port)
            );
            pb.directory(serviceDir);
            // Inherit PATH etc; also forward OPENAI_API_KEY (from env or system property)
            pb.environment().putIfAbsent("OPENAI_API_KEY",
                    System.getProperty("openai.key",
                            System.getenv().getOrDefault("OPENAI_API_KEY", "")));
            // Optional: quiet OpenAI SDK warnings
            pb.environment().putIfAbsent("PYTHONUNBUFFERED", "1");

            // Start and pipe logs so you can see why it fails if it does.
            proc = pb.start();
            pipe(proc.getInputStream(), "[review_service] ");
            pipe(proc.getErrorStream(),  "[review_service:err] ");
        }

        // Wait until healthy (up to 45s)

        // inside ensureRunning(), replace the while loop with:
        long deadline = System.currentTimeMillis() + 45_000L;
        while (System.currentTimeMillis() < deadline) {
            // first: if something is already serving, we’re done
            if (isHealthy(baseUrl, 500)) return;

            // if our child died, check again if a server is healthy (someone else may be running it)
            if (proc != null && !proc.isAlive()) {
                if (isHealthy(baseUrl, 1000)) return;
                throw new IOException("review_service process exited immediately. Check the console for logs.");
            }
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        throw new IOException("Could not start the review service: review_service did not come up on "
                + host + ":" + port + " in 45000 ms");
    }

    // ---- helpers ----

    // replace the existing isHealthy with:
    private static boolean isHealthy(String base, int timeoutMs) {
        String[] paths = { "/healthz", "/ping" }; // support both
        for (String p : paths) {
            try {
                HttpClient c = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(timeoutMs))
                        .build();
                HttpRequest rq = HttpRequest.newBuilder()
                        .uri(URI.create(base + p))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .GET().build();
                HttpResponse<String> resp = c.send(rq, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) return true;
            } catch (Exception ignore) {}
        }
        return false;
    }

    private static void pipe(InputStream in, String prefix) {
        var exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "review-service-log");
            t.setDaemon(true);
            return t;
        });
        exec.submit(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(prefix + line);
                }
            } catch (IOException ignored) {}
            exec.shutdown();
            try { exec.awaitTermination(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        });
    }

    private static String sysOrEnv(String sysKey, String envKey, String def) {
        String v = System.getProperty(sysKey);
        if (v != null && !v.isBlank()) return v;
        v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;
        return def;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("win");
    }
}