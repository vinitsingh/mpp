import { randomBytes, createHmac } from 'node:crypto'
import type { MppChallenge, ChargeRequest, ChargeResponse, PaymentRequest } from './types.js'
import { vault } from './vault.js'

interface ChargesConfig {
  secret: string
  tokenTtlSeconds: number
}

// Store of valid one-time tokens: token → { challengeId, amount, currency, expiresAt }
const validTokens = new Map<string, {
  challengeId: string
  cardId: string
  amount: string
  currency: string
  decimals: number
  expiresAt: number
}>()

export function createChargesService(config: ChargesConfig) {
  const { secret, tokenTtlSeconds } = config

  /**
   * Process a charge request:
   * 1. Validate the saved card exists
   * 2. Charge the card via the vault
   * 3. Generate a one-time token bound to this challenge
   */
  function create(req: ChargeRequest): ChargeResponse {
    const { challenge, card_id } = req

    // Decode the payment request from the challenge
    const paymentRequest: PaymentRequest = JSON.parse(
      Buffer.from(challenge.request, 'base64url').toString('utf-8')
    )

    // Charge the saved card
    const amount = parseFloat(paymentRequest.amount)
    const result = vault.charge(card_id, amount, paymentRequest.currency)

    if (!result.success) {
      throw new ChargeError(result.error ?? 'Charge failed', 'CHARGE_FAILED')
    }

    // Generate one-time token bound to this challenge
    const tokenData = randomBytes(32).toString('hex')
    const hmac = createHmac('sha256', secret)
      .update(`${tokenData}:${challenge.id}:${paymentRequest.amount}`)
      .digest('base64url')
    const token = `tok_${tokenData.slice(0, 16)}.${hmac.slice(0, 16)}`

    const expiresAt = Date.now() + tokenTtlSeconds * 1000

    // Store the token
    validTokens.set(token, {
      challengeId: challenge.id,
      cardId: card_id,
      amount: paymentRequest.amount,
      currency: paymentRequest.currency,
      decimals: paymentRequest.decimals,
      expiresAt,
    })

    // Auto-cleanup after expiry
    setTimeout(() => validTokens.delete(token), (tokenTtlSeconds + 10) * 1000)

    return {
      token,
      amount: paymentRequest.amount,
      currency: paymentRequest.currency,
      expires: new Date(expiresAt).toISOString(),
    }
  }

  /**
   * Validate and consume a one-time token.
   * Returns the token data if valid, null if invalid/expired/already used.
   */
  function consume(token: string, challengeId: string): {
    cardId: string
    amount: string
    currency: string
    decimals: number
  } | null {
    const data = validTokens.get(token)
    if (!data) return null

    // Check expiry
    if (data.expiresAt < Date.now()) {
      validTokens.delete(token)
      return null
    }

    // Check challenge binding — token is bound to the specific challenge
    if (data.challengeId !== challengeId) return null

    // Consume — one-time use
    validTokens.delete(token)

    return {
      cardId: data.cardId,
      amount: data.amount,
      currency: data.currency,
      decimals: data.decimals,
    }
  }

  return { create, consume }
}

export class ChargeError extends Error {
  constructor(
    message: string,
    public readonly code: string,
  ) {
    super(message)
    this.name = 'ChargeError'
  }
}
