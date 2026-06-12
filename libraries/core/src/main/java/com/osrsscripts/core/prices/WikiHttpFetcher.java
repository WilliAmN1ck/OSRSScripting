package com.osrsscripts.core.prices;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * Real {@link HttpFetcher} backed by {@link HttpClient}. Sends a descriptive {@code User-Agent},
 * which the OSRS Wiki real-time prices API requires.
 */
public final class WikiHttpFetcher implements HttpFetcher {

    private final HttpClient client;
    private final String userAgent;

    public WikiHttpFetcher(String userAgent) {
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.client = HttpClient.newHttpClient();
    }

    @Override
    public String get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }
}
