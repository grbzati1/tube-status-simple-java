param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Calls = 8
)

$uri = "$BaseUrl/api/line/northern/status"

Write-Host "Calling $uri $Calls times..."
for ($i=1; $i -le $Calls; $i++) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 10
        $sw.Stop()
        "{0,2}: {1} in {2}ms" -f $i, $r.StatusCode, $sw.ElapsedMilliseconds
    } catch {
        $sw.Stop()
        $resp = $_.Exception.Response
        if ($resp -and $resp.StatusCode) {
            $code = [int]$resp.StatusCode.value__
            "{0,2}: {1} in {2}ms" -f $i, $code, $sw.ElapsedMilliseconds
        } else {
            "{0,2}: ERROR '{1}' in {2}ms" -f $i, $_.Exception.Message, $sw.ElapsedMilliseconds
        }
    }
}
