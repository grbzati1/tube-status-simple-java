# SLO_DEFINITION.md — Tube Status Service

SLIs
- Availability: 1 - (5xx + 503)/total (exclude 4xx; track 429 separately)
- Latency: p95/p99 for 2xx (also monitor all-responses p99)
- Dependency: TfL 2xx rate, upstream latency, CB open minutes
- Rate limiting: 429 rate

SLOs (30d)
- Availability 99.9%
- Latency (2xx): p95 < 200ms, p99 < 500ms
- Dependency (informational): TfL 2xx ≥ 99%, CB open < 0.5% minutes

Alerts
- Call: (5xx+503) > 5% for 5m; p99(2xx) > 1s for 10m; 503 > X% for 10m; CB open > 10m AND 503 elevated
- Ticket: (5xx+503) > 1% for 1h; 429 > 2% for 30m
