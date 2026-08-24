# Auto-configure JDK from IDEA
Write-Host "Finding IDEA bundled JDK..." -ForegroundColor Cyan

$possiblePaths = @(
    "D:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4\jbr",
    "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.4\jbr"
)

$foundJdk = $null
foreach ($path in $possiblePaths) {
    if (Test-Path "$path\bin\javac.exe") {
        $foundJdk = $path
        Write-Host "Found IDEA JDK: $path" -ForegroundColor Green
        break
    }
}

if ($foundJdk) {
    [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $foundJdk, "User")
    $env:JAVA_HOME = $foundJdk
    
    $newPath = "$foundJdk\bin;" + [System.Environment]::GetEnvironmentVariable("Path", "User")
    [System.Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    $env:Path = "$foundJdk\bin;" + $env:Path
    
    Write-Host "JAVA_HOME set to: $foundJdk" -ForegroundColor Green
    Write-Host "Please restart terminal or run: `$env:Path = `"$foundJdk\bin;`$env:Path`"" -ForegroundColor Yellow
    
    Write-Host "`nVerifying..." -ForegroundColor Cyan
    & "$foundJdk\bin\java.exe" -version
    & "$foundJdk\bin\javac.exe" -version
} else {
    Write-Host "IDEA JDK not found" -ForegroundColor Red
    Write-Host "Please install JDK 17 from: https://www.oracle.com/java/technologies/downloads/#java17"
    Write-Host "Or run project directly in IDEA (Right-click GameHallApplication.java -> Run)"
}
