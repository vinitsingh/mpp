/**
 * MPP SDK Tests
 *
 * Run:  npx tsx --test tests/mppx.test.ts
 */
import { describe, it, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { Mppx } from '../src/mppx.js'
import { vault } from '../src/vault.js'
import { parseCredential, encodeCredential } from '../src/credential.js'
import { createReceiptService } from '../src/receipt.js'
import type { MppChallenge, MppCredential } from '../src/types.js'

// ─── Helpers ─────────────────────────────────────────────────

const SECRET = 'test-secret'

function makeRequest(url = 'http://localhost/api/search', headers?: Record<string, string>): Request {
  return new Request(url, { headers })
}

async function extractChallenge(response: Response): Promise<MppChallenge> {
  const body = await response.json()
  return body.challenge
}

function buildCredentialHeader(challenge: MppChallenge, token: string, source = 'agent-001'): string {
  const credential: MppCredential = {
    challenge,
    source,
    payload: { token },
  }
  return `Payment ${encodeCredential(credential)}`
}

// ─── Vault Tests ─────────────────────────────────────────────

describe('Vault', () => {
  beforeEach(() => vault.reset())

  it('should add and retrieve a card', () => {
    const card = vault.add({
      agent_id: 'agent-001',
      card_number: '4242424242424242',
      exp_month: 12,
      exp_year: 2028,
      cvc: '123',
    })

    assert.ok(card.id.startsWith('card_'))
    assert.equal(card.last4, '4242')
    assert.equal(card.brand, 'visa')
    assert.equal(card.agent_id, 'agent-001')

    const retrieved = vault.get(card.id)
    assert.deepEqual(retrieved, card)
  })

  it('should list cards for an agent', () => {
    vault.add({ agent_id: 'agent-001', card_number: '4242424242424242', exp_month: 12, exp_year: 2028, cvc: '123' })
    vault.add({ agent_id: 'agent-001', card_number: '5555555555554444', exp_month: 6, exp_year: 2027, cvc: '456' })
    vault.add({ agent_id: 'agent-002', card_number: '4111111111111111', exp_month: 3, exp_year: 2029, cvc: '789' })

    const agent1Cards = vault.list('agent-001')
    assert.equal(agent1Cards.length, 2)
    assert.equal(agent1Cards[0].brand, 'visa')
    assert.equal(agent1Cards[1].brand, 'mastercard')

    const agent2Cards = vault.list('agent-002')
    assert.equal(agent2Cards.length, 1)
  })

  it('should remove a card', () => {
    const card = vault.add({ agent_id: 'agent-001', card_number: '4242424242424242', exp_month: 12, exp_year: 2028, cvc: '123' })
    assert.ok(vault.remove(card.id))
    assert.equal(vault.get(card.id), null)
    assert.equal(vault.list('agent-001').length, 0)
  })

  it('should charge a valid card', () => {
    const card = vault.add({ agent_id: 'agent-001', card_number: '4242424242424242', exp_month: 12, exp_year: 2028, cvc: '123' })
    const result = vault.charge(card.id, 1.00, 'usd')
    assert.ok(result.success)
  })

  it('should reject charge on expired card', () => {
    const card = vault.add({ agent_id: 'agent-001', card_number: '4242424242424242', exp_month: 1, exp_year: 2020, cvc: '123' })
    const result = vault.charge(card.id, 1.00, 'usd')
    assert.equal(result.success, false)
    assert.equal(result.error, 'Card expired')
  })

  it('should reject charge on unknown card', () => {
    const result = vault.charge('card_nonexistent', 1.00, 'usd')
    assert.equal(result.success, false)
    assert.equal(result.error, 'Card not found')
  })

  it('should detect card brands', () => {
    const visa = vault.add({ agent_id: 'a', card_number: '4242424242424242', exp_month: 12, exp_year: 2028, cvc: '1' })
    const mc = vault.add({ agent_id: 'a', card_number: '5555555555554444', exp_month: 12, exp_year: 2028, cvc: '1' })
    const amex = vault.add({ agent_id: 'a', card_number: '378282246310005', exp_month: 12, exp_year: 2028, cvc: '1' })

    assert.equal(visa.brand, 'visa')
    assert.equal(mc.brand, 'mastercard')
    assert.equal(amex.brand, 'amex')
  })
})

// ─── Credential Tests ────────────────────────────────────────

describe('Credential', () => {
  it('should parse valid credential with token', () => {
    const cred: MppCredential = {
      challenge: {
        id: 'test.hmac',
        realm: 'test',
        method: 'card',
        intent: 'charge',
        request: 'base64request',
        expires: new Date(Date.now() + 300_000).toISOString(),
      },
      source: 'agent-001',
      payload: { token: 'tok_abc123' },
    }

    const encoded = encodeCredential(cred)
    const parsed = parseCredential(`Payment ${encoded}`)

    assert.ok(parsed)
    assert.equal(parsed.source, 'agent-001')
    assert.equal(parsed.payload.token, 'tok_abc123')
  })

  it('should reject credential without token', () => {
    const cred = {
      challenge: { id: 'x', realm: 'x', method: 'x', intent: 'charge', request: 'x', expires: 'x' },
      source: 'agent-001',
      payload: {},  // no token
    }
    const encoded = Buffer.from(JSON.stringify(cred)).toString('base64url')
    assert.equal(parseCredential(`Payment ${encoded}`), null)
  })

  it('should reject malformed header', () => {
    assert.equal(parseCredential('Bearer xyz'), null)
    assert.equal(parseCredential(''), null)
  })
})

// ─── Receipt Tests ───────────────────────────────────────────

describe('Receipt', () => {
  it('should generate and verify', () => {
    const service = createReceiptService(SECRET)
    const receipt = service.generate('challenge-123', 'card', {
      amount: '1', currency: 'usd', decimals: 2,
    })

    assert.ok(receipt.receipt_id)
    assert.equal(receipt.method, 'card')
    assert.ok(service.verify(receipt))
  })

  it('should reject tampered receipt', () => {
    const service = createReceiptService(SECRET)
    const receipt = service.generate('challenge-123', 'card', {
      amount: '1', currency: 'usd', decimals: 2,
    })
    receipt.amount = '999'
    assert.ok(!service.verify(receipt))
  })
})

// ─── Full 402 Flow Tests ─────────────────────────────────────

describe('Mppx — Full Flow', () => {
  let gateway: Mppx
  let cardId: string

  beforeEach(() => {
    vault.reset()

    // Save a test card
    const card = vault.add({
      agent_id: 'agent-001',
      card_number: '4242424242424242',
      exp_month: 12,
      exp_year: 2028,
      cvc: '123',
    })
    cardId = card.id

    gateway = Mppx.create({
      secret: SECRET,
      realm: 'test',
      challengeTtlSeconds: 300,
    })
  })

  it('should return 402 when no credential', async () => {
    const handler = gateway.charge({ amount: '1', currency: 'usd', decimals: 2 })
    const result = await handler(makeRequest())

    assert.equal(result.status, 402)
    assert.equal(result.receipt, null)

    const body = await result.challenge.json()
    assert.equal(body.status, 402)
    assert.ok(body.challenge.id)
    assert.equal(body.challenge.method, 'card')

    const wwwAuth = result.challenge.headers.get('WWW-Authenticate')
    assert.ok(wwwAuth?.startsWith('Payment'))
  })

  it('should accept valid token from /charges and return receipt', async () => {
    const handler = gateway.charge({ amount: '1', currency: 'usd', decimals: 2 })

    // Step 1: Get challenge
    const challengeResult = await handler(makeRequest())
    const challenge = await extractChallenge(challengeResult.challenge)

    // Step 2: Call charges to get a one-time token
    const chargeResult = gateway.charges.create({ challenge, card_id: cardId })
    assert.ok(chargeResult.token.startsWith('tok_'))
    assert.equal(chargeResult.amount, '1')

    // Step 3: Retry with token
    const result = await handler(makeRequest('http://localhost/api/search', {
      Authorization: buildCredentialHeader(challenge, chargeResult.token),
    }))

    assert.equal(result.status, 200)
    assert.ok(result.receipt)
    assert.equal(result.receipt.amount, '1')
    assert.equal(result.receipt.method, 'card')
    assert.ok(result.receipt.signature)
  })

  it('should attach receipt to downstream response', async () => {
    const handler = gateway.charge({ amount: '0.50', currency: 'usd', decimals: 2 })

    const challengeResult = await handler(makeRequest())
    const challenge = await extractChallenge(challengeResult.challenge)
    const { token } = gateway.charges.create({ challenge, card_id: cardId })

    const result = await handler(makeRequest('http://localhost/api', {
      Authorization: buildCredentialHeader(challenge, token),
    }))

    const downstream = Response.json({ data: 'hello' })
    const final = result.withReceipt(downstream)

    assert.ok(final.headers.get('Payment-Receipt'))
    const body = await final.json()
    assert.equal(body.data, 'hello')
  })

  it('should reject replayed token', async () => {
    const handler = gateway.charge({ amount: '0.01', currency: 'usd', decimals: 2 })

    const challengeResult = await handler(makeRequest())
    const challenge = await extractChallenge(challengeResult.challenge)
    const { token } = gateway.charges.create({ challenge, card_id: cardId })
    const authHeader = buildCredentialHeader(challenge, token)

    // First use: success
    const result1 = await handler(makeRequest('http://localhost/api', { Authorization: authHeader }))
    assert.equal(result1.status, 200)

    // Replay: should fail (token consumed + challenge used)
    const result2 = await handler(makeRequest('http://localhost/api', { Authorization: authHeader }))
    assert.notEqual(result2.status, 200)
  })

  it('should reject invalid token', async () => {
    const handler = gateway.charge({ amount: '1', currency: 'usd', decimals: 2 })

    const challengeResult = await handler(makeRequest())
    const challenge = await extractChallenge(challengeResult.challenge)

    // Use a fake token
    const result = await handler(makeRequest('http://localhost/api', {
      Authorization: buildCredentialHeader(challenge, 'tok_fake.invalid'),
    }))

    assert.equal(result.status, 403)
    const body = await result.challenge.json()
    assert.equal(body.type, 'urn:mpp:error:invalid-token')
  })

  it('should reject charge with non-existent card', () => {
    const handler = gateway.charge({ amount: '1', currency: 'usd', decimals: 2 })

    // Get a challenge first (sync-ish via the charge service)
    const challenge: MppChallenge = {
      id: 'test.hmac',
      realm: 'test',
      method: 'card',
      intent: 'charge',
      request: Buffer.from(JSON.stringify({ amount: '1', currency: 'usd', decimals: 2 })).toString('base64url'),
      expires: new Date(Date.now() + 300_000).toISOString(),
    }

    assert.throws(
      () => gateway.charges.create({ challenge, card_id: 'card_nonexistent' }),
      { name: 'ChargeError' }
    )
  })

  it('should reject charge with expired card', () => {
    const expiredCard = vault.add({
      agent_id: 'agent-001',
      card_number: '4111111111111111',
      exp_month: 1,
      exp_year: 2020,
      cvc: '123',
    })

    const challenge: MppChallenge = {
      id: 'test.hmac',
      realm: 'test',
      method: 'card',
      intent: 'charge',
      request: Buffer.from(JSON.stringify({ amount: '1', currency: 'usd', decimals: 2 })).toString('base64url'),
      expires: new Date(Date.now() + 300_000).toISOString(),
    }

    assert.throws(
      () => gateway.charges.create({ challenge, card_id: expiredCard.id }),
      { name: 'ChargeError' }
    )
  })

  it('should reject malformed credential', async () => {
    const handler = gateway.charge({ amount: '1', currency: 'usd', decimals: 2 })

    const result = await handler(makeRequest('http://localhost/api', {
      Authorization: 'Payment bm90LXZhbGlk',
    }))

    assert.equal(result.status, 400)
    const body = await result.challenge.json()
    assert.equal(body.type, 'urn:mpp:error:invalid-credential')
  })
})

describe('Mppx — protect() middleware', () => {
  beforeEach(() => vault.reset())

  it('should return 402 Response when no credential', async () => {
    const gateway = Mppx.create({ secret: SECRET })
    const guard = gateway.protect({ amount: '0.01', currency: 'usd', decimals: 2 })

    const blocked = await guard(makeRequest())
    assert.ok(blocked instanceof Response)
    assert.equal(blocked.status, 402)
  })
})

describe('Mppx — discovery()', () => {
  it('should return discovery with charges endpoint', () => {
    const gateway = Mppx.create()
    const doc = gateway.discovery()

    assert.equal(doc.version, '1.0')
    const endpoints = doc.endpoints as Record<string, string>
    assert.equal(endpoints.charges, '/charges')
    assert.equal(endpoints.vault, '/vault/cards')
  })
})
