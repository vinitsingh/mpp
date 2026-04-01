# mppx

Machine Payments Protocol SDK. Your own payment rail for machine-to-machine commerce.

Zero external dependencies. No Stripe. No third-party processors. You are the payment network.

## Install

```bash
npm install mppx
```

## Quick Start

```ts
import { Mppx, mppx, ledger } from 'mppx/server'

// Fund agent accounts
ledger.fund('agent-001', 100, 'usd')
ledger.fund('agent-002', 50, 'usd')

// Create the payment gateway
const gateway = Mppx.create({
  methods: [
    mppx.charge({ networkId: 'production' }),
  ],
})

// Protect any endpoint
export async function handler(request: Request) {
  const result = await gateway.charge({
    amount: '1',
    currency: 'usd',
    decimals: 2,
    description: 'Premium API access',
  })(request)

  if (result.status === 402) return result.challenge

  return result.withReceipt(Response.json({ data: '...' }))
}
```

That's it. No API keys. No webhook handlers. No third-party dashboards.

## Agent Client

```ts
import { MppClient } from 'mppx/client'

const agent = MppClient.create({
  agentId: 'agent-001',
  resolvePayment: async (challenge) => ({
    token: 'agent-001-auth-token',
  }),
})

// Automatic 402 handling
const { response, receipt, paid } = await agent.fetch(
  'https://api.example.com/search?q=hello'
)

console.log(paid)     // true
console.log(receipt)  // { receipt_id, amount, settled_at, ... }
```

## Ledger API

The built-in ledger gives you full account management:

```ts
import { ledger } from 'mppx/server'

// Account operations
ledger.fund('agent-001', 100, 'usd')
ledger.balance('agent-001')           // 100
ledger.account('agent-001')           // { id, balance, status, ... }
ledger.debit('agent-001', 5, 'usd')  // direct debit
ledger.transactions('agent-001')      // full tx history

// Two-phase (hold → settle/release)
const holdId = ledger.hold('agent-001', 10, 'usd')
ledger.settle(holdId)   // finalize
// or
ledger.release(holdId)  // cancel, refund hold

// Admin
ledger.accounts()  // list all accounts
ledger.reset()     // clear everything (testing)
```

## Two-Phase Settlement

For authorize-then-capture flows:

```ts
import { Mppx, mppx } from 'mppx/server'

const gateway = Mppx.create({
  methods: [
    mppx.authorize({ networkId: 'production' }),  // hold → settle
  ],
})
```

## Middleware Guard

```ts
const guard = gateway.protect({
  amount: '0.50',
  currency: 'usd',
  decimals: 2,
})

app.get('/api/search', async (req) => {
  const paywall = await guard(req)
  if (paywall) return paywall  // 402 or error

  return Response.json({ results: ['...'] })
})
```

## Discovery

```ts
// GET /.well-known/mpp
app.get('/.well-known/mpp', () => {
  return Response.json(gateway.discovery())
})
// → { version: "1.0", methods: [{ method: "ledger", network_id: "production" }] }
```

## Custom Payment Method

Implement `PaymentMethodAdapter` to plug in any settlement backend:

```ts
import type { PaymentMethodAdapter } from 'mppx'

const myRail: PaymentMethodAdapter = {
  name: 'my-rail',
  networkId: 'production',

  validate(credential) {
    return !!credential.payload.token
  },

  async settle(credential, request) {
    // Your settlement logic here
    const result = await myBackend.charge(credential.source, request.amount)

    return {
      receipt_id: result.id,
      challenge_id: credential.challenge.id,
      amount: request.amount,
      currency: request.currency,
      decimals: request.decimals,
      method: 'my-rail',
      settled_at: new Date().toISOString(),
      signature: result.sig,
    }
  },
}

const gateway = Mppx.create({ methods: [myRail] })
```

## Protocol Flow

```
Agent                              mppx Server
  │                                    │
  │─── GET /api/search ───────────────▶│
  │                                    │ (no credential)
  │◀── 402 Payment Required ──────────│
  │    WWW-Authenticate: Payment       │
  │    id="hmac-bound"                 │
  │    method="ledger"                 │
  │    request="<base64url>"           │
  │                                    │
  │  [Agent builds credential]         │
  │                                    │
  │─── GET /api/search ───────────────▶│
  │    Authorization: Payment <cred>   │
  │                                    │ ✓ HMAC verify
  │                                    │ ✓ Replay check
  │                                    │ ✓ Expiry check
  │                                    │ ✓ Ledger debit
  │◀── 200 OK ────────────────────────│
  │    Payment-Receipt: <base64url>    │
  │    { search results }              │
  │                                    │
```

## Architecture

| Module            | Purpose                              |
|-------------------|--------------------------------------|
| `Mppx`            | Core 402 flow orchestrator           |
| `challenge.ts`    | HMAC-SHA256 bound challenge gen      |
| `credential.ts`   | Parse/validate/encode credentials    |
| `receipt.ts`      | Receipt generation & verification    |
| `ledger.ts`       | Built-in double-entry ledger         |
| `MppClient`       | Agent-side auto-402 client           |

## Zero Dependencies

```
node:crypto  ← HMAC, UUID, randomBytes
node:buffer  ← base64url encoding
```

That's the entire dependency tree. Node built-ins only.

## License

MIT
