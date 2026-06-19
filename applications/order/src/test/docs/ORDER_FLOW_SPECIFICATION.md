# OFBiz Order Processing — Business Specification

**Scope:** Complete order-placement flow in `COG-GTM/ofbiz-framework`, from the moment a shopper presses "Submit Order" through to the order-confirmation email. Traced from the `storeOrder` service (`applications/order/servicedef/services.xml`, implemented by `OrderServices.createOrder`) and its entry point `CheckOutEvents.createOrder`.

**How to read this document:** Each numbered phase below describes, in plain English, *what happens*, *what conditions cause it to happen differently*, and *what data gets saved*. Section A is the high-level call tree. Section B is the step-by-step narrative. Section C lists every branch/permutation. Section D lists the automatic background rules (SECAs) that fire on their own. Section E is the stakeholder review checklist.

A non-technical reviewer should be able to read Section B + C and confirm "yes, that is how our orders work" or flag a path that is wrong or missing.

---

## A. The order flow at a glance (call tree)

```
[Shopper submits order] (web request: "processorder")
│
└─ CheckOutEvents.createOrder            ← entry-point event
   │   • re-validates the cart (payment method + shipping method present for sales orders)
   │   • optionally "explodes" kit items into components (store setting)
   │   • gathers tracking-code / affiliate / distributor / visit / website context
   │
   └─ CheckOutHelper.createOrder         ← builds the "order context" map from the cart
      │
      ├─ storeOrder  (= OrderServices.createOrder)   ← THE CORE: writes the order to the DB
      │   ├─ Permission check (sales vs purchase order)
      │   ├─ Product Store lookup (sales orders)
      │   ├─ Per-item validation loop
      │   │     • countProductQuantityOrdered      (statistics)
      │   │     • product exists?
      │   │     • introduction date passed?         (sales)
      │   │     • sales-discontinuation date?       (sales)
      │   │     • isStoreInventoryAvailableOrNotRequired  (sales, stock check)
      │   │     • rental items must have WorkEfforts
      │   ├─ Generate orderId (getNextOrderId)
      │   ├─ PERSIST OrderHeader            (status = ORDER_CREATED)
      │   ├─ PERSIST OrderStatus            (header + one per item)
      │   ├─ PERSIST OrderItem(s) / OrderItemGroup(s)
      │   ├─ PERSIST OrderAttribute / OrderItemAttribute
      │   ├─ createOrderNote (internal + public notes)
      │   ├─ PERSIST WorkEffort + calendars (rental items)
      │   ├─ PERSIST OrderAdjustment(s)     (promotions, tax, shipping, fees)
      │   ├─ PERSIST OrderContactMech / OrderItemContactMech
      │   ├─ PERSIST OrderItemShipGroup + OrderItemShipGroupAssoc (+ ship adjustments)
      │   ├─ PERSIST OrderRole / PartyRole  (customer, vendor, affiliate, distributor…)
      │   ├─ PERSIST OrderItemSurveyResponse / OrderItemPriceInfo / OrderItemAssoc
      │   ├─ PERSIST OrderProductPromoUse / OrderProductPromoCode
      │   ├─ PERSIST OrderPaymentPreference (status = PAYMENT_NOT_RECEIVED)
      │   ├─ PERSIST TrackingCodeOrder / OrderTerm / OrderHeaderWorkEffort
      │   ├─ delegator.storeAll(...)        ← single DB write of everything above
      │   ├─ receiveInventoryProduct        (service-type products only)
      │   └─ reserveInventory → reserveStoreInventory  (sales orders, per ship group)
      │        └─ createProductionRunForMktgPkg (auto marketing packages)
      │   ‹ SECAs fire automatically here — see Section D.1 ›
      │
      ├─ createProductionRunFromConfiguration  (AGGREGATED / configurable products)
      └─ createOrderRequirementCommitment      (items sourced from a Requirement)
   │
   ├─ checkOrderDenylist  (web request: "checkDenyList")   ← fraud screen
   │     └─ if matched → failedDenylistCheck → order rejected, session killed
   │
   ├─ CheckOutEvents.processPayment (web request: "processpayment")
   │   └─ CheckOutHelper.processPayment
   │       ├─ manual / verbal reference auths → processAuthResult → approveOrder
   │       ├─ PayPal Express completion        → doExpressCheckout
   │       ├─ online cards/EFT (NOT_AUTH)      → authOrderPayments → APPROVED/FAILED/ERROR
   │       └─ CASH / COD / CHECK / BILLING ACCT → approveOrder (rules per type)
   │       └─ face-to-face (POS)               → completeOrder (invoice + receive payment)
   │           └─ approveOrder/completeOrder → OrderChangeHelper.orderStatusChanges
   │                 └─ changeOrderStatus / changeOrderItemStatus
   │                     ‹ status-change SECAs fire — see Section D.2 ›
   │
   ├─ destroyCart  (web request: "clearcart")
   │
   └─ sendOrderConfirmation  (web request: "emailorder", run ASYNC)
       └─ OrderServices.sendOrderConfirmNotification
           └─ sendOrderNotificationScreen( emailType = PRDS_ODR_CONFIRM )
               └─ sendMailFromScreen   → renders & emails the confirmation
               ‹ SECA: createOrderNotificationLog records that the email was sent ›
```

---

## B. Step-by-step narrative

### Phase 1 — Order entry & cart re-validation (`CheckOutEvents.createOrder`)
Triggered by the `processorder` web request.

- The shopping cart is reloaded from the session.
- **Validation (`checkoutValidation`)** — for **sales orders only**:
  - At least one **payment method type** must be selected, otherwise the order is rejected with *"No payment method selected."*
  - A **shipment method** must be selected, otherwise *"No shipment method selected."*
  - (Purchase orders skip both checks.)
- If there is no logged-in user, the cart's user login is adopted (supports **anonymous checkout**).
- **Kit explosion (`explodeOrderItems`)** — if the Product Store has `explodeOrderItems = Y`, kit/bundle line items are expanded into their component lines before the order is written.
- Context is collected: tracking codes, distributor id, affiliate id, visit id, web-site id.
- Control passes to `CheckOutHelper.createOrder`, which converts the cart into a flat "context" map (`cart.makeCartMap`) and calls the **`storeOrder`** service.

### Phase 2 — Core order creation (`storeOrder` = `OrderServices.createOrder`)

**2a. Security & store context**
- Permission is checked by order type: sales orders need `ORDERMGR_SALES_CREATE`/`ORDERMGR_CREATE` (or the user is ordering for themselves, or is a sales agent for the customer); purchase orders need a purchase-order permission.
- For **sales orders** a **Product Store** record is loaded. A sales order *must* have a product store; a purchase order may omit it.
- The store's `isImmediatelyFulfilled` flag is read (drives the "immediate fulfillment / POS" path later).

**2b. Per-item validation loop** (collects ALL errors, then fails once if any exist)
- Quantities are normalized across duplicate product lines so stock checks are accurate.
- `countProductQuantityOrdered` increments lifetime demand statistics (also rolls up to the virtual/parent product).
- Each product must **exist**.
- **Sales orders only:**
  - Product **introduction date** must not be in the future → else *"not yet for sale."*
  - Product **sales-discontinuation date** must not have passed → else *"no longer for sale."* (Back-dated historical imports are allowed.)
  - **Stock availability** via `isStoreInventoryAvailableOrNotRequired` → else *"out of stock."*
- **Rental items** (`RENTAL_ORDER_ITEM`) must have matching `WorkEfforts` (start/end dates, persons); the related Fixed Asset is resolved and reserved against a capacity calendar.

**2c. Order identity & header**
- An `orderId` is generated (`getNextOrderId`, store/organization aware; falls back to a sequence).
- **`OrderHeader`** is created and written immediately with:
  - `statusId = ORDER_CREATED`, order date, entry date, order type, currency, grand total, billing account, sales channel (defaults to the store's channel, else `UNKNWN_SALES_CHANNEL`), product store, web site, created-by, priority/rush flags, and `needsInventoryIssuance = Y` if the store is immediate-fulfillment.

**2d. Everything else is queued and written in one batch (`storeAll`)**
The following are accumulated and persisted together:

| Data written | Purpose |
|---|---|
| **OrderStatus** (header) | initial ORDER_CREATED audit record |
| **OrderItem** + **OrderStatus** (per item) | the actual line items + per-line status history |
| **OrderItemGroup** | groups/sub-orders (if used) |
| **OrderAttribute / OrderItemAttribute** | extra key/value data on order & lines |
| **Order notes** (`createOrderNote`) | internal and customer-visible notes |
| **WorkEffort / WorkOrderItemFulfillment / TechDataCalendar*** | rental scheduling & asset capacity |
| **OrderAdjustment** | promotions, taxes, shipping, surcharges, fees (order- and item-level) |
| **OrderContactMech / OrderItemContactMech** | addresses, emails tied to the order |
| **OrderItemShipGroup** | one per shipment destination/method; defaults carrier role to CARRIER; flags drop-ship groups (those with a supplier) |
| **OrderItemShipGroupAssoc** | links each item+quantity to a ship group |
| **OrderRole / PartyRole** | customer, ship-to, bill-to, vendor, affiliate, distributor, etc. |
| **OrderItemSurveyResponse** | survey answers captured at purchase |
| **OrderItemPriceInfo** | how each price was derived |
| **OrderItemAssoc** | item-to-item links (e.g. exchanges) |
| **OrderProductPromoUse / OrderProductPromoCode** | promotions applied & codes entered |
| **OrderPaymentPreference** | one per payment instrument; `statusId = PAYMENT_NOT_RECEIVED` |
| **TrackingCodeOrder** | marketing attribution |
| **OrderTerm** | payment/credit terms (esp. purchase orders) |
| **OrderHeaderWorkEffort** | links order to a work effort, if supplied |

**2e. Inventory after persistence**
- **Service products** (`SERVICE_PRODUCT` / `AGGREGATEDSERV_CONF`): a serialized inventory item is auto-received (`receiveInventoryProduct`) so the service can be "stocked."
- **`reserveInventory`** runs for **sales orders** (skipped if the store is immediate-fulfillment), iterating each `OrderItemShipGroupAssoc`:
  - Drop-ship groups are **not** reserved.
  - If store `allocateInventory = Y`, only items flagged `autoReserve=true` are reserved.
  - Items with a future `reserveAfterDate` are skipped.
  - Cancelled/rejected/completed lines are skipped; rental and non-product lines are skipped.
  - Marketing-package "pick" kits reserve their **components**; everything else calls **`reserveStoreInventory`** for the line quantity at the ship group's facility.
  - Marketing-package "auto" products trigger `createProductionRunForMktgPkg`.
  - If any reservation fails → *"…is no longer in stock"* and the whole order creation rolls back.

On success `storeOrder` returns `orderId` and `statusId = ORDER_CREATED`.

### Phase 3 — Post-creation side effects (back in `CheckOutHelper.createOrder`)
- **Configurable/aggregated products** → `createProductionRunFromConfiguration` (run as `system`).
- Items linked to a **Requirement** → `createOrderRequirementCommitment`.
- Customer email addresses (party emails + any additional emails typed at checkout) are saved as **OrderContactMech** records with purpose `ORDER_EMAIL`.

### Phase 4 — Fraud / deny-list screen (`checkOrderDenylist`)
- Compares the shipping address, and for credit cards the card number and billing address, against the **OrderDenylist**.
- **No match →** continue to payment.
- **Match →** `failedDenylistCheck`: the order is marked fraud-rejected using the store's `authFraudMessage`, and the anonymous/own session is invalidated.

### Phase 5 — Payment processing (`CheckOutEvents.processPayment` → `CheckOutHelper.processPayment`)
Reads the store's decline/error messages and `retryFailedAuths` flag, loads all non-cancelled `OrderPaymentPreference`s, then:

1. **Manual / verbal-reference payments** (have a `manualRefNum`): treated as already authorized (`processAuthResult`), the **order is approved**, and if the store has `manualAuthIsCapture = Y` the funds are also captured (`processCaptureResult`).
2. **PayPal Express** preferences: completed via `doExpressCheckout`.
3. **Online payments awaiting auth** (`PAYMENT_NOT_AUTH`, e.g. credit cards/EFT):
   - If the order total is **0** and the store auto-approves → approve without calling the gateway.
   - Otherwise call **`authOrderPayments`** (the gateway):
     - **APPROVED →** if the store auto-approves, **order is approved** (a CyberSource ACCEPT decision is honored when configured).
     - **FAILED →** **order is rejected**; customer sees the decline message.
     - **ERROR →** if not face-to-face and `retryFailedAuths = Y`, return a soft error so the shopper can retry; otherwise **cancel the order**.
4. **Offline types only** (when nothing needed online auth) — when the order's payments are exclusively CASH / EXT_COD / PERSONAL_CHECK / EXT_BILLACT:
   - **PERSONAL_CHECK** → approved **only** if face-to-face; otherwise left pending.
   - Otherwise (cash / COD / billing account) → **order is approved**.
5. **Face-to-face (POS) sales** → change is calculated, and the order is **completed** immediately (`completeOrder` creates the invoice and received-payment records).

Approve/reject/cancel/complete all funnel through `OrderChangeHelper.orderStatusChanges`, which calls **`changeOrderStatus`** (header) and **`changeOrderItemStatus`** (lines). Target statuses come from the **Product Store** (`headerApprovedStatus`, `itemApprovedStatus`, `digitalItemApprovedStatus`, decline/cancel equivalents), defaulting to `ORDER_APPROVED` / `ITEM_APPROVED`. A held order goes to `ORDER_PROCESSING` instead.

On any payment error the web layer clears the declined payment methods from the cart and nulls the order id so the shopper can try again.

### Phase 6 — Cart cleanup & confirmation email
- `clearcart` destroys the session cart.
- `emailorder` invokes **`sendOrderConfirmation`** **asynchronously** (so a slow mail server never blocks order placement; up to 3 retries).
- `sendOrderConfirmNotification` → `sendOrderNotificationScreen` with email type **`PRDS_ODR_CONFIRM`**:
  - Requires the order to have a **web site** and a matching **ProductStoreEmailSetting** for that store + email type (subject, from/cc/bcc, body screen, optional PDF attachment screen).
  - Requires a customer **email address** on the order (else a soft failure is logged — order still stands).
  - Locale is chosen from the placing customer, else store default, else system default.
  - `sendMailFromScreen` renders the body screen and sends the message.
  - A SECA then records a **`createOrderNotificationLog`** entry capturing that the confirmation was sent.

The shopper is redirected to the order-view / order-complete page.

---

## C. Branching points & permutations (every path the order can take)

### C.1 Order type
- **Sales order (`SALES_ORDER`)** — requires product store, payment & shipment methods, stock checks, inventory reservation, sales channel.
- **Purchase order (`PURCHASE_ORDER`)** — no product store required, no stock/availability checks, different roles & terms, no inventory reservation; on approval can auto-create a payment and update requirements to "Ordered."
- **Work order** — handled like a sales order in the web flow (same deny-list → payment chain).

### C.2 Product Store configuration (drives many paths)
| Store setting | Effect |
|---|---|
| `explodeOrderItems` | Kits expanded into components at entry. |
| `isImmediatelyFulfilled` | Order flagged `needsInventoryIssuance=Y`; **inventory reservation is skipped** (POS/immediate model). |
| `allocateInventory` | Only `autoReserve=true` items are reserved. |
| `oneInventoryFacility` / `inventoryFacilityId` | Where service inventory is received / stock is reserved. |
| `autoApproveOrder` | Whether an authorized order is approved automatically. |
| `headerApprovedStatus` / `itemApprovedStatus` / `digitalItemApprovedStatus` | Override the post-approval statuses. |
| `headerDeclinedStatus` / `itemDeclinedStatus` / cancel equivalents | Override reject/cancel statuses. |
| `retryFailedAuths` | Soft-retry vs. cancel on gateway ERROR. |
| `manualAuthIsCapture` | Manual auth also captures funds. |
| `authDeclinedMessage` / `authErrorMessage` / `authFraudMessage` | Customer-facing messages. |
| `reqShipAddrForDigItems` | All-digital carts can skip a shipping address. |
| `defaultSalesChannelEnumId` / `defaultLocaleString` | Channel & email locale defaults. |

### C.3 Payment method type
- **Credit card / EFT (online)** → gateway auth → APPROVED / FAILED / ERROR paths above.
- **Manual / verbal reference** → treated as authorized; optional capture.
- **PayPal Express (`EXT_PAYPAL`)** → external completion; (PayflowPro config routes through a dedicated "paypal" step).
- **Cash (`CASH`)** → approved (typically POS).
- **COD (`EXT_COD`)** → approved, collected on delivery.
- **Personal check (`PERSONAL_CHECK`)** → approved only face-to-face.
- **Billing account (`EXT_BILLACT`)** → approved against account credit.
- **Zero-total order** → approved without contacting any gateway (if auto-approve).
- **Mixed online + offline** → online auth governs; offline-only shortcut applies only when *every* preference is offline.

### C.4 Shipping / ship-group variations
- **One vs. multiple ship groups** (multiple destinations or split shipments) — each is its own `OrderItemShipGroup` with its own method, address, and tax.
- **Drop-ship group** (has a `supplierPartyId`) — not reserved; can spawn a drop-ship purchase order (SECA, see D.1).
- **All-digital order** with `reqShipAddrForDigItems = N` — no shipping address required.
- **Face-to-face / facility pickup** — tax falls back to the origin-facility address when there's no ship or billing address.

### C.5 Promotions / adjustments
- Promotion uses and entered promo codes are stored (`OrderProductPromoUse`, `OrderProductPromoCode`).
- Adjustments (discounts, tax, shipping, surcharges) are stored as `OrderAdjustment`; the grand total is recomputed automatically (SECA `resetGrandTotal`).

### C.6 Special product/item types
- **Service products** → inventory auto-received.
- **Configurable/aggregated products** → production run created.
- **Marketing package "pick"** → components reserved; **"auto"** → production run created.
- **Rental items** → work efforts + asset capacity calendar; capacity sell-out is enforced.
- **Items from a Requirement** → requirement commitment recorded.

### C.7 Customer identity
- **Logged-in**, **anonymous** (temporary user login used for emails/locale), or **created on behalf of** (order manager / sales agent).

### C.8 Exchange / replacement orders
- If an `originOrderId` is present, an exchange association is created (SECA `createExchangeOrderAssoc`).

---

## D. Automatic background rules (Service Event Condition Actions — SECAs)
These fire on their own when a service commits/returns; they are *not* called explicitly in the code path.

### D.1 When `storeOrder` finishes (`applications/order/servicedef/secas.xml`)
| Condition | Action(s) fired | Plain English |
|---|---|---|
| always (return) | `resetGrandTotal` (sync), `addSuggestionsToShoppingList` (async) | Recompute order total; suggest related items. |
| `orderTypeId = SALES_ORDER` (return) | `checkCreateDropShipPurchaseOrders` (as system) | Create drop-ship POs to suppliers for drop-ship groups. |
| always (return) | `balanceOrderItemsWithNegativeReservations` | Fix any over-reservations. |
| `orderTypeId = PURCHASE_ORDER` (return) | `setUnitPriceAsLastPrice` | Remember supplier's last price. |
| always (return) | `setOrderReservationPriority` | Set reservation priority. |
| `SALES_ORDER` + `isInventoryAllocationRequired` (return) | `associateOrderWithAllocationPlans` | Tie order to allocation plans. |
| `originOrderId` present (commit) | `createExchangeOrderAssoc` | Link exchange to original order. |
| always (commit) | `updateShoppingListQuantitiesFromOrder` | Track shopping-list purchases. |
| `SALES_ORDER` (commit) | `checkOrderItemForProductGroupOrder` | Group-buying handling. |

### D.2 When order/item status changes (`changeOrderStatus` / `changeOrderItemStatus`)
| Condition | Action(s) | Plain English |
|---|---|---|
| header → `ORDER_APPROVED` from `ORDER_CREATED`, SALES | `createAutoRequirementsForOrder`, `createATPRequirementsForOrder` | Generate procurement/ATP requirements. |
| header → `ORDER_APPROVED` from `ORDER_CREATED`, PURCHASE | `updateRequirementsToOrdered` | Mark requirements ordered. |
| header → `ORDER_APPROVED`, SALES | `updateContentSubscriptionByOrder`, `processExtendSubscriptionByOrder` | Activate/extend digital subscriptions. |
| header → `ORDER_APPROVED`, PURCHASE | `createPaymentFromOrder` | Auto-create payable (configurable). |
| header → `ORDER_COMPLETED`, SALES | `createPaymentFromOrder` | Record received payment (configurable). |
| header → `ORDER_COMPLETED` (post-commit) | `createInvoiceFromOrder` (+ others) | Generate the invoice. |
| header → `ORDER_CANCELLED` | `releaseOrderPayments`, `processRefundReturnForReplacement` | Release auths / refund. |
| item → `ITEM_APPROVED` | `checkOrderItemStatus`, `checkDigitalItemFulfillment`, `invoiceServiceItems` | Roll up status, fulfill digital goods, invoice services. |
| item → `ITEM_CANCELLED` | `cancelOrderInventoryReservation`, recalc shipping/tax/total, `checkOrderItemStatus` | Undo reservation & recompute. |

### D.3 When confirmation/notification emails commit
| Service | Action | Plain English |
|---|---|---|
| `sendOrderConfirmation` | `createOrderNotificationLog` | Record that the confirmation email was sent. |
| `sendOrderChangeNotification` / `sendOrderBackorderNotification` / `sendOrderCompleteNotification` / `sendOrderPayRetryNotification` | `createOrderNotificationLog` | Same, for other notification types. |
| `updateOrderItems` / `appendOrderItem` (commit) | `resetGrandTotal`, `sendOrderChangeNotification` | Recompute & notify on edits. |
| `updateOrderItems` / `appendOrderItem` (return) | `processOrderPayments` | Re-process payments after edits. |

---

## E. Expected outputs per scenario (stakeholder checklist)

| Scenario | End state of the order | Customer sees / receives |
|---|---|---|
| Online card, approved, auto-approve store | `ORDER_APPROVED`; inventory reserved; payment authorized | Order-complete page + **confirmation email** (PRDS_ODR_CONFIRM) |
| Online card, declined | `ORDER_REJECTED` (or store decline status); reservations & auths released | Decline message; cart kept for retry |
| Online card, gateway error, retry enabled | Order left pending | Soft error message; shopper can retry |
| Online card, gateway error, retry disabled | `ORDER_CANCELLED` | Error message |
| Zero-total order, auto-approve | `ORDER_APPROVED` without any gateway call | Confirmation email |
| Cash / COD / billing account | `ORDER_APPROVED` | Confirmation email |
| Personal check, not face-to-face | Created, **left pending** approval | Confirmation email; awaits manual approval |
| Face-to-face (POS) | `ORDER_COMPLETED`; invoice + received payment created; change calculated | Receipt; confirmation email if email set |
| Deny-list match | Fraud-rejected; session invalidated | Fraud message |
| Out-of-stock sales item | Order **not created** | "out of stock" error |
| Discontinued / not-yet-for-sale item | Order **not created** | "no longer / not yet for sale" error |
| Missing payment or shipment method (sales) | Order **not created** | Validation error |
| Immediate-fulfillment store | `ORDER_CREATED` with `needsInventoryIssuance=Y`; **no reservation** | Confirmation email |
| Drop-ship group | Order created; supplier PO auto-created; those items not reserved | Confirmation email |
| Anonymous checkout | Order created under temporary user login; email/locale resolved from order | Confirmation email |
| Exchange order (has originOrderId) | Order created + linked to original | Confirmation email |
| No email address on order | Order stands; confirmation **not** sent (soft failure logged) | No email |

---

## F. Key source references
- Entry event & web chain: `applications/order/src/main/java/.../shoppingcart/CheckOutEvents.java` (`createOrder` ~L473, `processPayment` ~L580, `checkShipmentNeeded` ~L557); `applications/order/webapp/ordermgr/WEB-INF/controller.xml` (`processorder`→`checkDenyList`→`processpayment`→`clearcart`→`emailorder`, ~L999–1048).
- Cart→service bridge & payment engine: `.../shoppingcart/CheckOutHelper.java` (`createOrder` ~L656, `processPayment` ~L1095).
- Core persistence & reservation: `.../order/order/OrderServices.java` (`createOrder` ~L190, `reserveInventory` ~L1212).
- Status transitions: `.../order/order/OrderChangeHelper.java` (`approveOrder`/`rejectOrder`/`completeOrder`/`cancelOrder`/`orderStatusChanges` ~L45–160).
- Confirmation email: `OrderServices.sendOrderConfirmNotification`/`sendOrderNotificationScreen` (~L2727, ~L2759).
- Service definitions: `applications/order/servicedef/services.xml` (`storeOrder` ~L107, `sendOrderConfirmation` ~L49).
- Automatic rules: `applications/order/servicedef/secas.xml`.
