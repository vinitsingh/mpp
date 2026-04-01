import type { MppCredential } from './types.js'

export function parseCredential(authHeader: string): MppCredential | null {
  const match = authHeader.match(/^Payment\s+(.+)$/i)
  if (!match) return null

  try {
    const decoded = Buffer.from(match[1], 'base64url').toString('utf-8')
    const credential = JSON.parse(decoded) as MppCredential

    if (!credential.challenge?.id) return null
    if (!credential.challenge?.method) return null
    if (!credential.source) return null
    if (!credential.payload?.token) return null

    return credential
  } catch {
    return null
  }
}

export function encodeCredential(credential: MppCredential): string {
  return Buffer.from(JSON.stringify(credential)).toString('base64url')
}
