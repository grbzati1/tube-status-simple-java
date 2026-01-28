package com.example.tube;

import com.example.tube.otel.Metrics;
import com.example.tube.otel.Telemetry;
import com.example.tube.ratelimit.IpRateLimiter;
import com.example.tube.resilience.CircuitBreaker;
import com.example.tube.resilience.HttpStatusException;
import com.example.tube.resilience.RetryPolicy;
import com.example.tube.service.TflClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ResilienceTests {

    @Test
    void retries_on_5xx_then_succeeds() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("err"));
            server.enqueue(new MockResponse().setResponseCode(502).setBody("err"));
            server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
            server.start();

            var http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(200))
                    .build();

            var cb = new CircuitBreaker(5, Duration.ofSeconds(30), 2);

            // maxAttempts=4 => up to 4 total attempts
            var retry = new RetryPolicy(4, 1);

            var client = new TflClient(http, cb, retry, server.url("/").toString(), Metrics.noop());

            var lines = client.getLineStatus("northern", null, null);

            assertNotNull(lines);
            assertEquals(3, server.getRequestCount(), "Should retry twice then succeed");
        }
    }

    @Test
    void does_not_retry_on_4xx() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(400).setBody("bad request"));
            server.start();

            var http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(200))
                    .build();

            var cb = new CircuitBreaker(5, Duration.ofSeconds(30), 2);
            var retry = new RetryPolicy(4, 1);

            var client = new TflClient(http, cb, retry, server.url("/").toString(), Metrics.noop());

            HttpStatusException ex =
                    assertThrows(HttpStatusException.class, () -> client.getLineStatus("northern", null, null));

            assertEquals(400, ex.statusCode(), "Should surface the upstream 4xx");
            assertEquals(1, server.getRequestCount(), "Should not retry 4xx");
        }
    }

    @Test
    void circuit_breaker_opens_after_5_failures_and_then_fails_fast() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            for (int i = 0; i < 50; i++) {
                server.enqueue(new MockResponse().setResponseCode(500).setBody("err"));
            }
            server.start();

            var http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(200))
                    .build();

            var cb = new CircuitBreaker(5, Duration.ofSeconds(30), 2);

            // No retries so each client call == 1 upstream call
            var retry = RetryPolicy.noRetry();

            var client = new TflClient(http, cb, retry, server.url("/").toString(), Metrics.noop());

            // First 5 calls: hit upstream and fail
            for (int i = 0; i < 5; i++) {
                assertThrows(RuntimeException.class, () -> client.getLineStatus("northern", null, null));
            }

            int before = server.getRequestCount();

            // Next call: breaker open -> should fail fast and NOT hit upstream
            assertThrows(RuntimeException.class, () -> client.getLineStatus("northern", null, null));

            int after = server.getRequestCount();
            assertEquals(before, after, "Breaker open should prevent upstream call");
        }
    }

    @Test
    void resets_after_window() throws Exception {
        IpRateLimiter limiter = new IpRateLimiter(2, Duration.ofMillis(50));

        assertTrue(limiter.allow("1.2.3.4"));
        assertTrue(limiter.allow("1.2.3.4"));
        assertFalse(limiter.allow("1.2.3.4"));

        Thread.sleep(60);

        assertTrue(limiter.allow("1.2.3.4"), "Should allow again after window reset");
    }
}
