# Tebex rank delivery (staging)

Configure the existing VIP and MVP groups first using MineNightPerms. The plugin does not change their design. Configure four separate products: VIP monthly EUR 4.99, MVP monthly EUR 9.99, VIP lifetime EUR 20, MVP lifetime EUR 30.

Use package-level commands in the official Tebex Minecraft plugin. Disable "Require the customer to be online". The documented universal placeholders are {id} (game UUID) and {transaction} (transaction ID). See https://docs.tebex.io/creators/command-management/an-introduction-to-commands.

| Product | Initial command | Renewal command |
|---|---|---|
| VIP Monthly | `mnperms tebex purchase {id} vip_monthly {transaction}` | `mnperms tebex renew {id} vip_monthly {transaction}` |
| MVP Monthly | `mnperms tebex purchase {id} mvp_monthly {transaction}` | `mnperms tebex renew {id} mvp_monthly {transaction}` |
| VIP Lifetime | `mnperms tebex purchase {id} vip_lifetime {transaction}` | None |
| MVP Lifetime | `mnperms tebex purchase {id} mvp_lifetime {transaction}` | None |

For each product configure refund and chargeback commands using the same product token: `mnperms tebex refund {id} vip_monthly {transaction}`, `mnperms tebex chargeback {id} vip_monthly {transaction}`. Replace vip_monthly with that package's token. Monthly expiry: `mnperms tebex expire {id} vip_monthly {transaction}` (or mvp_monthly). Do not revoke access on cancellation request.

The local ledger stores one entry per player/product/transaction. Monthly delivery grants one UTC calendar month starting at receipt or the current paid-through date, whichever is later. Expiry never shortens a locally recorded paid period. Refund and chargeback revoke only the referenced transaction, including a refund-before-purchase tombstone. Administrative groups remain separate. The initial delivery time may differ from payment time; exact provider billing period dates are not available through these verified command placeholders. Before production, test manual payments, renewal transaction identity, offline delivery, delayed delivery, refunds, chargebacks, restart, expiry and overlapping ranks. If Tebex reuses a transaction reference on renewal, this adapter will reject it as duplicate; do not activate real subscriptions until verified. No Discord integration was identified in the inspected Java core. No payment processing is implemented here.
