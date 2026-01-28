package com.example.tube.service;

import com.example.tube.errors.UpstreamUnavailableException;
import com.example.tube.resilience.CallNotPermittedException;
import com.example.tube.resilience.CircuitBreaker;
import com.example.tube.resilience.HttpStatusException;
import com.example.tube.resilience.RetryPolicy;
import com.example.tube.tfl.Line;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import com.example.tube.otel.Metrics;

/**
 * Client for calling the TfL API with built-in resilience.
 *
 * <p>This client wraps outbound HTTP calls with:
 * <ul>
 *   <li>Circuit breaker for fast failure when the dependency is unhealthy</li>
 *   <li>Retry with backoff for transient upstream or network failures</li>
 *   <li>Timeouts to prevent slow dependencies from consuming resources</li>
 *   <li>OpenTelemetry metrics for dependency latency and availability SLIs</li>
 * </ul>
 *
 * <p>All failures are normalised into domain-specific exceptions so that
 * higher layers (e.g. HTTP routing) can map them consistently to API responses.
 *
 * <p>This class is synchronous and stateless; it is safe to reuse across requests.
 */
public class TflClient {
    private final HttpClient http;
    private final CircuitBreaker cb;
    private final RetryPolicy retry;
    private final String baseUrl;
    private final Metrics metrics;
    private final ObjectMapper om = new ObjectMapper();

    public TflClient(HttpClient http, CircuitBreaker cb, RetryPolicy retry, String baseUrl, Metrics metrics) {
        this.http = http;
        this.cb = cb;
        this.retry = retry;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.metrics = metrics;
    }

    public Line[] getLineStatus(String lineId, LocalDate from, LocalDate to) {
        String path = (from == null || to == null)
                ? "/Line/%s/Status".formatted(lineId)
                : "/Line/%s/Status/%s/to/%s".formatted(lineId, from, to);
        return getJson(path, Line[].class);
    }

    public Line[] getAllTubeLineStatus() {
        return getJson("/Line/Mode/tube/Status", Line[].class);
    }

    private <T> T getJson(String path, Class<T> clazz) {
        Callable<T> oneAttempt = () -> {
            cb.acquirePermission();                 // fail fast if OPEN
            HttpResponse<String> resp = send(path); // does HTTP + metrics
            int code = resp.statusCode();

            if (code >= 200 && code < 300) {
                cb.onSuccess();
                return om.readValue(resp.body(), clazz);
            }

            cb.onFailure();
            throw new HttpStatusException(code, "TfL returned HTTP " + code);
        };

        try {
            return retry.execute(oneAttempt);

        } catch (CallNotPermittedException e) {
            metrics.cbOpenBlocked.add(1);
            throw new UpstreamUnavailableException("TfL circuit breaker is OPEN; failing fast", e);

        } catch (HttpStatusException e) {
            // Router maps 4xx -> 400, 5xx -> 503 (retry policy already applied)
            throw e;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException("TfL call interrupted", e);

        } catch (IOException e) {
            throw new UpstreamUnavailableException("TfL call failed after retries", e);

        } catch (Exception e) {
            throw new UpstreamUnavailableException("TfL call failed", e);
        }
    }


    private HttpResponse<String> send(String path) throws IOException, InterruptedException {
        String url = baseUrl + path;
        System.out.println("TfL GET " + url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(300))
                .GET()
                .build();

        long start = System.nanoTime();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();

        double ms = (System.nanoTime() - start) / 1_000_000.0;
        metrics.upstreamRequests.add(1, Metrics.upstreamAttrs("tfl", code));
        metrics.upstreamLatencyMs.record(ms, Metrics.upstreamAttrs("tfl", code));

        return resp;
    }

}
