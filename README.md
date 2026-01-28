

# Tube Status Service (Simple Java)

Minimal, framework-free Java service demonstrating SRE reliability patterns
(retry, circuit breaker, rate limiting) with OpenTelemetry metrics and explicit SLIs/SLOs.
- GET /api/line/{lineId}/status (current) and optional from/to (planned range)
- GET /api/disruptions/unplanned
- GET /healthz

Resilience:
- Circuit breaker: OPEN after 5 consecutive failures, HALF-OPEN after 30s
- Retry: exponential backoff + jitter, max 3 retries, no retry on 4xx
- Rate limit: 100 req/min per client IP (429 + Retry-After)

## Run
`mvn -q clean package`

`java -jar target/tube-status-simple-java-0.1.0.jar`

## Examples
`curl.exe -s "http://localhost:8080/api/line/central/status"`

`curl.exe -s "http://localhost:8080/api/line/northern/status?from=2026-01-29&to=2026-01-30"`

`curl.exe -s "http://localhost:8080/api/disruptions/unplanned"`

`curl.exe -s "http://localhost:8080/healthz"`

or can be tested by using Postman GET with above urls:
```powershell
http://localhost:8080/api/line/circle/status
```
Example response:
```text
{
    "lineId": "circle",
    "lineName": "Circle",
    "status": "Good Service",
    "disrupted": false,
    "planned": false,
    "reasons": [],
    "sourceUrl": "https://api.tfl.gov.uk/Line/circle/Status"
}
```

## Metrics and observability

The service exposes OpenTelemetry metrics via a Prometheus-compatible endpoint: `http://localhost:9464/metrics` endpoint.

Metrics include:
- HTTP request counts and latency histograms
- Upstream (TfL) request counts and latency
- Circuit breaker open / fail-fast events
- Rate limiting (429) counts

Example:
```text
...
upstream_requests_total{target="tfl",status="200"} 3
upstream_request_duration_ms_bucket{target="tfl",le="250"} 3
```

These metrics support SLIs such as:

Availability: 1 − (5xx + 503) / total

Latency: p95 / p99 derived from histograms

Dependency health: upstream success rate and CB open time

For this exercise, metrics are inspected directly via /metrics.
In production, they would be scraped by Prometheus or an OpenTelemetry backend and would be used in alert policies.

## How to test resilience locally

Start a mock upstream (always returns 500)

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\mock-upstream-500.ps1
```

Point the app to the mock upstream

In aaplication.properties:
```test
tfl.baseUrl=http://localhost:9099
```

Restart the app.

Test retries + circuit breaker
```powershell
curl.exe -i http://localhost:8080/api/line/northern/status
```
```text
HTTP/1.1 503 Service Unavailable
X-ratelimit-remaining: 99
Date: Tue, 27 Jan 2026 22:51:30 GMT
Content-type: application/json; charset=utf-8
Content-length: 146

{"timestamp":1769554290.074448900,"status":503,"error":"Service Unavailable","message":"TfL returned HTTP 500","path":"/api/line/northern/status"}
```
First calls show retry logs in the app logs (retry attempt 1/2/3)

After repeated failures, requests fail fast (circuit breaker open)

Response returns 503 Service Unavailable

### Test rate limiting
```powershell
.\scripts\smoke-rate-limit.ps1
```
After ~100 requests/min, responses return 429 Too Many Requests

```text
97: 200  remaining=
98: 200  remaining=
99: 200  remaining=
100: 200  remaining=
101: 429  Retry-After=60
```
Here testing if rate limit enforced, 429 returned and retry-after header present.

### What smoke-retry-cb.ps1 is for
It’s a smoke test to prove retries and the circuit breaker without looking at logs.
Checking if retries actually happen, retries add latency, circuit breaker opens and fail-fast behaviour works.
The first request is slow because retries run; subsequent requests are fast because the circuit breaker opens and fails fast.
```powershell
.\scripts\smoke-retry-cb.ps1
```
```text
Calling http://localhost:8080/api/line/northern/status 8 times...
1: 500 in 796ms
2: 500 in 3ms
3: 500 in 3ms
```

Then restore normal operation

Stop the mock upstream

Switch base URL back to https://api.tfl.gov.uk

Restart the app

### Failure scenario: TfL API unavailable for 6 hours

When the TfL API is unavailable, retries occur briefly before the circuit breaker opens and the service fails fast with `503 Service Unavailable`. This prevents resource exhaustion and keeps latency low while rate limiting continues to apply. Metrics capture elevated error rates and circuit breaker open time, and the breaker periodically probes recovery via a half-open state.

## Architecture decisions and trade-offs

- **Framework-free Java HTTP server**  
  Chosen to make request handling and failure behaviour explicit.  
  *Trade-off:* more manual wiring; fewer framework features.


- **In-code resilience patterns** (retry, circuit breaker, rate limiting)  
  Implemented explicitly to demonstrate behaviour and interactions.  
  *Trade-off:* simpler than production-grade libraries.


- **Configuration externalised**  
  Timeouts, thresholds, and limits are read from config files.  
  *Trade-off:* no dynamic reload or schema validation.


- **Simple logging via `System.out`**  
  Used to make retries and circuit breaker transitions visible during smoke tests.  
  *Trade-off:* no structured logs, levels, or correlation IDs.


- **Testing approach: unit + smoke tests**  
  Unit tests validate resilience logic; PowerShell smoke tests verify real failure scenarios.  
  *Trade-off:* no fully automated integration test pipeline.


- **Stateless design**  
  Enables horizontal scaling.  
  *Trade-off:* rate limiting is per-instance, not global.


- **Selective JavaDoc**  
  JavaDoc is used for core components to document intent rather than generate full API docs.


- **Intentional production gaps**  
  Caching, authentication, distributed tracing, and global rate limiting are omitted to keep scope focused.

