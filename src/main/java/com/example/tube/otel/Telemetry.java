package com.example.tube.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;

public final class Telemetry {

    private Telemetry() {}

    public static OpenTelemetry initPrometheus(int prometheusPort) {

        // Prometheus scrape endpoint: http://localhost:<port>/metrics
        PrometheusHttpServer prometheusReader = PrometheusHttpServer.builder()
                .setHost("0.0.0.0")
                .setPort(prometheusPort)
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(prometheusReader)
                .build();

        return OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();
    }
}
