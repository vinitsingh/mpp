import { randomBytes } from 'node:crypto'
import type {
  MppxConfig,
  ChargeParams,
  ChargeResult,
  PaymentRequest,
  MppChallenge,
} from './types.js'
import { createChallengeService } from './challenge.js'
import { parseCredential } from './credential.js'
import { createReceiptService } from './receipt.js'
import { createChargesService } from './charges.js'

export class Mppx {
  private challengeService: ReturnType<typeof createChallengeService>
  private receiptService: ReturnType<typeof createReceiptService>
  private chargesService: ReturnType<typeof createChargesService>
  private secret: string

  private constructor(config: MppxConfig) {
    this.secret = config.secret ?? randomBytes(32).toString('hex')
    const ttl = config.challengeTtlSeconds ?? 300
    const realm = config.realm ?? 'mpp'

    this.challengeService = createChallengeService({
      secret: this.secret,
      ttlSeconds: ttl,
      realm,
    })
    this.receiptService = createReceiptService(this.secret)
    this.chargesService = createChargesService({
      secret: this.secret,
      tokenTtlSeconds: ttl,
    })
  }

  static create(config: MppxConfig = {}): Mppx {
    return new Mppx(config)
  }

  /** Access the charges service (for the /charges route handler) */
  get charges() {
    return this.chargesService
  }

  /**
   * Create a charge handler for a specific price point.
   *
   * ```ts
   * const result = await gateway.charge({
   *   amount: '1', currency: 'usd', decimals: 2,
   * })(request)
   *
   * if (result.status === 402) return result.challenge
   * return result.withReceipt(Response.json({ data: '...' }))
   * ```
   */
  charge(params: ChargeParams): (request: Request) => Promise<ChargeResult> {
    const paymentRequest: PaymentRequest = {
      amount: params.amount,
      currency: params.currency,
      decimals: params.decimals,
      description: params.description,
      metadata: params.metadata,
    }

    return async (request: Request): Promise<ChargeResult> => {
      const authHeader = request.headers.get('authorization')

      // ─── No credential → 402 challenge ─────────────────────
      if (!authHeader || !authHeader.toLowerCase().startsWith('payment ')) {
        return this.issueChallenge(paymentRequest)
      }

      // ─── Parse credential ──────────────────────────────────
      const credential = parseCredential(authHeader)
      if (!credential) {
        return this.errorResult(400, 'urn:mpp:error:invalid-credential', 'Malformed payment credential')
      }

      // ─── Validate challenge ────────────────────────────────
      if (!this.challengeService.verifyId(credential.challenge.id)) {
        return this.errorResult(403, 'urn:mpp:error:invalid-challenge', 'Invalid or replayed challenge')
      }

      if (this.challengeService.isExpired(credential.challenge)) {
        return this.errorResult(403, 'urn:mpp:error:expired-challenge', 'Challenge has expired')
      }

      // ─── Validate one-time token ───────────────────────────
      const tokenData = this.chargesService.consume(
        credential.payload.token,
        credential.challenge.id,
      )

      if (!tokenData) {
        return this.errorResult(403, 'urn:mpp:error:invalid-token', 'Invalid, expired, or already-used payment token')
      }

      // ─── Amount match check ────────────────────────────────
      if (tokenData.amount !== paymentRequest.amount || tokenData.currency !== paymentRequest.currency) {
        return this.errorResult(400, 'urn:mpp:error:amount-mismatch', 'Token amount does not match request')
      }

      // ─── Success — mark challenge used, issue receipt ──────
      this.challengeService.markUsed(credential.challenge.id)

      const receipt = this.receiptService.generate(
        credential.challenge.id,
        'card',
        paymentRequest,
      )
      const receiptB64 = this.receiptService.encode(receipt)

      return {
        status: 200,
        challenge: new Response(null, { status: 200 }),
        receipt,
        withReceipt(downstream: Response): Response {
          const headers = new Headers(downstream.headers)
          headers.set('Payment-Receipt', receiptB64)
          return new Response(downstream.body, {
            status: downstream.status,
            statusText: downstream.statusText,
            headers,
          })
        },
      }
    }
  }

  /**
   * Middleware guard — returns null if payment succeeded, Response otherwise.
   */
  protect(params: ChargeParams) {
    const handler = this.charge(params)
    return async (request: Request): Promise<Response | null> => {
      const result = await handler(request)
      if (result.status !== 200) return result.challenge
      return null
    }
  }

  /** Generate /.well-known/mpp discovery document */
  discovery(): Record<string, unknown> {
    return {
      version: '1.0',
      methods: [{ method: 'card', type: 'vault' }],
      endpoints: {
        charges: '/charges',
        vault: '/vault/cards',
      },
    }
  }

  // ─── Internals ─────────────────────────────────────────────

  private issueChallenge(request: PaymentRequest): ChargeResult {
    const challenge = this.challengeService.issue(request, 'card')
    const wwwAuth = this.formatWwwAuthenticate(challenge)

    return {
      status: 402,
      challenge: new Response(
        JSON.stringify({
          type: 'urn:mpp:error:payment-required',
          title: 'Payment Required',
          status: 402,
          challenge,
        }),
        {
          status: 402,
          headers: {
            'Content-Type': 'application/problem+json',
            'WWW-Authenticate': wwwAuth,
          },
        },
      ),
      receipt: null,
      withReceipt: (r) => r,
    }
  }

  private formatWwwAuthenticate(challenge: MppChallenge): string {
    return [
      `Payment realm="${challenge.realm}"`,
      `id="${challenge.id}"`,
      `method="${challenge.method}"`,
      `intent="${challenge.intent}"`,
      `request="${challenge.request}"`,
      `expires="${challenge.expires}"`,
    ].join(', ')
  }

  private errorResult(status: 400 | 402 | 403 | 500, type: string, title: string): ChargeResult {
    return {
      status,
      challenge: new Response(
        JSON.stringify({ type, title, status }),
        { status, headers: { 'Content-Type': 'application/problem+json' } },
      ),
      receipt: null,
      withReceipt: (r) => r,
    }
  }
}
