// ─── MPP Wire Types ────────────────────────────────────────

export interface MppChallenge {
  id: string
  realm: string
  method: string
  intent: 'charge' | 'authorize' | 'subscribe'
  request: string          // base64url-encoded PaymentRequest
  expires: string          // ISO 8601
}

export interface PaymentRequest {
  amount: string
  currency: string
  decimals: number
  description?: string
  metadata?: Record<string, string>
}

export interface MppCredential {
  challenge: MppChallenge
  source: string
  payload: {
    token: string          // one-time token from /charges
    [key: string]: unknown
  }
}

export interface MppReceipt {
  receipt_id: string
  challenge_id: string
  amount: string
  currency: string
  decimals: number
  method: string
  settled_at: string
  signature: string
}

// ─── Charges API Types ─────────────────────────────────────

export interface ChargeRequest {
  /** The challenge received from the 402 response */
  challenge: MppChallenge
  /** Saved card ID from the vault */
  card_id: string
}

export interface ChargeResponse {
  /** One-time payment token to use in the Authorization header */
  token: string
  /** Amount charged */
  amount: string
  currency: string
  /** Token expiry */
  expires: string
}

// ─── Vault Types ───────────────────────────────────────────

export interface SavedCard {
  id: string
  agent_id: string
  last4: string
  brand: string
  exp_month: number
  exp_year: number
  created_at: string
}

export interface VaultAddRequest {
  agent_id: string
  card_number: string
  exp_month: number
  exp_year: number
  cvc: string
}

// ─── SDK Configuration ────────────────────────────────────

export interface ChargeParams {
  amount: string
  currency: string
  decimals: number
  description?: string
  metadata?: Record<string, string>
}

export interface ChargeResult {
  status: 200 | 402 | 400 | 403 | 500
  challenge: Response
  receipt: MppReceipt | null
  withReceipt: (downstream: Response) => Response
}

export interface MppxConfig {
  /** HMAC secret for challenge binding (auto-generated if omitted) */
  secret?: string

  /** Challenge expiry in seconds (default: 300) */
  challengeTtlSeconds?: number

  /** Realm for WWW-Authenticate header */
  realm?: string
}
