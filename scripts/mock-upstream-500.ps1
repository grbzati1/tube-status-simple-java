param([int]$Port = 9099)

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://localhost:$Port/")
$listener.Start()

Write-Host "Mock upstream listening on http://localhost:$Port/ (always 500)"

while ($true) {
  $ctx = $listener.GetContext()
  $resp = $ctx.Response
  $resp.StatusCode = 500
  $bytes = [System.Text.Encoding]::UTF8.GetBytes("upstream error")
  $resp.OutputStream.Write($bytes, 0, $bytes.Length)
  $resp.Close()
}
