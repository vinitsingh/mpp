import { randomUUID } from 'node:crypto'
import type { SavedCard, VaultAddRequest } from './types.js'

const cards = new Map<string, SavedCard>()

// Index: agent_id → card_ids
const agentCards = new Map<string, Set<string>>()

export const vault = {
  /** Save a card to the vault. Returns the saved card (with tokenized ID, no raw numbers). */
  add(req: VaultAddRequest): SavedCard {
    const card: SavedCard = {
      id: `card_${randomUUID().replace(/-/g, '').slice(0, 16)}`,
      agent_id: req.agent_id,
      last4: req.card_number.slice(-4),
      brand: detectBrand(req.card_number),
      exp_month: req.exp_month,
      exp_year: req.exp_year,
      created_at: new Date().toISOString(),
    }

    cards.set(card.id, card)

    if (!agentCards.has(req.agent_id)) {
      agentCards.set(req.agent_id, new Set())
    }
    agentCards.get(req.agent_id)!.add(card.id)

    return card
  },

  /** Get a saved card by ID */
  get(cardId: string): SavedCard | null {
    return cards.get(cardId) ?? null
  },

  /** List all cards for an agent */
  list(agentId: string): SavedCard[] {
    const cardIds = agentCards.get(agentId)
    if (!cardIds) return []
    return Array.from(cardIds)
      .map(id => cards.get(id)!)
      .filter(Boolean)
  },

  /** Remove a card from the vault */
  remove(cardId: string): boolean {
    const card = cards.get(cardId)
    if (!card) return false
    cards.delete(cardId)
    agentCards.get(card.agent_id)?.delete(cardId)
    return true
  },

  /** Simulate charging a saved card. Returns true if the card is valid and charge succeeds. */
  charge(cardId: string, amount: number, currency: string): { success: boolean; error?: string } {
    const card = cards.get(cardId)
    if (!card) return { success: false, error: 'Card not found' }

    // Check expiry
    const now = new Date()
    const expiry = new Date(card.exp_year, card.exp_month - 1)
    if (expiry < now) return { success: false, error: 'Card expired' }

    // In production: call your card processor here
    // For now: simulated success
    return { success: true }
  },

  /** Reset (testing) */
  reset(): void {
    cards.clear()
    agentCards.clear()
  },
}

function detectBrand(number: string): string {
  const n = number.replace(/\s/g, '')
  if (n.startsWith('4')) return 'visa'
  if (/^5[1-5]/.test(n)) return 'mastercard'
  if (/^3[47]/.test(n)) return 'amex'
  if (n.startsWith('6011') || n.startsWith('65')) return 'discover'
  return 'unknown'
}
