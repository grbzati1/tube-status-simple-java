package com.example.tube.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

public final class Metrics {

    public final LongCounter httpRequests;
    public final DoubleHistogram httpLatencyMs;

    public final LongCounter upstreamRequests;
    public final DoubleHistogram upstreamLatencyMs;

    public final LongCounter rateLimited;
    public final LongCounter cbOpenBlocked;

    public static Metrics noop() {
        var meter = OpenTelemetry.noop().getMeter("noop");
        return new Metrics(meter);
    }

    public Metrics(Meter meter) {
        httpRequests = meter.counterBuilder("http_server_requests_total")
                .setDescription("Total HTTP requests")
                .build();

        httpLatencyMs = meter.histogramBuilder("http_server_request_duration_ms")
                .setDescription("HTTP request duration (ms)")
                .setUnit("ms")
                .build();

        upstreamRequests = meter.counterBuilder("upstream_requests_total")
                .setDescription("Total upstream requests")
                .build();

        upstreamLatencyMs = meter.histogramBuilder("upstream_request_duration_ms")
                .setDescription("Upstream request duration (ms)")
                .setUnit("ms")
                .build();

        rateLimited = meter.counterBuilder("rate_limited_total")
                .setDescription("Total 429 responses")
                .build();

        cbOpenBlocked = meter.counterBuilder("circuit_breaker_open_blocked_total")
                .setDescription("Requests blocked due to CB OPEN")
                .build();
    }

    public static Attributes httpAttrs(String route, String method, int status) {
        return Attributes.builder()
                .put("route", route)
                .put("method", method)
                .put("status", Integer.toString(status))
                .build();
    }

    public static Attributes upstreamAttrs(String target, int status) {
        return Attributes.builder()
                .put("target", target)
                .put("status", Integer.toString(status))
                .build();
    }
}
