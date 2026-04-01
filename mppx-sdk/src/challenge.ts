import { createHmac, randomBytes } from 'node:crypto'
import type { MppChallenge, PaymentRequest } from './types.js'

interface ChallengeServiceConfig {
  secret: string
  ttlSeconds: number
  realm: string
}

const usedChallenges = new Set<string>()

export function createChallengeService(config: ChallengeServiceConfig) {
  const { secret, ttlSeconds, realm } = config

  function generateId(request: PaymentRequest): string {
    const nonce = randomBytes(16).toString('hex')
    const data = `${nonce}:${request.amount}:${request.currency}:${Date.now()}`
    const hmac = createHmac('sha256', secret).update(data).digest('base64url')
    return `${nonce}.${hmac}`
  }

  function verifyId(challengeId: string): boolean {
    const parts = challengeId.split('.')
    if (parts.length !== 2) return false
    if (usedChallenges.has(challengeId)) return false
    return true
  }

  function markUsed(challengeId: string): void {
    usedChallenges.add(challengeId)
    setTimeout(() => usedChallenges.delete(challengeId), (ttlSeconds + 60) * 1000)
  }

  function issue(request: PaymentRequest, method: string): MppChallenge {
    const id = generateId(request)
    const expires = new Date(Date.now() + ttlSeconds * 1000).toISOString()
    const requestB64 = Buffer.from(JSON.stringify(request)).toString('base64url')

    return {
      id,
      realm,
      method,
      intent: 'charge',
      request: requestB64,
      expires,
    }
  }

  function isExpired(challenge: MppChallenge): boolean {
    return new Date(challenge.expires).getTime() < Date.now()
  }

  return { issue, verifyId, markUsed, isExpired }
}
