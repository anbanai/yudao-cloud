# Coupon marketing backend contract — CMP-100

Backend: `.paperclip-repositories/yudao-cloud-e0d5a436a842`, target branch `teaworthshare` review base `bbe6610c`.
All JSON examples below are the `data` inside the existing CommonResult envelope. Standard `/admin-api`, `/app-api`, and RPC gateway prefixes still apply. Prices are integer cents. All writes require the existing authenticated role or internal RPC authentication; public endpoints are explicitly listed.

## Template configuration

The existing admin `/promotion/coupon-template/create`, `/update`, `/get`, and `/page` add:

| Field | Contract |
| --- | --- |
| `couponType` | 1 discount (default), 2 product exchange |
| `exchangeSpuId`, `exchangeSkuId` | Exchange requires an existing SKU belonging to the SPU |
| `canStack` | Whether another coupon may be combined; default false |
| `stackLimit` | Maximum count of simultaneously applied coupons, at least 1; every selected coupon's limit applies |
| `stackWithPromotion` | Whether discount/VIP/reward marketing and point redemption can coexist; default false |
| `newcomerOnly` | Mark a USER template as the homepage newcomer coupon. All USER claim routes enforce qualification for this template |

Exchange templates disallow stacking and bind one SKU. ADMIN and REGISTER retain their existing issuance meanings. Snapshot fields are copied onto the issued coupon, including the original validity deadline; later template changes and member upgrades do not invalidate issued coupons. App coupon and template responses expose the exchange and stacking fields.

## Preview, confirm, and submit

New admin routes all use `promotion:coupon:send`, except reading a result uses `promotion:coupon:query`:

1. `POST /promotion/coupon/send-preview` accepts `{templateId, idempotencyKey, userIds?, mobiles?, tagIds?}`. IDs, deduplicated trimmed mobile numbers, and the union of matching tags resolve to a sorted account snapshot (maximum 5000 accounts; maximum 100 tags). Mobile values must match mainland 11-digit mobile format. Invalid/unknown accounts, quota violations, exhausted inventory, or expired templates appear in `failureDetails` / `failureCount`. No coupons are issued here. `submit` in the historical DTO is ignored.
2. Response is a batch: `{id, templateId, operatorId, status:0, totalCount, successCount:0, failureCount, mobileSnapshot, userSnapshot, templateSnapshot, failureDetails}`. `templateSnapshot` contains the exact name, validity rules, and template settings to display in the confirmation dialog. The idempotency key is bound to the operator/template; repeated preview returns the original snapshot.
3. Canceling the dialog performs no submit call; the inert preview record may remain. Do not call the legacy send route on cancel.
4. Confirm with `POST /promotion/coupon/send-submit?batchId=<id>`. Only the previewing operator may submit. Template changes require a new preview. This rechecks inventory and per-user limits under a template lock. Errors roll back the entire batch, including all coupon inserts and counters, then persist `status:2`, zero successes, and a failure detail on the batch. Retries return the same terminal batch without issuing coupons. Success returns `status:1, successCount:totalCount`.
5. Read `GET /promotion/coupon/send-batch?id=<id>` to recover a response after a timeout.

Single-account sends use `userIds:[id]` through exactly the same preview/submit flow. Existing `/promotion/coupon/send` and internal gift APIs remain compatible; the management UI must use preview/submit for second confirmation. No menu/permission migration is needed: existing coupon send/query permissions apply.

## Promotion

- Admin `POST /promotion/coupon-promotion/create`, permission `promotion:coupon-template:query`, accepts `{templateId, source?, channel?, idempotencyKey}`. Only enabled USER templates are promotable. The server generates `shortCode` and `miniProgramPath=/pages/coupon/detail?id=<templateId>&promotionCode=<shortCode>`. Clients share this path as a link/card. Custom client `miniProgramPath` is ignored. This implementation generates a path; it does not call a WeChat card or poster generation service.
- Public app `GET /promotion/coupon-promotion/resolve?code=...` resolves the template and path. Unknown code returns null.
- Authenticated app `POST /promotion/coupon-promotion/take?code=...` returns coupon IDs. A persistent `(tenant,promotion,user)` ledger makes retries return the same ID even after the coupon is used/revoked. The coupon stores `promotionId`; the promotion stores source/channel and increments takeCount once. `visitCount` is reserved and is not a unique-visitor metric.
- The miniapp must preserve `promotionCode` and call the promotion take endpoint to retain attribution.

## Newcomer coupon

Homepage DIY stores a template ID configured with `newcomerOnly=true`. Public `GET /promotion/coupon-template/newcomer?id=...` returns template display fields plus `{templateId, showEntry, canTake, reasonCode, reason}`. Missing/disabled/non-newcomer templates return null.

Visitors see `showEntry=true, canTake=false, reasonCode=LOGIN_REQUIRED`. Logged-in users must have L0 (or an unassigned default level), no historical orders (including unpaid, canceled, and logically deleted orders), and no previous claim. Upgraded or unknown assigned levels fail closed and hide the entry. Other reason codes are `USER_NOT_FOUND`, `ORDER_HISTORY_EXISTS`, `ALREADY_TAKEN`, `TAKE_LIMIT_REACHED`, `TEMPLATE_EXHAUSTED`, and `REGISTER_AUTO_ISSUE`.

Authenticated `POST /promotion/coupon/take-newcomer` takes `{templateId}` and returns the qualification response with `ALREADY_TAKEN` after success. The shared service rechecks all USER claim paths under the template lock, preventing bypass through `/take` or promotion links. Per-template concurrency cannot issue two newcomer coupons. Once issued, order completion and upgrade do not affect use: validity and ownership are the use criteria. Cross-service order creation versus eligibility checking still needs staging race validation.

## Settlement, exchange, cancellation

Existing app `/trade/order/settlement` and `/create` accept `couponIds:[...]`; legacy `couponId` is merged/deduplicated first. At most 10 coupon IDs are accepted. Prices are calculated in request order against the remaining item price. Every coupon must permit the group count and mutual stacking. Other marketing and point redemption require `stackWithPromotion=true`; special activity order types retain the existing coupon exclusion.

Settlement returns `effectiveCouponIds` and `couponExclusionReasons` alongside coupon availability, per-item prices and promotion details. Rejected coupons are excluded from the preview. Creation recomputes strictly and fails if any selected coupon is no longer valid; it never silently changes the selected set. The order persists all effective IDs, with `couponId` retaining the first for older consumers. Internal batch use/return endpoints atomically mutate all coupons in the promotion database. Row locks, stable order IDs, and order-checked returns protect duplicate/late callbacks.

Exchange uses the normal `/trade/order/create` flow with one bound SKU, `count:1`, and `couponId`. No standalone exchange fulfillment API is required. The item price is fully discounted; shipping may still be payable. Normal product stock checks/reservation, delivery address, shipment, and cancellation hooks apply. Zero-payment exchanges move to UNDELIVERED without creating a payment order. Exchange records link coupon/user/SPU/SKU/order and transition `0 reserved -> 1 fulfilled` on delivery or `-> 2 canceled` on cancellation/full item refund. Coupon validity is preserved on return.

An immediate rollback callback returns coupons if order creation rolls back after a remote coupon commit. Every ten minutes a tenant-aware recovery job checks USED coupons older than 30 minutes against the trade service, returning only those whose order is absent or canceled. Trade-service failures leave the coupon for retry. The trade status RPC includes logically deleted orders, preventing completed deleted orders from being treated as absent. Staging must verify the 30-minute grace period exceeds the maximum order transaction duration and exercise remote failure recovery together with the existing stock compensation path.

## Database and verification

With writes stopped and a backup taken, apply `V20261113_02__coupon_marketing.sql` on MySQL 8+. The forward script checks existing columns, indexes, and tables. Coupon tables include tenant-scoped idempotency constraints and recovery indexes. Run `R20261113_02__coupon_marketing.sql` only after exporting snapshots and stopping jobs/writes; it refuses rollback with pending exchange fulfillment. Rollback intentionally removes new feature data after export; it does not undo already placed trade orders. The separate member point lifecycle migration is not part of this change.

H2 tests exercise real service transactions, mappers, FIFO, concurrent debits, atomic coupon batches, idempotency, lifecycle records and the production historical FIFO SELECT. Full MySQL forward/re-run/rollback execution and staging Feign/stock/payment/tenant scheduling tests remain release checks, not claimed as passed by the H2 results. Exact Maven commands and test counts are recorded in `docs/evidence/cmp100-tests.txt`.
