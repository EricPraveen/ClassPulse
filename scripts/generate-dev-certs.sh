#!/usr/bin/env bash
# ==============================================================================
# ClassPulse - JSSE/TLS Development Certificate Generator
# ==============================================================================
# Generates a self-signed RSA 2048-bit server keystore and client truststore
# with Subject Alternative Name (SAN) configured for localhost and 127.0.0.1.
# ==============================================================================

set -euo pipefail

OUTPUT_DIR="${1:-certs}"
PASSWORD="${2:-changeit}"
VALIDITY_DAYS="${3:-365}"

echo "=== ClassPulse TLS Development Certificate Setup ==="

mkdir -p "${OUTPUT_DIR}"

SERVER_KEYSTORE="${OUTPUT_DIR}/server-keystore.p12"
CLIENT_TRUSTSTORE="${OUTPUT_DIR}/client-truststore.p12"
CERT_FILE="${OUTPUT_DIR}/server-cert.crt"

rm -f "${SERVER_KEYSTORE}" "${CLIENT_TRUSTSTORE}" "${CERT_FILE}"

echo "[1/3] Generating server key pair and PKCS12 keystore..."
keytool -genkeypair \
    -alias classpulse-server \
    -keyalg RSA \
    -keysize 2048 \
    -validity "${VALIDITY_DAYS}" \
    -keystore "${SERVER_KEYSTORE}" \
    -storetype PKCS12 \
    -storepass "${PASSWORD}" \
    -keypass "${PASSWORD}" \
    -dname "CN=localhost, OU=ClassPulse, O=University, L=Colombo, C=LK" \
    -ext "SAN=dns:localhost,ip:127.0.0.1"

echo "[2/3] Exporting public certificate from keystore..."
keytool -exportcert \
    -alias classpulse-server \
    -keystore "${SERVER_KEYSTORE}" \
    -storepass "${PASSWORD}" \
    -file "${CERT_FILE}" \
    -rfc

echo "[3/3] Importing public certificate into client truststore..."
keytool -importcert \
    -alias classpulse-server \
    -file "${CERT_FILE}" \
    -keystore "${CLIENT_TRUSTSTORE}" \
    -storetype PKCS12 \
    -storepass "${PASSWORD}" \
    -noprompt

rm -f "${CERT_FILE}"

echo ""
echo "=== TLS Setup Completed Successfully! ==="
echo "Server Keystore:   ${SERVER_KEYSTORE}"
echo "Client Truststore: ${CLIENT_TRUSTSTORE}"
echo "Store Password:    ${PASSWORD}"
echo "Validity:          ${VALIDITY_DAYS} days"
echo "Subject DN:        CN=localhost, OU=ClassPulse, O=University, L=Colombo, C=LK"
echo "SANs:              dns:localhost, ip:127.0.0.1"
echo "=========================================="
