# MPP Server — Machine Payments Protocol (Java Spring Boot)

A production-grade Java implementation of the [Machine Payments Protocol (MPP)](https://mpp.dev),
the open standard for machine-to-machine payments co-developed by Stripe and Tempo.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        MPP Server                                │
│                                                                  │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │  Discovery    │    │  MppPaymentFilter │    │  Paid API     │  │
│  │  Controller   │    │  (WebFilter)      │    │  Controllers  │  │
│  │              │    │                    │    │               │  │
│  │ /.well-known │    │ 1. Check registry │    │ /api/v1/*     │  │
│  │ /mpp/*       │    │ 2. Issue 402      │    │ (protected)   │  │
│  └──────────────┘    │ 3. Verify creds   │    └───────────────┘  │
│                      │ 4. Settle payment │                       │
│                      │ 5. Attach receipt │                       │
│                      └────────┬─────────┘                       │
│                               │                                  │
│  ┌────────────────────────────┼──────────────────────────────┐  │
│  │                    Service Layer                           │  │
│  │                                                            │  │
│  │  ┌─────────────┐  ┌─────────────────┐  ┌──────────────┐  │  │
│  │  │ Challenge   │  │ Credential      │  │ Receipt      │  │  │
│  │  │ Service     │  │ Service         │  │ Service      │  │  │
│  │  │             │  │                 │  │              │  │  │
│  │  │ HMAC-bound  │  │ Parse, verify,  │  │ Generate &   │  │  │
│  │  │ ID gen      │  │ settle pipeline │  │ encode       │  │  │
│  │  │ Replay prot │  │                 │  │              │  │  │
│  │  └─────────────┘  └────────┬────────┘  └──────────────┘  │  │
│  │                             │                              │  │
│  └─────────────────────────────┼──────────────────────────────┘  │
│                                │                                  │
│  ┌─────────────────────────────┼──────────────────────────────┐  │
│  │              PaymentMethod SPI (Pluggable)                 │  │
│  │                                                            │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │  │
│  │  │ Internal     │  │ Stripe       │  │ Card / Tempo │    │  │
│  │  │ Ledger       │  │ (pluggable)  │  │ (pluggable)  │    │  │
│  │  │ ✅ included   │  │ 🔌 add yours │  │ 🔌 add yours │    │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Protocol Flow

```
Agent                              MPP Server
  │                                    │
  │─── GET /api/v1/search?q=hello ────▶│
  │                                    │ (no credential)
  │◀── 402 Payment Required ──────────│
  │    WWW-Authenticate: Payment       │
  │    id="hmac-bound-id"              │
  │    method="internal"               │
  │    intent="charge"                 │
  │    request="<base64url>"           │
  │                                    │
  │  [Agent authorizes payment]        │
  │                                    │
  │─── GET /api/v1/search?q=hello ────▶│
  │    Authorization: Payment <cred>   │
  │                                    │ ✓ HMAC check
  │                                    │ ✓ Expiry check
  │                                    │ ✓ Replay check
  │                                    │ ✓ Balance check
  │                                    │ ✓ Settlement
  │◀── 200 OK ────────────────────────│
  │    Payment-Receipt: <base64url>    │
  │    { search results }              │
  │                                    │
```

## Quick Start

### 1. Build & Run

```bash
cd mpp-server
./mvnw spring-boot:run
```

Server starts on port **8402**.

### 2. Discovery

```bash
# What does this server accept?
curl http://localhost:8402/.well-known/mpp | jq

# What services are available?
curl http://localhost:8402/mpp/discovery/services | jq
```

### 3. Full Payment Flow

```bash
# Step 1: Request a paid resource → get 402 with challenge
curl -v http://localhost:8402/api/v1/search?q=hello

# Step 2: Build credential from challenge, base64url encode it
# Step 3: Retry with Authorization: Payment <credential>
# Step 4: Get 200 + receipt

# Or run the full demo:
./demo.sh
```

### 4. Admin API

```bash
# Credit an agent account
curl -X POST http://localhost:8402/admin/accounts/my-agent/credit \
  -H "Content-Type: application/json" \
  -d '{"amount": "5000"}'

# Check balance
curl http://localhost:8402/admin/accounts/my-agent/balance

# Register a paid resource
curl -X POST http://localhost:8402/admin/resources \
  -H "Content-Type: application/json" \
  -d '{
    "path": "/api/v1/custom",
    "amount": "200",
    "currency": "usd",
    "recipient": "my-service",
    "description": "Custom API - $2.00 per request"
  }'
```

## Adding a Custom Payment Method

Implement `PaymentMethod` and annotate with `@Component`:

```java
@Component
public class StripePaymentMethod implements PaymentMethod {

    @Override
    public String methodId() { return "stripe"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public Mono<VerificationResult> verify(Credential credential, PaymentRequest request) {
        // Verify Stripe PaymentIntent
        String paymentIntentId = (String) credential.getPayload().get("paymentIntentId");
        // Call Stripe API to confirm...
        return Mono.just(VerificationResult.ok());
    }

    @Override
    public Mono<String> settle(Credential credential, PaymentRequest request) {
        // Capture the PaymentIntent
        return Mono.just("pi_xxx_captured");
    }
}
```

The `PaymentMethodRegistry` auto-discovers it via Spring component scan.

## Project Structure

```
src/main/java/dev/mpp/
├── MppServerApplication.java          # Entry point
├── config/
│   ├── MppProperties.java            # Configuration POJO
│   ├── PaidResourceRegistry.java      # URI → pricing mapping
│   ├── BootstrapConfig.java           # Seed data on startup
│   └── JacksonConfig.java            # JSON serialization
├── protocol/
│   ├── challenge/
│   │   ├── Challenge.java             # 402 challenge model
│   │   └── PaymentRequest.java        # Decoded request field
│   ├── credential/
│   │   └── Credential.java            # Authorization proof
│   └── receipt/
│       └── Receipt.java               # Payment acknowledgment
├── payment/
│   └── method/
│       ├── PaymentMethod.java         # SPI interface
│       ├── PaymentMethodRegistry.java # Auto-discovery
│       └── InternalLedgerPaymentMethod.java  # Built-in ledger
├── filter/
│   └── MppPaymentFilter.java         # Core 402 WebFilter
├── service/
│   ├── ChallengeService.java          # HMAC binding + replay
│   ├── CredentialService.java         # Verify + settle pipeline
│   └── ReceiptService.java            # Receipt generation
├── controller/
│   ├── DiscoveryController.java       # /.well-known/mpp
│   ├── AdminController.java           # Account/resource mgmt
│   └── PaidApiController.java         # Example paid endpoints
├── model/
│   └── ProblemDetail.java             # RFC 9457 errors
└── util/
    └── MppCryptoUtil.java             # Base64url, HMAC, SHA-256
```

## Security Features

| Feature | Implementation |
|---------|---------------|
| **Challenge binding** | HMAC-SHA256 on (realm, method, intent, request-hash, expires) |
| **Replay protection** | Single-use challenge IDs tracked in ConcurrentHashMap |
| **Tamper detection** | HMAC verification rejects modified parameters |
| **Expiry enforcement** | Challenges expire after configurable TTL (default 5min) |
| **Constant-time comparison** | `MessageDigest.isEqual()` for HMAC verification |
| **No state leakage** | 402 responses use `Cache-Control: no-store` |
| **Credential privacy** | Credentials are never logged |

## Configuration

```yaml
mpp:
  realm: "api.example.com"
  challenge:
    expiry-seconds: 300
    hmac-secret: "${MPP_HMAC_SECRET}"   # Use env var in prod!
  payment-methods:
    - name: "internal"
      enabled: true
    - name: "stripe"
      enabled: true
      api-key: "${STRIPE_API_KEY}"
```

## Spec Compliance

This implementation follows the [MPP specification](https://mpp.dev/protocol):

- ✅ HTTP 402 Payment Required status code
- ✅ `WWW-Authenticate: Payment` challenge header
- ✅ `Authorization: Payment` credential header
- ✅ `Payment-Receipt` response header
- ✅ Base64url encoding (RFC 4648 §5)
- ✅ HMAC-bound challenge IDs
- ✅ Problem Details (RFC 9457) error responses
- ✅ Single-use credential enforcement
- ✅ Content-Digest body binding (RFC 9530)
- ✅ Multiple payment method support
- ✅ Discovery endpoint (`.well-known/mpp`)
- ✅ Payment method agnostic (pluggable SPI)

## License

MIT
