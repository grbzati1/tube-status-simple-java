package com.example.tube.http;

import com.example.tube.dto.LineStatusResponse;
import com.example.tube.dto.UnplannedDisruptionsResponse;
import com.example.tube.errors.BadRequestException;
import com.example.tube.errors.UpstreamUnavailableException;
import com.example.tube.ratelimit.IpRateLimiter;
import com.example.tube.resilience.HttpStatusException;
import com.example.tube.service.TubeStatusService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.example.tube.otel.Metrics;
import io.opentelemetry.api.common.Attributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

public class Router {
    private final TubeStatusService service;
    private final IpRateLimiter limiter;
    private final int rateLimitWindowSeconds;
    private final Metrics metrics;

    public Router(TubeStatusService service,
                  IpRateLimiter limiter,
                  int rateLimitWindowSeconds,
                  Metrics metrics
    ) {
        this.service = service;
        this.limiter = limiter;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.metrics = metrics;
    }

    public void register(HttpServer server) {
        server.createContext("/healthz", this::healthz);
        server.createContext("/api/disruptions/unplanned", this::unplanned);
        server.createContext("/api/line", this::lineRoutes); // /api/line/{id}/status
    }

    private void healthz(HttpExchange ex) throws IOException {
        long start = System.nanoTime();
        int status = 200;
        String route = "/healthz";

        try {
            if (!rateLimit(ex)) { status = 429; return; }
            metrics.rateLimited.add(1);
            Json.sendText(ex, 200, "ok");
        } catch (Exception e) {
            status = 500;
            Json.sendError(ex, 500, "Internal Server Error", e.getMessage());
        } finally {
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            var attrs = Metrics.httpAttrs(route, ex.getRequestMethod(), status);
            metrics.httpRequests.add(1, attrs);
            metrics.httpLatencyMs.record(ms, attrs);
        }
    }


    private void unplanned(HttpExchange ex) throws IOException {
        if (!rateLimit(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { Json.sendError(ex, 405, "Method Not Allowed", "Only GET"); return; }
        try {
            UnplannedDisruptionsResponse r = service.getAllUnplannedDisruptions();
            Json.sendJson(ex, 200, r);
        } catch (UpstreamUnavailableException e) {
            Json.sendError(ex, 503, "Service Unavailable", e.getMessage());
        } catch (Exception e) {
            Json.sendError(ex, 500, "Internal Server Error", e.getMessage());
        }
    }

    private void lineRoutes(HttpExchange ex) throws IOException {
        try {
            if (!rateLimit(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                Json.sendError(ex, 405, "Method Not Allowed", "Only GET supported");
                System.out.println("RESP 405 sent");
                return;
            }
            String[] parts = ex.getRequestURI().getPath().split("/");
            if (parts.length < 5) {
                Json.sendError(ex, 404, "Not Found", "Expected /api/line/{lineId}/status");
                System.out.println("RESP 404 sent");
                return;
            }

            String lineId = parts[3];
            String tail = parts[4];
            if (!"status".equalsIgnoreCase(tail)) {
                Json.sendError(ex, 404, "Not Found", "Expected /api/line/{lineId}/status");
                System.out.println("RESP 404 sent");
                return;
            }
            if (!lineId.matches("^[a-z0-9-]+$")) {
                Json.sendError(ex, 400, "Bad Request", "lineId must be like 'central'");
                System.out.println("RESP 400 sent");
                return;
            }

            Map<String, String> q = Query.parse(ex.getRequestURI());
            LocalDate from = parseDate(q.get("from"));
            LocalDate to = parseDate(q.get("to"));

            try {
                LineStatusResponse r = service.getLineStatus(lineId, from, to);
                Json.sendJson(ex, 200, r);
            } catch (HttpStatusException hs) {
                int code = hs.statusCode();
                int outCode = (code >= 400 && code < 500) ? 400 : 503;
                Json.sendError(ex, outCode, outCode == 400 ? "Bad Request" : "Service Unavailable", hs.getMessage());
                System.out.println("RESP " + outCode + " sent (HttpStatusException)");
            } catch (UpstreamUnavailableException e) {
                Json.sendError(ex, 503, "Service Unavailable", e.getMessage());
                System.out.println("RESP 503 sent (UpstreamUnavailableException)");
            } catch (BadRequestException e) {
                Json.sendError(ex, 400, "Bad Request", e.getMessage());
                System.out.println("RESP 400 sent (BadRequestException)");
            } catch (Exception e) {
                Json.sendError(ex, 500, "Internal Server Error", e.getMessage());
                System.out.println("RESP 500 sent (Exception)");
            }
        } catch (Throwable t) {
            Json.sendError(
                    ex,
                    500,
                    "Internal Server Error",
                    t.getClass().getSimpleName() + ": " + t.getMessage()
            );
            System.out.println("RESP 500 sent (Throwable)");
        }

    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s); }
        catch (DateTimeParseException e) { throw new BadRequestException("Invalid date. Use yyyy-MM-dd"); }
    }

    private boolean rateLimit(HttpExchange ex) throws IOException {
        String key = clientIp(ex);
        boolean allowed = limiter.allow(key);

        if (allowed) {
            return true;
        }

        ex.getResponseHeaders().set("Retry-After", String.valueOf(rateLimitWindowSeconds));
        ex.getResponseHeaders().set("X-RateLimit-Remaining", "0");

        Json.sendError(ex, 429, "Too Many Requests", "Rate limit exceeded");
        return false;
    }


    private String clientIp(HttpExchange ex) {
        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        InetSocketAddress remote = ex.getRemoteAddress();
        if (remote != null && remote.getAddress() != null) return remote.getAddress().getHostAddress();
        return "unknown";
    }
}
