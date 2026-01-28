package com.example.tube;

import com.example.tube.config.AppConfig;
import com.example.tube.http.Router;
import com.example.tube.otel.Metrics;
import com.example.tube.otel.Telemetry;
import com.example.tube.resilience.CircuitBreaker;
import com.example.tube.resilience.RetryPolicy;
import com.example.tube.ratelimit.IpRateLimiter;
import com.example.tube.service.TflClient;
import com.example.tube.service.TubeStatusService;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

public class App {
    public static void main(String[] args) throws Exception {

        AppConfig cfg = new AppConfig("config/application.properties");

        int port = cfg.getInt("server.port", 8080);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(200))
                .build();

        CircuitBreaker cb = new CircuitBreaker(
                cfg.getInt("cb.failureThreshold", 5),
                Duration.ofSeconds(cfg.getInt("cb.openDurationSeconds", 30)),
                cfg.getInt("cb.halfOpenPermits", 2)
        );

        RetryPolicy retry = new RetryPolicy(
                cfg.getInt("retry.maxAttempts", 3),
                cfg.getInt("retry.baseDelayMs", 200)
        );

        var otel = Telemetry.initPrometheus(cfg.getInt("otel.prometheusPort", 9464));
        var meter = otel.getMeter("tube-status-simple");
        var metrics = new Metrics(meter);

        TflClient tfl = new TflClient(
                httpClient,
                cb,
                retry,
                cfg.getString("tfl.baseUrl"),
                metrics
        );
        TubeStatusService service = new TubeStatusService(tfl, cfg.getString("tfl.baseUrl"));

        IpRateLimiter limiter =
                new IpRateLimiter(
                        cfg.getInt("ratelimit.maxRequests", 100),
                        Duration.ofSeconds(cfg.getInt("ratelimit.windowSeconds", 60))
                );

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));

        int windowSeconds = cfg.getInt("ratelimit.windowSeconds", 60);
        new Router(service, limiter,windowSeconds,metrics).register(server);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Listening on http://localhost:" + port);
    }
}
