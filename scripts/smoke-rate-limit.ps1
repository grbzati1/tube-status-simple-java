param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$Requests = 120
)

$uri = "$BaseUrl/healthz"

Write-Host "Hitting $uri $Requests times..."
$counts = @{
  "200" = 0
  "429" = 0
  "other" = 0
}

for ($i=1; $i -le $Requests; $i++) {
  try {
    $r = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 5
    $code = [string]$r.StatusCode
    if ($code -eq "200") { $counts["200"]++ } else { $counts["other"]++ }
    "{0,3}: {1}  remaining={2}" -f $i, $code, $r.Headers["X-RateLimit-Remaining"]
  }
  catch {
    $resp = $_.Exception.Response
    if ($resp -and $resp.StatusCode) {
      $code = [string]$resp.StatusCode.value__
      if ($code -eq "429") {
        $counts["429"]++
        $retryAfter = $resp.Headers["Retry-After"]
        "{0,3}: 429  Retry-After={1}" -f $i, $retryAfter
      } else {
        $counts["other"]++
        "{0,3}: {1}" -f $i, $code
      }
    } else {
      $counts["other"]++
      "{0,3}: ERROR {1}" -f $i, $_.Exception.Message
    }
  }
}

Write-Host ""
Write-Host "Summary: 200=$($counts["200"])  429=$($counts["429"])  other=$($counts["other"])"
