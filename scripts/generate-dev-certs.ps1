# ==============================================================================
# ClassPulse - JSSE/TLS Development Certificate Generator
# ==============================================================================
# Generates a self-signed RSA 2048-bit server keystore and client truststore
# with Subject Alternative Name (SAN) configured for localhost and 127.0.0.1.
# ==============================================================================

param(
    [string]$OutputDir = "certs",
    [string]$Password = "changeit",
    [int]$ValidityDays = 365
)

$ErrorActionPreference = "Stop"

Write-Host "=== ClassPulse TLS Development Certificate Setup ===" -ForegroundColor Cyan

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
    Write-Host "[+] Created output directory: $OutputDir"
}

$ServerKeystore = Join-Path $OutputDir "server-keystore.p12"
$ClientTruststore = Join-Path $OutputDir "client-truststore.p12"
$CertFile = Join-Path $OutputDir "server-cert.crt"

# Clean previous generated files if any
if (Test-Path $ServerKeystore) { Remove-Item $ServerKeystore -Force }
if (Test-Path $ClientTruststore) { Remove-Item $ClientTruststore -Force }
if (Test-Path $CertFile) { Remove-Item $CertFile -Force }

Write-Host "[1/3] Generating server key pair and PKCS12 keystore..."
keytool -genkeypair `
    -alias classpulse-server `
    -keyalg RSA `
    -keysize 2048 `
    -validity $ValidityDays `
    -keystore $ServerKeystore `
    -storetype PKCS12 `
    -storepass $Password `
    -keypass $Password `
    -dname "CN=localhost, OU=ClassPulse, O=University, L=Colombo, C=LK" `
    -ext "SAN=dns:localhost,ip:127.0.0.1"

Write-Host "[2/3] Exporting public certificate from keystore..."
keytool -exportcert `
    -alias classpulse-server `
    -keystore $ServerKeystore `
    -storepass $Password `
    -file $CertFile `
    -rfc

Write-Host "[3/3] Importing public certificate into client truststore..."
keytool -importcert `
    -alias classpulse-server `
    -file $CertFile `
    -keystore $ClientTruststore `
    -storetype PKCS12 `
    -storepass $Password `
    -noprompt

# Remove intermediate crt file
if (Test-Path $CertFile) { Remove-Item $CertFile -Force }

Write-Host "`n=== TLS Setup Completed Successfully! ===" -ForegroundColor Green
Write-Host "Server Keystore:   $ServerKeystore"
Write-Host "Client Truststore: $ClientTruststore"
Write-Host "Store Password:    $Password"
Write-Host "Validity:          $ValidityDays days"
Write-Host "Subject DN:        CN=localhost, OU=ClassPulse, O=University, L=Colombo, C=LK"
Write-Host "SANs:              dns:localhost, ip:127.0.0.1"
Write-Host "========================================================`n"
