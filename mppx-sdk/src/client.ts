import type { MppChallenge, MppCredential, MppReceipt } from './types.js'
import { encodeCredential } from './credential.js'

interface MppClientConfig {
  /** Agent identifier */
  agentId: string

  /** Saved card ID in the vault */
  cardId: string

  /** Base URL of the MPP server (for calling /charges) */
  serverUrl: string

  /** Max retries after 402 (default: 1) */
  maxRetries?: number
}

interface MppFetchResult {
  response: Response
  receipt: MppReceipt | null
  paid: boolean
}

/**
 * Agent-side client that auto-handles the full 402 flow:
 * 1. Make request → get 402 with challenge
 * 2. POST /charges with challenge + card_id → get one-time token
 * 3. Retry request with token in Authorization header → get 200 + receipt
 *
 * ```ts
 * const agent = MppClient.create({
 *   agentId: 'agent-001',
 *   cardId: 'card_abc123',
 *   serverUrl: 'http://localhost:3402',
 * })
 *
 * const { response, receipt } = await agent.fetch('/api/search?q=hello')
 * ```
 */
export class MppClient {
  private config: Required<MppClientConfig>

  private constructor(config: MppClientConfig) {
    this.config = {
      ...config,
      maxRetries: config.maxRetries ?? 1,
    }
  }

  static create(config: MppClientConfig): MppClient {
    return new MppClient(config)
  }

  async fetch(path: string, init?: RequestInit): Promise<MppFetchResult> {
    const url = `${this.config.serverUrl}${path}`
    let response = await fetch(url, init)

    for (let attempt = 0; attempt < this.config.maxRetries && response.status === 402; attempt++) {
      const challenge = await this.extractChallenge(response)
      if (!challenge) break

      // Call /charges to get a one-time token
      const token = await this.chargeCard(challenge)
      if (!token) break

      // Build credential with the token
      const credential: MppCredential = {
        challenge,
        source: this.config.agentId,
        payload: { token },
      }

      const headers = new Headers(init?.headers)
      headers.set('Authorization', `Payment ${encodeCredential(credential)}`)

      response = await fetch(url, { ...init, headers })
    }

    // Extract receipt
    const receiptHeader = response.headers.get('Payment-Receipt')
    let receipt: MppReceipt | null = null
    if (receiptHeader) {
      try {
        receipt = JSON.parse(Buffer.from(receiptHeader, 'base64url').toString('utf-8'))
      } catch { /* continue */ }
    }

    return { response, receipt, paid: receipt !== null }
  }

  /** Call POST /charges to charge the saved card and get a one-time token */
  private async chargeCard(challenge: MppChallenge): Promise<string | null> {
    try {
      const res = await fetch(`${this.config.serverUrl}/charges`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          challenge,
          card_id: this.config.cardId,
        }),
      })

      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        console.error('Charge failed:', err)
        return null
      }

      const data = await res.json()
      return data.token
    } catch (err) {
      console.error('Charge request failed:', err)
      return null
    }
  }

  private async extractChallenge(response: Response): Promise<MppChallenge | null> {
    try {
      const body = await response.clone().json()
      if (body.challenge) return body.challenge as MppChallenge
    } catch { /* fallback */ }

    const wwwAuth = response.headers.get('WWW-Authenticate')
    if (!wwwAuth) return null
    return this.parseWwwAuthenticate(wwwAuth)
  }

  private parseWwwAuthenticate(header: string): MppChallenge | null {
    const extract = (key: string): string | undefined => {
      const match = header.match(new RegExp(`${key}="([^"]*)"`, 'i'))
      return match?.[1]
    }

    const id = extract('id')
    const realm = extract('realm')
    const method = extract('method')
    const intent = extract('intent') as MppChallenge['intent']
    const request = extract('request')
    const expires = extract('expires')

    if (!id || !realm || !method || !intent || !request || !expires) return null
    return { id, realm, method, intent, request, expires }
  }
}
