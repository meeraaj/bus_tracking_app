<#
.SYNOPSIS
  One-time developer tool: generates the static, road-snapped route asset
  (app/src/main/assets/routes/<routeId>.json) from an ordered list of stop
  coordinates.

.DESCRIPTION
  This is the ONLY place in the project that talks to OSRM. It runs on a
  developer machine at authoring time. The output JSON is committed to the
  repository and bundled in the APK, so client devices never call
  router.project-osrm.org (Architecture Guardrail #3).

  Re-run only when the route or its stops change.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools/generate-route-polyline.ps1 -RouteId route_85 -RouteName "Route 85" `
      -Stops "12.923789,80.156028;12.923444,80.159860;12.922650,80.162502;12.921098,80.167407;12.918524,80.176407" `
      -StopNames "Stop A;Stop B;Stop C;Stop D;Stop E"
#>
param(
    [Parameter(Mandatory = $true)] [string] $RouteId,
    [Parameter(Mandatory = $true)] [string] $RouteName,
    # Ordered stops as "lat,lng;lat,lng;..."
    [Parameter(Mandatory = $true)] [string] $Stops,
    # Optional stop names as "Name 1;Name 2;..." (same order as -Stops)
    [string] $StopNames = "",
    [string]   $OutDir = ""
)

$ErrorActionPreference = "Stop"
if (-not $OutDir) { $OutDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\app\src\main\assets\routes" }
$inv = [System.Globalization.CultureInfo]::InvariantCulture

$stopList = $Stops.Split(";") | Where-Object { $_.Trim() }
$nameList = if ($StopNames) { $StopNames.Split(";") | ForEach-Object { $_.Trim() } } else { @() }

$parsed = foreach ($s in $stopList) {
    $p = $s.Split(",") | ForEach-Object { $_.Trim() }
    if ($p.Count -ne 2) { throw "Invalid stop '$s' (expected 'lat,lng')" }
    [pscustomobject]@{ Lat = [double]::Parse($p[0], $inv); Lng = [double]::Parse($p[1], $inv) }
}

# OSRM expects lng,lat pairs separated by ';'
$coordParam = ($parsed | ForEach-Object { $_.Lng.ToString($inv) + "," + $_.Lat.ToString($inv) }) -join ";"
$url = "https://router.project-osrm.org/route/v1/driving/$coordParam" + "?overview=full&geometries=geojson&steps=false"

Write-Host "Requesting road-snapped geometry from OSRM (one-time, dev machine only)..."
$resp = Invoke-RestMethod -Uri $url -Headers @{ "User-Agent" = "com.campus.bustracker-route-tool/1.0" } -TimeoutSec 60
if ($resp.code -ne "Ok") { throw "OSRM returned code '$($resp.code)'" }

$polyline = @($resp.routes[0].geometry.coordinates | ForEach-Object {
    # Objects (not nested arrays) because Windows PowerShell 5.1 flattens nested arrays in ConvertTo-Json.
    [ordered]@{ lat = [math]::Round($_[1], 6); lng = [math]::Round($_[0], 6) }
})

$stopsOut = for ($i = 0; $i -lt $parsed.Count; $i++) {
    $wp = $resp.waypoints[$i]
    $name = if ($i -lt $nameList.Count -and $nameList[$i]) { $nameList[$i] }
            elseif ($wp.name) { "$($wp.name) (Stop $($i + 1))" }
            else { "Stop $($i + 1)" }
    [ordered]@{
        stopId        = "{0}_s{1:D2}" -f $RouteId, ($i + 1)
        name          = $name
        latitude      = $parsed[$i].Lat
        longitude     = $parsed[$i].Lng
        sequenceOrder = $i + 1
    }
}

$doc = [ordered]@{
    routeId        = $RouteId
    routeName      = $RouteName
    generatedAt    = (Get-Date).ToString("o")
    source         = "OSRM (one-time, developer machine) - do not call from clients"
    distanceMeters = [math]::Round($resp.routes[0].distance, 1)
    polylinePoints = $polyline
    stops          = @($stopsOut)
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$outFile = Join-Path $OutDir "$RouteId.json"
$json = $doc | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText($outFile, $json, (New-Object System.Text.UTF8Encoding($false)))  # UTF-8 without BOM
Write-Host ("Wrote {0} ({1} polyline points, {2} stops, {3} m)" -f $outFile, $polyline.Count, $parsed.Count, $doc.distanceMeters)
