package eviesaurr.sablecat.update;

import com.google.gson.*;
import eviesaurr.sablecat.SableCat;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class UpdateChecker {

    // Query both the personal repo and the open-source repo; use the higher version as the update notification
    private static final String[] GITHUB_API_URLS = {
    };
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private UpdateChecker() {}

    public static void checkAsync() {
        Thread thread = new Thread(UpdateChecker::check, "sablecat Update Checker");
        thread.setDaemon(true);
        thread.start();
    }

    private static void check() {
        try {
            SableCat.LOGGER.info("Checking for updates via GitHub...");

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            String bestRemoteVersion = null;
            String bestHtmlUrl = null;

            for (String apiUrl : GITHUB_API_URLS) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String tagName = json.get("tag_name").getAsString();
                    // tag_name may be "v1.6.3" or "1.6.3"
                    String remoteVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    String htmlUrl = json.get("html_url").getAsString();

                    if (bestRemoteVersion == null || compareVersions(remoteVersion, bestRemoteVersion) > 0) {
                        bestRemoteVersion = remoteVersion;
                        bestHtmlUrl = htmlUrl;
                    }
                } catch (Exception ignored) {
                    // Failure of one repo query does not affect the others
                }
            }

            if (bestRemoteVersion == null) {
                SableCat.LOGGER.warn("Update check failed: no release found in any source repo");
                return;
            }

            if (compareVersions(bestRemoteVersion, SableCat.VERSION) <= 0) {
                SableCat.LOGGER.info("Already up to date (v{})", SableCat.VERSION);
                return;
            }

            SableCat.LOGGER.info("");
            SableCat.LOGGER.info("  ========================================");
            SableCat.LOGGER.info("  |  New version available: v{} (current: v{})", bestRemoteVersion, SableCat.VERSION);
            SableCat.LOGGER.info("  |  Download: {}", bestHtmlUrl);
            SableCat.LOGGER.info("  ========================================");
            SableCat.LOGGER.info("");

        } catch (Exception e) {
            SableCat.LOGGER.warn("Update check failed: {}", e.getMessage());
        }
    }

    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int nb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }
}
