# TLS Certificate Setup Guide

This guide covers TLS 1.3 certificate generation, configuration, and deployment for the Hospital Management System. It addresses both development (self-signed) and production (CA-signed) certificate scenarios with HIPAA compliance requirements.

## Table of Contents

- [Development Certificate (Self-Signed)](#development-certificate-self-signed)
- [Production Certificate (CA-Signed)](#production-certificate-ca-signed)
- [Converting Certificates to PKCS12](#converting-certificates-to-pkcs12)
- [Environment Configuration](#environment-configuration)
- [Verification Steps](#verification-steps)
- [Security Best Practices](#security-best-practices)
- [Load Balancer / Reverse Proxy](#load-balancer--reverse-proxy)
- [HIPAA Compliance Notes](#hipaa-compliance-notes)
- [Troubleshooting](#troubleshooting)

## Development Certificate (Self-Signed)

For local development and testing, use a self-signed certificate. **DO NOT use self-signed certificates in production.**

### Generate Self-Signed Certificate

```bash
# Generate a 2048-bit RSA key and self-signed certificate
keytool -genkeypair -alias hospital-api -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 365 \
  -storepass changeit \
  -dname "CN=localhost, OU=Hospital Dev, O=Hospital, L=City, ST=State, C=US"
```

**Parameters:**
- `-alias hospital-api`: Identifier for the key entry (must match `server.ssl.key-alias` in application.yml)
- `-keyalg RSA -keysize 2048`: Use RSA encryption with 2048-bit key
- `-storetype PKCS12`: Modern keystore format (preferred over JKS)
- `-validity 365`: Certificate valid for 1 year
- `-storepass changeit`: Keystore password (use a secure password)
- `-dname`: Distinguished Name for the certificate

### Configure Development Environment

```bash
# Set environment variables for development
export SSL_KEYSTORE_PATH=./keystore.p12
export SSL_KEYSTORE_PASSWORD=changeit
```

Or add to `.env` file (ensure `.env` is in `.gitignore`):

```properties
SSL_KEYSTORE_PATH=./keystore.p12
SSL_KEYSTORE_PASSWORD=changeit
```

### Accept Self-Signed Certificate in Browser

When accessing `https://localhost:8443`, browsers will show a security warning. For development:

- **Chrome/Edge**: Click "Advanced" → "Proceed to localhost (unsafe)"
- **Firefox**: Click "Advanced" → "Accept the Risk and Continue"
- **Safari**: Click "Show Details" → "visit this website"

For automated testing, use the `-k` flag with curl:
```bash
curl -k https://localhost:8443/actuator/health
```

## Production Certificate (CA-Signed)

Production deployments require certificates signed by a trusted Certificate Authority (CA). Three common approaches:

### Option A: Let's Encrypt (Recommended for Public-Facing APIs)

**Free, automated, and trusted by all major browsers.**

1. Install Certbot:
   ```bash
   # Ubuntu/Debian
   sudo apt install certbot

   # macOS
   brew install certbot
   ```

2. Generate certificate (requires domain name and port 80/443 access):
   ```bash
   sudo certbot certonly --standalone \
     -d api.hospital.example.com \
     --email admin@hospital.example.com \
     --agree-tos
   ```

3. Certificates are stored in `/etc/letsencrypt/live/api.hospital.example.com/`:
   - `fullchain.pem`: Certificate + intermediate chain
   - `privkey.pem`: Private key

4. Convert to PKCS12 (see [Converting Certificates](#converting-certificates-to-pkcs12))

5. Set up auto-renewal (Let's Encrypt certificates expire every 90 days):
   ```bash
   # Test renewal
   sudo certbot renew --dry-run

   # Add cron job for automatic renewal
   sudo crontab -e
   # Add: 0 3 * * * certbot renew --quiet --post-hook "systemctl restart hospital-api"
   ```

### Option B: Commercial CA (DigiCert, GlobalSign, etc.)

**Recommended for enterprise deployments with extended validation (EV) certificates.**

1. Generate a Certificate Signing Request (CSR):
   ```bash
   openssl req -new -newkey rsa:2048 -nodes \
     -keyout hospital-api.key \
     -out hospital-api.csr \
     -subj "/C=US/ST=State/L=City/O=Hospital/CN=api.hospital.example.com"
   ```

2. Submit `hospital-api.csr` to your CA (DigiCert, GlobalSign, etc.)

3. CA will verify your organization and issue certificate files:
   - `hospital-api.crt`: Your certificate
   - `ca-bundle.crt`: Intermediate certificates

4. Convert to PKCS12 (see [Converting Certificates](#converting-certificates-to-pkcs12))

### Option C: Internal Enterprise CA

**For private APIs behind corporate firewalls.**

If your organization has an internal CA:

1. Request certificate from your internal PKI team
2. Provide CSR (generated using OpenSSL as shown in Option B)
3. Receive certificate and intermediate chain
4. Convert to PKCS12 format

**Note:** Internal CAs require client systems to trust the root CA certificate.

## Converting Certificates to PKCS12

Spring Boot requires PKCS12 format. Convert PEM certificates:

### From Let's Encrypt PEM to PKCS12

```bash
sudo openssl pkcs12 -export \
  -in /etc/letsencrypt/live/api.hospital.example.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/api.hospital.example.com/privkey.pem \
  -out /etc/hospital/keystore.p12 \
  -name hospital-api \
  -passout pass:$SSL_KEYSTORE_PASSWORD
```

### From Commercial CA Certificates to PKCS12

```bash
# Combine certificate and intermediate chain
cat hospital-api.crt ca-bundle.crt > fullchain.pem

# Convert to PKCS12
openssl pkcs12 -export \
  -in fullchain.pem \
  -inkey hospital-api.key \
  -out keystore.p12 \
  -name hospital-api \
  -passout pass:$SSL_KEYSTORE_PASSWORD
```

### Set Secure Permissions

```bash
# Restrict access to keystore
sudo chown hospital-app:hospital-app /etc/hospital/keystore.p12
sudo chmod 600 /etc/hospital/keystore.p12
```

## Environment Configuration

### Production Environment Variables

Set these environment variables in your deployment environment:

```bash
# Required
export SSL_KEYSTORE_PATH=/etc/hospital/keystore.p12
export SSL_KEYSTORE_PASSWORD=<secure-password>

# Optional (defaults provided in application-prod.yml)
export SERVER_PORT=8443
```

### Docker/Kubernetes

**Docker Compose:**
```yaml
services:
  hospital-api:
    image: hospital-api:latest
    environment:
      - SSL_KEYSTORE_PATH=/etc/hospital/keystore.p12
      - SSL_KEYSTORE_PASSWORD=${SSL_KEYSTORE_PASSWORD}
    volumes:
      - /etc/hospital/keystore.p12:/etc/hospital/keystore.p12:ro
    ports:
      - "8443:8443"
```

**Kubernetes Secret:**
```bash
# Create secret from keystore file
kubectl create secret generic hospital-tls \
  --from-file=keystore.p12=/etc/hospital/keystore.p12 \
  --from-literal=password=$SSL_KEYSTORE_PASSWORD
```

**Kubernetes Deployment:**
```yaml
spec:
  containers:
  - name: hospital-api
    env:
    - name: SSL_KEYSTORE_PATH
      value: /etc/hospital/keystore.p12
    - name: SSL_KEYSTORE_PASSWORD
      valueFrom:
        secretKeyRef:
          name: hospital-tls
          key: password
    volumeMounts:
    - name: tls-keystore
      mountPath: /etc/hospital
      readOnly: true
  volumes:
  - name: tls-keystore
    secret:
      secretName: hospital-tls
      items:
      - key: keystore.p12
        path: keystore.p12
```

## Verification Steps

### Test TLS 1.3 Support

```bash
# Verify TLS 1.3 is supported
openssl s_client -connect localhost:8443 -tls1_3 < /dev/null

# Expected output should include:
# Protocol  : TLSv1.3
# Cipher    : TLS_AES_256_GCM_SHA384
```

### Test TLS 1.2 Fallback

```bash
# Verify TLS 1.2 fallback works
openssl s_client -connect localhost:8443 -tls1_2 < /dev/null

# Expected output should include:
# Protocol  : TLSv1.2
# Cipher    : ECDHE-RSA-AES256-GCM-SHA384
```

### Verify Certificate Details

```bash
# List keystore contents
keytool -list -v -keystore /etc/hospital/keystore.p12 -storepass $SSL_KEYSTORE_PASSWORD

# Expected output includes:
# Alias name: hospital-api
# Entry type: PrivateKeyEntry
# Certificate chain length: 1 (self-signed) or 2+ (CA-signed)
```

### Test API Endpoints

```bash
# Test health endpoint
curl https://localhost:8443/actuator/health

# Expected: {"status":"UP"}

# For self-signed certificates, use -k flag
curl -k https://localhost:8443/actuator/health
```

### Check HTTP to HTTPS Redirect (if TlsConfig enabled)

```bash
# Attempt HTTP connection
curl -I http://localhost:8080/actuator/health

# Expected: HTTP/1.1 302 (redirect to https://localhost:8443)
```

### Verify Cipher Suites

```bash
# Test with nmap (install: brew install nmap)
nmap --script ssl-enum-ciphers -p 8443 localhost

# Expected: TLS 1.3 and TLS 1.2 ciphers only, no weak ciphers
```

## Security Best Practices

### Key Generation
- **Minimum key size:** 2048-bit RSA or 256-bit ECDSA
- **Recommended:** 4096-bit RSA for long-lived certificates
- **Algorithm preference:** RSA 2048+ or ECDSA P-256+

### Certificate Rotation
- **Set expiration alerts:** Monitor certificates 30 days before expiration
- **Automate renewal:** Use Let's Encrypt with auto-renewal or enterprise PKI automation
- **Test renewal process:** Verify certificate updates don't cause downtime

### Keystore Security
- **File permissions:** `chmod 600` on keystore file
- **Ownership:** Dedicated service account, not root
- **Password management:** Use secrets manager (AWS Secrets Manager, HashiCorp Vault)
- **Backup:** Encrypted backups of keystores, separate from application backups

### Environment Separation
- **Use different certificates** for dev, staging, and production
- **Never reuse production certificates** in non-production environments
- **Internal vs external:** Use internal CA for private APIs, public CA for external APIs

### Monitoring
- **Certificate expiration:** Set up alerts 30/14/7 days before expiration
- **TLS handshake failures:** Monitor failed TLS connections
- **Cipher suite usage:** Track which clients use TLS 1.2 vs 1.3
- **Certificate transparency logs:** Monitor CT logs for unauthorized certificates

## Load Balancer / Reverse Proxy

Most production deployments use a load balancer or reverse proxy for TLS termination:

### AWS Application Load Balancer (ALB)

```yaml
# ALB handles TLS, application runs on HTTP internally
server:
  port: 8080  # HTTP only
  forward-headers-strategy: framework  # Trust X-Forwarded-* headers

spring:
  security:
    require-ssl: false  # ALB enforces HTTPS
```

**ALB Configuration:**
- Listener: HTTPS:443 → Target Group: HTTP:8080
- Certificate: AWS Certificate Manager (ACM)
- Security policy: ELBSecurityPolicy-TLS13-1-2-2021-06 (TLS 1.3 + 1.2)

### Nginx Reverse Proxy

```nginx
server {
    listen 443 ssl http2;
    server_name api.hospital.example.com;

    # TLS 1.3 configuration
    ssl_protocols TLSv1.3 TLSv1.2;
    ssl_ciphers 'TLS_AES_256_GCM_SHA384:TLS_AES_128_GCM_SHA256:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers on;

    ssl_certificate /etc/letsencrypt/live/api.hospital.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.hospital.example.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### Traefik (Docker/Kubernetes)

```yaml
# docker-compose.yml
services:
  traefik:
    image: traefik:v2.10
    ports:
      - "443:443"
    command:
      - --entrypoints.websecure.address=:443
      - --entrypoints.websecure.http.tls.options=default@file
      - --providers.docker
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - ./traefik-tls.yml:/etc/traefik/dynamic/tls.yml:ro

  hospital-api:
    image: hospital-api:latest
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.hospital.rule=Host(`api.hospital.example.com`)"
      - "traefik.http.routers.hospital.entrypoints=websecure"
      - "traefik.http.routers.hospital.tls.certresolver=letsencrypt"
```

**Important:** When using TLS termination at load balancer/proxy:
- Ensure `X-Forwarded-Proto` header is preserved
- Configure audit logging to use `X-Forwarded-For` for client IP
- Disable TLS in application configuration

## HIPAA Compliance Notes

### Required TLS Versions
- **TLS 1.3:** Recommended (as of 2024 HIPAA guidance)
- **TLS 1.2:** Acceptable minimum
- **TLS 1.0, TLS 1.1, SSLv3:** **PROHIBITED** - known vulnerabilities

### Cipher Suite Requirements
- **Use only strong, authenticated encryption:**
  - TLS 1.3: `TLS_AES_256_GCM_SHA384`, `TLS_AES_128_GCM_SHA256`, `TLS_CHACHA20_POLY1305_SHA256`
  - TLS 1.2: `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`, `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`
- **Disable weak ciphers:**
  - No RC4, DES, 3DES, MD5
  - No anonymous Diffie-Hellman (ADH)
  - No export-grade ciphers

### Certificate Authority Requirements
- **Production certificates MUST be from a trusted CA**
- Self-signed certificates only acceptable for:
  - Development environments
  - Internal testing
  - Network isolated systems (with documented risk acceptance)

### Perfect Forward Secrecy (PFS)
- **Required for HIPAA compliance**
- Use ECDHE (Elliptic Curve Diffie-Hellman Ephemeral) cipher suites
- Ensures session keys cannot be compromised even if server private key is leaked

### Certificate Expiration Monitoring
- **Required under HIPAA security rule (§164.308(a)(1)(ii)(A))**
- Monitor certificate expiration at least 30 days in advance
- Document certificate renewal process in security policies

### Audit Requirements
- **Log TLS connection failures** (attempted weak ciphers, expired certificates)
- **Log certificate changes** (renewal, replacement)
- **Quarterly review** of TLS configuration and cipher suite usage

## Troubleshooting

### Application Fails to Start: "Keystore not found"

**Cause:** `SSL_KEYSTORE_PATH` not set or file doesn't exist.

**Solution:**
```bash
# Check environment variable
echo $SSL_KEYSTORE_PATH

# Verify file exists
ls -l $SSL_KEYSTORE_PATH

# Check application has read permissions
sudo -u hospital-app cat $SSL_KEYSTORE_PATH > /dev/null
```

### Application Fails to Start: "Keystore password incorrect"

**Cause:** `SSL_KEYSTORE_PASSWORD` doesn't match keystore password.

**Solution:**
```bash
# Verify password with keytool
keytool -list -keystore $SSL_KEYSTORE_PATH -storepass $SSL_KEYSTORE_PASSWORD
```

### Browser Shows "ERR_SSL_VERSION_OR_CIPHER_MISMATCH"

**Cause:** Client doesn't support TLS 1.3/1.2 or configured cipher suites.

**Solution:**
```bash
# Test with OpenSSL to see supported protocols
openssl s_client -connect localhost:8443 -showcerts

# If needed, add TLS 1.2 fallback ciphers in application-prod.yml
```

### curl Shows "SSL certificate problem: self signed certificate"

**Cause:** Using self-signed certificate in production or curl doesn't trust CA.

**Solution:**
```bash
# For self-signed (development only)
curl -k https://localhost:8443/actuator/health

# For CA-signed, add CA certificate to trust store
curl --cacert /path/to/ca-bundle.crt https://localhost:8443/actuator/health
```

### HTTP Requests Not Redirecting to HTTPS

**Cause:** `TlsConfig` not active or port 8080 not accessible.

**Solution:**
```bash
# Verify TlsConfig bean is loaded
# Check application logs for: "Bean 'tlsConfig' created"

# Ensure Spring profile is set
export SPRING_PROFILES_ACTIVE=prod

# Check if port 8080 is listening
netstat -an | grep 8080
```

### Let's Encrypt Renewal Fails

**Cause:** Certbot can't bind to port 80/443 or domain DNS not resolving.

**Solution:**
```bash
# Test DNS resolution
dig api.hospital.example.com

# Ensure ports 80/443 are accessible
sudo lsof -i :80
sudo lsof -i :443

# Test renewal with verbose output
sudo certbot renew --dry-run --verbose
```

## Summary

This guide provides comprehensive TLS 1.3 setup for HIPAA-compliant Hospital Management System:

- **Development:** Self-signed certificates for local testing
- **Production:** CA-signed certificates (Let's Encrypt, commercial CA, or internal CA)
- **Configuration:** Spring Boot application-prod.yml with TLS 1.3/1.2 and strong cipher suites
- **Deployment:** Environment variables, Docker, Kubernetes, and load balancer patterns
- **Compliance:** HIPAA requirements for encryption in transit
- **Operations:** Monitoring, rotation, and troubleshooting

For questions or issues, refer to:
- Spring Boot TLS documentation: https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.configure-ssl
- HIPAA Security Rule: https://www.hhs.gov/hipaa/for-professionals/security/
