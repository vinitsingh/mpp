/**
 * MPP Server Example
 *
 * Run:  npx tsx examples/server.ts
 *
 * Flow:
 *   1. Agent saves a card:       POST /vault/cards
 *   2. Agent hits paid endpoint:  GET /api/search  → gets 402 + challenge
 *   3. Agent charges card:        POST /charges    → gets one-time token
 *   4. Agent retries with token:  GET /api/search  → gets 200 + receipt
 */
import { createServer } from 'node:http'
import { Mppx, vault } from '../src/server.js'

// ─── Create the gateway ──────────────────────────────────────
const gateway = Mppx.create({
  realm: 'example-api',
  challengeTtlSeconds: 300,
})

// ─── Route handlers ──────────────────────────────────────────

/** POST /vault/cards — save a card */
async function handleVaultAdd(body: any): Promise<Response> {
  try {
    const card = vault.add({
      agent_id: body.agent_id,
      card_number: body.card_number,
      exp_month: body.exp_month,
      exp_year: body.exp_year,
      cvc: body.cvc,
    })
    return Response.json(card, { status: 201 })
  } catch (err) {
    return Response.json({ error: (err as Error).message }, { status: 400 })
  }
}

/** GET /vault/cards/:agent_id — list saved cards */
function handleVaultList(agentId: string): Response {
  return Response.json(vault.list(agentId))
}

/** POST /charges — charge a saved card, get a one-time token */
async function handleCharge(body: any): Promise<Response> {
  try {
    const result = gateway.charges.create({
      challenge: body.challenge,
      card_id: body.card_id,
    })
    return Response.json(result)
  } catch (err: any) {
    const status = err.code === 'CHARGE_FAILED' ? 402 : 400
    return Response.json(
      { type: `urn:mpp:error:${err.code?.toLowerCase() ?? 'charge-failed'}`, title: err.message, status },
      { status },
    )
  }
}

/** GET /api/search — paid endpoint ($0.01) */
async function handleSearch(request: Request): Promise<Response> {
  const result = await gateway.charge({
    amount: '0.01',
    currency: 'usd',
    decimals: 2,
    description: 'Search API call',
  })(request)

  if (result.status !== 200) return result.challenge

  return result.withReceipt(
    Response.json({
      results: [
        { id: 1, title: 'First result' },
        { id: 2, title: 'Second result' },
      ],
      _payment: {
        receipt_id: result.receipt?.receipt_id,
        amount: result.receipt?.amount,
        currency: result.receipt?.currency,
      },
    })
  )
}

/** GET /api/premium — paid endpoint ($1.00) */
async function handlePremium(request: Request): Promise<Response> {
  const result = await gateway.charge({
    amount: '1',
    currency: 'usd',
    decimals: 2,
    description: 'Premium content access',
  })(request)

  if (result.status !== 200) return result.challenge

  return result.withReceipt(
    Response.json({
      content: 'This is premium content worth paying for.',
      _payment: {
        receipt_id: result.receipt?.receipt_id,
        amount: result.receipt?.amount,
      },
    })
  )
}

/** GET /.well-known/mpp — discovery */
function handleDiscovery(): Response {
  return Response.json(gateway.discovery())
}

// ─── HTTP server ─────────────────────────────────────────────

async function route(req: Request, url: URL): Promise<Response> {
  const path = url.pathname
  const method = req.method

  if (path === '/.well-known/mpp') return handleDiscovery()
  if (path === '/api/search') return handleSearch(req)
  if (path === '/api/premium') return handlePremium(req)

  if (path === '/charges' && method === 'POST') {
    const body = await req.json()
    return handleCharge(body)
  }

  if (path === '/vault/cards' && method === 'POST') {
    const body = await req.json()
    return handleVaultAdd(body)
  }

  if (path.startsWith('/vault/cards/') && method === 'GET') {
    const agentId = path.split('/vault/cards/')[1]
    return handleVaultList(agentId)
  }

  return Response.json({ error: 'Not found' }, { status: 404 })
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url!, `http://${req.headers.host}`)
  const headers = new Headers()
  for (const [key, value] of Object.entries(req.headers)) {
    if (value) headers.set(key, Array.isArray(value) ? value[0] : value)
  }

  // Read body for POST requests
  let bodyText = ''
  if (req.method === 'POST') {
    bodyText = await new Promise<string>((resolve) => {
      let data = ''
      req.on('data', (chunk) => { data += chunk })
      req.on('end', () => resolve(data))
    })
  }

  const request = new Request(url.toString(), {
    method: req.method,
    headers,
    body: req.method === 'POST' ? bodyText : undefined,
  })

  const response = await route(request, url)

  res.writeHead(response.status, Object.fromEntries(response.headers.entries()))
  const body = await response.text()
  res.end(body)
})

server.listen(3402, () => {
  console.log('MPP Server running on http://localhost:3402')
  console.log()
  console.log('Endpoints:')
  console.log('  GET  /.well-known/mpp        → Discovery')
  console.log('  POST /vault/cards             → Save a card')
  console.log('  GET  /vault/cards/:agent_id   → List saved cards')
  console.log('  POST /charges                 → Charge card, get one-time token')
  console.log('  GET  /api/search              → Paid endpoint ($0.01)')
  console.log('  GET  /api/premium             → Paid endpoint ($1.00)')
})
