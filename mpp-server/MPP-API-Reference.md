# MPP API — Request & Response Reference

Base URL: `http://localhost:8402`

---

## 1. Discovery

### GET /.well-known/mpp

```bash
curl -s http://localhost:8402/.well-known/mpp | jq
```

**Response: 200 OK**
```json
{
  "protocol": "MPP",
  "version": "0.1.0",
  "realm": "localhost:8402",
  "methods": ["internal"],
  "intents": ["charge", "session"],
  "services_url": "/mpp/discovery/services"
}
```

### GET /mpp/discovery/services

```bash
curl -s http://localhost:8402/mpp/discovery/services | jq
```

**Response: 200 OK**
```json
{
  "realm": "localhost:8402",
  "services": [
    {
      "path": "/api/v1/generate",
      "amount": "100",
      "currency": "usd",
      "method": "internal",
      "intent": "charge",
      "description": "Text generation - $1.00 per request"
    },
    {
      "path": "/api/v1/search",
      "amount": "10",
      "currency": "usd",
      "method": "internal",
      "intent": "charge",
      "description": "Web search - $0.10 per query"
    },
    {
      "path": "/api/v1/image",
      "amount": "500",
      "currency": "usd",
      "method": "internal",
      "intent": "charge",
      "description": "Image generation - $5.00 per image"
    },
    {
      "path": "/api/v1/data",
      "amount": "50",
      "currency": "usd",
      "method": "internal",
      "intent": "charge",
      "description": "Data API - $0.50 per request"
    }
  ]
}
```

---

## 2. Request without payment → 402

### GET /api/v1/search?q=hello (no Authorization header)

```bash
curl -sv http://localhost:8402/api/v1/search?q=hello 2>&1 | grep -E '< HTTP|< WWW|< Cache'
```

**Response: 402 Payment Required**

Headers:
```
HTTP/1.1 402 Payment Required
WWW-Authenticate: Payment id="aB3xYz...", realm="localhost:8402",
    method="internal", intent="charge",
    expires="2026-03-29T16:05:00Z",
    request="eyJhbW91bnQiOiIxMCIsImN1cnJlbmN5IjoidXNkIiwicmVjaXBpZW50Ijoic2VydmljZS1hY2NvdW50LTEifQ"
Cache-Control: no-store
Content-Type: application/json
```

Body:
```json
{
  "type": "https://paymentauth.org/problems/payment-required",
  "title": "Payment Required",
  "status": 402,
  "detail": "Web search - $0.10 per query"
}
```

The `request` field decodes (base64url → JSON) to:
```json
{
  "amount": "10",
  "currency": "usd",
  "recipient": "service-account-1"
}
```

---

## 3. Build credential and pay

### Step 3a: Build the credential JSON

Using the challenge values from step 2:

```json
{
  "challenge": {
    "id": "<challenge_id from step 2>",
    "realm": "localhost:8402",
    "method": "internal",
    "intent": "charge",
    "request": "<request field from step 2>",
    "expires": "<expires from step 2>"
  },
  "source": "agent-001",
  "payload": {
    "token": "agent-001-auth-token"
  }
}
```

### Step 3b: Base64url encode and send

```bash
# One-liner: get challenge, build credential, pay
CHALLENGE_ID="<paste from step 2>"
REALM="localhost:8402"
METHOD="internal"
INTENT="charge"
REQUEST="<paste from step 2>"
EXPIRES="<paste from step 2>"

CRED=$(echo -n "{\"challenge\":{\"id\":\"$CHALLENGE_ID\",\"realm\":\"$REALM\",\"method\":\"$METHOD\",\"intent\":\"$INTENT\",\"request\":\"$REQUEST\",\"expires\":\"$EXPIRES\"},\"source\":\"agent-001\",\"payload\":{\"token\":\"agent-001-auth-token\"}}" | base64 | tr '+/' '-_' | tr -d '=')

curl -s http://localhost:8402/api/v1/search?q=hello \
  -H "Authorization: Payment $CRED" | jq
```

**Response: 200 OK**

Headers:
```
HTTP/1.1 200 OK
Payment-Receipt: eyJjaGFsbGVuZ2VJZCI6ImFCM3hZei4uLiIs...
Cache-Control: private
Content-Type: application/json
```

Body:
```json
{
  "query": "hello",
  "results": [
    {
      "title": "Result 1 for: hello",
      "url": "https://example.com/1"
    },
    {
      "title": "Result 2 for: hello",
      "url": "https://example.com/2"
    }
  ],
  "timestamp": "2026-03-29T16:00:05Z"
}
```

The `Payment-Receipt` header decodes to:
```json
{
  "challengeId": "aB3xYz...",
  "method": "internal",
  "reference": "internal-abc123def4",
  "settlement": {
    "amount": "10",
    "currency": "usd"
  },
  "status": "success",
  "timestamp": "2026-03-29T16:00:05Z"
}
```

---

## 4. Replay same credential → rejected

```bash
# Reuse the exact same $CRED from step 3
curl -s http://localhost:8402/api/v1/search?q=hello \
  -H "Authorization: Payment $CRED" | jq
```

**Response: 402 Payment Required**

```json
{
  "type": "https://paymentauth.org/problems/invalid-challenge",
  "title": "Invalid Challenge",
  "status": 402,
  "detail": "Challenge already consumed (replay detected)"
}
```

A fresh `WWW-Authenticate` challenge is included — agent must pay again.

---

## 5. Admin: Check balance

```bash
curl -s http://localhost:8402/admin/accounts/agent-001/balance | jq
```

**Response: 200 OK**
```json
{
  "source": "agent-001",
  "balance": "9990"
}
```

(Started with 10000, spent 10 on the search = 9990 remaining)

---

## 6. Admin: Credit an account

```bash
curl -s -X POST http://localhost:8402/admin/accounts/agent-001/credit \
  -H "Content-Type: application/json" \
  -d '{"amount": "5000"}' | jq
```

**Response: 200 OK**
```json
{
  "source": "agent-001",
  "credited": "5000",
  "balance": "14990"
}
```

---

## 7. Admin: Register a new paid resource

```bash
curl -s -X POST http://localhost:8402/admin/resources \
  -H "Content-Type: application/json" \
  -d '{
    "path": "/api/v1/translate",
    "amount": "25",
    "currency": "usd",
    "recipient": "service-account-1",
    "method": "internal",
    "intent": "charge",
    "description": "Translation API - $0.25 per request"
  }' | jq
```

**Response: 201 Created**
```json
{
  "path": "/api/v1/translate",
  "pricing": {
    "amount": "25",
    "currency": "usd",
    "recipient": "service-account-1",
    "method": "internal",
    "intent": "charge",
    "description": "Translation API - $0.25 per request"
  },
  "status": "registered"
}
```

---

## 8. Admin: List all paid resources

```bash
curl -s http://localhost:8402/admin/resources | jq
```

**Response: 200 OK**
```json
{
  "/api/v1/generate": {
    "amount": "100",
    "currency": "usd",
    "description": "Text generation - $1.00 per request"
  },
  "/api/v1/search": {
    "amount": "10",
    "currency": "usd",
    "description": "Web search - $0.10 per query"
  },
  "/api/v1/image": {
    "amount": "500",
    "currency": "usd",
    "description": "Image generation - $5.00 per image"
  },
  "/api/v1/data": {
    "amount": "50",
    "currency": "usd",
    "description": "Data API - $0.50 per request"
  }
}
```

---

## Error Responses Reference

| Scenario | Status | Problem Type |
|----------|--------|-------------|
| No payment credential | 402 | `payment-required` |
| Replayed credential | 402 | `invalid-challenge` |
| Expired challenge | 402 | `payment-expired` |
| Bad base64 / malformed JSON | 402 | `malformed-credential` |
| Insufficient balance | 402 | `verification-failed` |
| Unsupported payment method | 402 | `method-unsupported` |
| Amount too low | 402 | `payment-insufficient` |

All errors follow RFC 9457 Problem Details format:
```json
{
  "type": "https://paymentauth.org/problems/{error-code}",
  "title": "Human Readable Title",
  "status": 402,
  "detail": "Specific error message"
}
```
