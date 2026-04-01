$url = "http://localhost:8080/api/auth/login"
$headers = @{"Content-Type"="application/json"}
$body = '{"username":"admin","password":"Yw110120"}'

try {
    $response = Invoke-WebRequest -Uri $url -Method POST -Headers $headers -Body $body
    Write-Host "API call successful!"
    Write-Host "Status code: $($response.StatusCode)"
    Write-Host "Response content: $($response.Content)"
} catch {
    Write-Host "API call failed: $($_.Exception.Message)"
}