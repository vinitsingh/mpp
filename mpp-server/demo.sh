#!/bin/bash
# =============================================================================
# MPP Demo Script - Machine Payments Protocol
# Full payment flow demonstration using curl
# =============================================================================

set -e
BASE_URL="http://localhost:8402"

echo "============================================="
echo "  MPP - Machine Payments Protocol Demo"
echo "============================================="
echo ""

# ── Step 0: Discovery ───────────────────────────────────────
echo "▶ Step 0: Agent discovers MPP capabilities"
echo "  GET /.well-known/mpp"
echo ""
curl -s "$BASE_URL/.well-known/mpp" | python3 -m json.tool
echo ""

echo "  GET /mpp/discovery/services"
curl -s "$BASE_URL/mpp/discovery/services" | python3 -m json.tool
echo ""

# ── Step 1: Request without payment → 402 ──────────────────
echo "============================================="
echo "▶ Step 1: Agent requests paid resource (no credential)"
echo "  GET /api/v1/search?q=hello"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/v1/search?q=hello" \
    -H "Accept: application/json" -D /tmp/mpp_headers.txt)

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -n -1)

echo "  HTTP Status: $HTTP_CODE"
echo "  WWW-Authenticate: $(grep 'WWW-Authenticate' /tmp/mpp_headers.txt | head -1)"
echo ""
echo "  Response body (Problem Details):"
echo "$BODY" | python3 -m json.tool
echo ""

# Parse challenge ID from WWW-Authenticate header
CHALLENGE_ID=$(grep 'WWW-Authenticate' /tmp/mpp_headers.txt | head -1 | \
    sed 's/.*id="\([^"]*\)".*/\1/')
CHALLENGE_REALM=$(grep 'WWW-Authenticate' /tmp/mpp_headers.txt | head -1 | \
    sed 's/.*realm="\([^"]*\)".*/\1/')
CHALLENGE_METHOD=$(grep 'WWW-Authenticate' /tmp/mpp_headers.txt | head -1 | \
    sed 's/.*method="\([^"]*\)".*/\1/')
CHALLENGE_INTENT=$(grep 'WWW-Authenticate' /tmp/mpp_headers.txt | head -1 | \
    sed 's/.*intent="\([^"]*\)".*/\1/')
CHALLENGE_REQUEST=$(grep 'WWW-Authenticate' /tmp/mpp_headers.txt | head -1 | \
    sed 's/.*request="\([^"]*\)".*/\1/')
CHALLENGE_EXPIRES=$(grep 'WWW-Authenticate' /tmp/mpp_headers.txt | head -1 | \
    sed 's/.*expires="\([^"]*\)".*/\1/')

echo "  Parsed challenge:"
echo "    id:      $CHALLENGE_ID"
echo "    method:  $CHALLENGE_METHOD"
echo "    intent:  $CHALLENGE_INTENT"
echo "    request: $CHALLENGE_REQUEST"
echo ""

# ── Step 2: Check agent balance ─────────────────────────────
echo "============================================="
echo "▶ Step 2: Check agent-001 balance"
echo ""
curl -s "$BASE_URL/admin/accounts/agent-001/balance" | python3 -m json.tool
echo ""

# ── Step 3: Build credential and retry ──────────────────────
echo "============================================="
echo "▶ Step 3: Agent builds credential and retries with payment"
echo ""

# Build the credential JSON
CREDENTIAL_JSON=$(cat <<EOF
{
  "challenge": {
    "id": "$CHALLENGE_ID",
    "realm": "$CHALLENGE_REALM",
    "method": "$CHALLENGE_METHOD",
    "intent": "$CHALLENGE_INTENT",
    "request": "$CHALLENGE_REQUEST",
    "expires": "$CHALLENGE_EXPIRES"
  },
  "source": "agent-001",
  "payload": {
    "token": "agent-001-auth-token"
  }
}
EOF
)

echo "  Credential JSON:"
echo "$CREDENTIAL_JSON" | python3 -m json.tool
echo ""

# Base64url encode the credential
CREDENTIAL_B64=$(echo -n "$CREDENTIAL_JSON" | base64 | tr '+/' '-_' | tr -d '=')

echo "  GET /api/v1/search?q=hello"
echo "  Authorization: Payment <credential>"
echo ""

RESPONSE2=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/v1/search?q=hello" \
    -H "Accept: application/json" \
    -H "Authorization: Payment $CREDENTIAL_B64" \
    -D /tmp/mpp_headers2.txt)

HTTP_CODE2=$(echo "$RESPONSE2" | tail -1)
BODY2=$(echo "$RESPONSE2" | head -n -1)

echo "  HTTP Status: $HTTP_CODE2"

if [ "$HTTP_CODE2" = "200" ]; then
    RECEIPT=$(grep 'Payment-Receipt' /tmp/mpp_headers2.txt | head -1)
    echo "  Payment-Receipt: $RECEIPT"
    echo ""
    echo "  ✅ Payment successful! Resource delivered:"
    echo "$BODY2" | python3 -m json.tool
else
    echo "  ❌ Payment failed:"
    echo "$BODY2" | python3 -m json.tool
fi
echo ""

# ── Step 4: Check balance after payment ─────────────────────
echo "============================================="
echo "▶ Step 4: Check agent-001 balance after payment"
echo ""
curl -s "$BASE_URL/admin/accounts/agent-001/balance" | python3 -m json.tool
echo ""

# ── Step 5: Replay protection ──────────────────────────────
echo "============================================="
echo "▶ Step 5: Replay protection - reuse same credential"
echo ""

RESPONSE3=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/v1/search?q=hello" \
    -H "Accept: application/json" \
    -H "Authorization: Payment $CREDENTIAL_B64" \
    -D /tmp/mpp_headers3.txt)

HTTP_CODE3=$(echo "$RESPONSE3" | tail -1)
BODY3=$(echo "$RESPONSE3" | head -n -1)

echo "  HTTP Status: $HTTP_CODE3"
if [ "$HTTP_CODE3" = "402" ]; then
    echo "  ✅ Replay correctly rejected!"
    echo "$BODY3" | python3 -m json.tool
fi
echo ""

echo "============================================="
echo "  Demo complete!"
echo "============================================="
