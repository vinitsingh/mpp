import { createHmac, randomUUID } from 'node:crypto'
import type { MppReceipt, PaymentRequest } from './types.js'

export function createReceiptService(secret: string) {
  function generate(
    challengeId: string,
    method: string,
    request: PaymentRequest,
  ): MppReceipt {
    const receipt: Omit<MppReceipt, 'signature'> = {
      receipt_id: randomUUID(),
      challenge_id: challengeId,
      amount: request.amount,
      currency: request.currency,
      decimals: request.decimals,
      method,
      settled_at: new Date().toISOString(),
    }

    const signature = createHmac('sha256', secret)
      .update(JSON.stringify(receipt))
      .digest('base64url')

    return { ...receipt, signature }
  }

  function encode(receipt: MppReceipt): string {
    return Buffer.from(JSON.stringify(receipt)).toString('base64url')
  }

  function verify(receipt: MppReceipt): boolean {
    const { signature, ...rest } = receipt
    const expected = createHmac('sha256', secret)
      .update(JSON.stringify(rest))
      .digest('base64url')
    return signature === expected
  }

  return { generate, encode, verify }
}
