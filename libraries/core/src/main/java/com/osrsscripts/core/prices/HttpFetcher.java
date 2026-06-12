package com.osrsscripts.core.prices;

import java.io.IOException;

/** Minimal HTTP GET seam, so the price client can be unit-tested without real network calls. */
public interface HttpFetcher {

    /** Returns the response body for a GET of {@code url}, or throws on failure. */
    String get(String url) throws IOException;
}
