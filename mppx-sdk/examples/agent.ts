/**
 * MPP Agent Client Example
 *
 * Run the server first:  npx tsx examples/server.ts
 * Then run this:          npx tsx examples/agent.ts
 */
import { MppClient } from '../src/client.js'

const SERVER = 'http://localhost:3402'

async function main() {
  console.log('='.repeat(60))
  console.log('MPP Agent Client Demo')
  console.log('='.repeat(60))
  console.log()

  // Step 1: Save a card to the vault
  console.log('▶ Step 1: Save a card to the vault')
  const cardRes = await fetch(`${SERVER}/vault/cards`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      agent_id: 'agent-001',
      card_number: '4242424242424242',
      exp_month: 12,
      exp_year: 2028,
      cvc: '123',
    }),
  })
  const card = await cardRes.json()
  console.log('  Saved:', card)
  console.log()

  // Step 2: Create an MPP client with the saved card
  console.log('▶ Step 2: Create MPP client')
  const agent = MppClient.create({
    agentId: 'agent-001',
    cardId: card.id,
    serverUrl: SERVER,
  })
  console.log('  Agent ready with card:', card.id)
  console.log()

  // Step 3: Make a paid API call (full auto: 402 → /charges → retry → 200)
  console.log('▶ Step 3: Paid search (auto 402 → charge card → get token → retry → 200)')
  const { response, receipt, paid } = await agent.fetch('/api/search?q=hello')

  console.log('  Status:', response.status)
  console.log('  Paid:', paid)
  if (receipt) {
    console.log('  Receipt:')
    console.log('    receipt_id:', receipt.receipt_id)
    console.log('    amount:', receipt.amount, receipt.currency)
    console.log('    method:', receipt.method)
  }
  const body = await response.json()
  console.log('  Body:', body)
  console.log()

  // Step 4: Make another call — premium
  console.log('▶ Step 4: Paid premium access ($1.00)')
  const { response: r2, receipt: r2r } = await agent.fetch('/api/premium')
  console.log('  Status:', r2.status)
  console.log('  Receipt:', r2r?.receipt_id?.slice(0, 8) + '...', 'amount:', r2r?.amount)
  console.log('  Body:', await r2.json())
  console.log()

  // Step 5: Multiple search calls
  console.log('▶ Step 5: Multiple paid calls')
  for (let i = 0; i < 3; i++) {
    const { receipt: r } = await agent.fetch(`/api/search?q=query-${i}`)
    console.log(`  Call ${i + 1}: receipt=${r?.receipt_id.slice(0, 8)}... amount=${r?.amount}`)
  }
  console.log()

  console.log('='.repeat(60))
  console.log('Done.')
}

main().catch(console.error)
