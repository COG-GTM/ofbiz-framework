# Order-Flow Cucumber / JUnit Test Harness

This document explains the BDD (Cucumber) + JUnit test harness that exercises the OFBiz
order-creation flow, how it is wired together, how to run it, and how to extend it with new
scenarios. It complements the Phase 1 business specification
([`ORDER_FLOW_SPECIFICATION.md`](ORDER_FLOW_SPECIFICATION.md)) and the Phase 3 parity framework
(`org.apache.ofbiz.order.bdd.parity.OrderParitySnapshot`).

---

## 1. Goal

Prove that the order-placement pipeline
(`CheckOutEvents.createOrder` → `CheckOutHelper.createOrder` → `storeOrder` /
`OrderServices.createOrder`) behaves as described in the business specification, by:

1. Describing each order permutation in plain-English Gherkin that a business stakeholder can read.
2. Building a **real** `ShoppingCart`, placing the order through the real `storeOrder` service.
3. Validating results at **two levels**:
   - **(a) the returned acknowledgment** — the service result map (`responseMessage`, `orderId`).
   - **(b) the persisted database state** — querying `OrderHeader`, `OrderItem`, `OrderStatus`,
     `OrderPaymentPreference`, `OrderItemShipGroup`, `OrderAdjustment` and asserting expected values.

---

## 2. Where everything lives

```
applications/order/src/test
├── docs/
│   ├── ORDER_FLOW_SPECIFICATION.md      Phase 1 business spec
│   ├── CUCUMBER_JUNIT_HARNESS.md        this document
│   └── README.md                        short overview of all three phases
├── java/org/apache/ofbiz/order/bdd/
│   ├── OfbizTestContainer.java          boots an in-process delegator + dispatcher (once per JVM)
│   ├── OrderFlowCucumberTest.java       JUnit 5 entry point that runs the feature files
│   ├── OrderStoreStepDefinitions.java   the Given/When/Then glue
│   └── parity/
│       ├── OrderParitySnapshot.java     Phase 3 snapshot + diff utility
│       └── OrderParityTest.java         demonstrates baseline-vs-candidate parity
└── resources/features/order/            the six Gherkin scenarios (*.feature)
```

Build wiring: two `testImplementation` dependencies (`io.cucumber:cucumber-java` and
`io.cucumber:cucumber-core`) are declared in `dependencies.gradle`. No production code changes.

---

## 3. Architecture

```
        ./gradlew test
              │
              ▼
   OrderFlowCucumberTest      (JUnit 5 test)
              │  assumeTrue(OfbizTestContainer.isAvailable())
              │  builds Cucumber RuntimeOptions (glue + feature path)
              ▼
   Cucumber Runtime           (io.cucumber.core.runtime.Runtime)
              │  matches each Gherkin step to a glue method
              ▼
   OrderStoreStepDefinitions  (the steps)
        │            │
        │            └── reads/asserts persisted rows via EntityQuery (Delegator)
        ▼
   ShoppingCart ──► CheckOutHelper.createOrder ──► storeOrder service
        ▲
        │ delegator + dispatcher supplied by
   OfbizTestContainer          (single in-process OFBiz runtime)
```

### 3.1 `OfbizTestContainer` — runtime bootstrap

OFBiz services need a live **delegator** (Entity Engine) and **dispatcher** (Service Engine).
`OfbizTestContainer` boots both, once per JVM, the first time it is touched:

- Loads the OFBiz component definitions (`ComponentContainer`) and obtains the default delegator.
- Initialises the `ServiceContainer` and obtains a `LocalDispatcher`.
- Exposes `getDelegator()`, `getDispatcher()`, `isAvailable()`, `unavailableReason()`.

If the runtime cannot start (for example a `./gradlew check` run on a machine where
`./gradlew loadAll` has not populated the seed/demo data), `isAvailable()` returns `false` and the
tests **skip gracefully** via JUnit's `assumeTrue(...)` instead of failing the build.

### 3.2 `OrderFlowCucumberTest` — runner

Cucumber is driven through its **own** `io.cucumber.core.runtime.Runtime` rather than the
JUnit Platform engine, so the harness does not depend on the JUnit Platform version OFBiz pins:

```java
RuntimeOptions options = new CommandlineOptionsParser(System.out)
        .parse(GLUE, "org.apache.ofbiz.order.bdd",
               PLUGIN, "pretty", PLUGIN, "summary",
               "classpath:features/order")
        .build();

Runtime runtime = Runtime.builder().withRuntimeOptions(options).build();
runtime.run();
assertEquals((byte) 0, runtime.exitStatus(), "One or more order-flow scenarios failed");
```

`runtime.exitStatus()` is non-zero if any scenario fails, which fails the JUnit test.

### 3.3 `OrderStoreStepDefinitions` — the glue

Holds the per-scenario state (`cart`, `result`, `orderId`, `rejected`, `errorMessage`) and the
step methods. The class is `final` (OFBiz checkstyle forbids non-final, non-javadoc'd extendable
methods); Cucumber instantiates it once per scenario, so state never leaks between scenarios.

---

## 4. Step-definition vocabulary

These are the steps available to write scenarios with.

### Given — start a cart
| Step | Effect |
|------|--------|
| `a sales order for customer {string} in store {string} paying with {string}` | New `ShoppingCart` for the party/store, payment type remembered |
| `a sales order for customer {string} in store {string}` | Same, defaulting payment to `CREDIT_CARD` |

### When / And — populate and place
| Step | Effect |
|------|--------|
| `the cart contains:` (data table: `productId`, `quantity`, `unitPrice`) | Adds each row as a cart line at the given price |
| `a promotion adjustment of {string} is applied` | Adds a `PROMOTION_ADJUSTMENT` order adjustment |
| `the items are split across {int} ship groups` | Creates extra ship groups and distributes quantity |
| `the order is placed` | Runs `CheckOutHelper.createOrder` (invokes `storeOrder`) |
| `the storeOrder service is invoked with no order lines` | Negative path: calls `storeOrder` directly with an empty item list |

### Then / And — assert acknowledgment
| Step | Asserts |
|------|---------|
| `the order is created successfully` | `responseMessage == success`, an `orderId` was returned |
| `the order creation is rejected with an error` | the attempt was rejected with an error message |
| `no OrderHeader is persisted for the rejected attempt` | no `orderId` was produced |

### Then / And — assert persisted DB state
| Step | Asserts (via `EntityQuery`) |
|------|------------------------------|
| `the persisted OrderHeader has status {string} and type {string}` | `OrderHeader.statusId` / `orderTypeId` |
| `the order has {int} OrderItem` | row count in `OrderItem` |
| `each OrderItem has status {string}` | every `OrderItem.statusId` |
| `the order has {int} OrderPaymentPreference of type {string}` | matching `OrderPaymentPreference` rows |
| `the order has {int} OrderItemShipGroup records` | row count in `OrderItemShipGroup` |
| `the order has an OrderAdjustment of type {string}` | an `OrderAdjustment` of that type exists |
| `at least {int} OrderStatus records exist` | `OrderStatus` row count |
| `the persisted order grand total is {string}` | `OrderHeader.grandTotal` (BigDecimal compare) |

---

## 5. The six scenarios

| Feature file | Permutation covered |
|--------------|---------------------|
| `single_item_order.feature` | one product, credit-card payment (happy path) |
| `multi_item_order.feature` | three different products in one order |
| `order_with_promotion.feature` | a promotional `OrderAdjustment` discount |
| `multiple_ship_groups.feature` | one line split across two ship groups |
| `alternate_payment_type.feature` | non-card payment (cash on delivery, `EXT_COD`) |
| `invalid_order_rejected.feature` | **negative** — `storeOrder` with no order lines is rejected |

> **Why an empty-order negative instead of a bad product id?** Feeding a non-existent product id
> into `storeOrder` triggers `countProductQuantityOrdered`, which fails on a foreign-key violation
> and poisons the transaction (a messy rollback rather than a clean validation error). Submitting an
> order with no lines exercises `storeOrder`'s own validation (`items.none`) and returns a clean
> error **before any row is written** — a deterministic, side-effect-free rejection.

Example (`single_item_order.feature`):

```gherkin
Feature: Single-item sales order

  Scenario: Customer buys one product and pays by credit card
    Given a sales order for customer "DemoCustomer" in store "9000" paying with "CREDIT_CARD"
    When the cart contains:
      | productId | quantity | unitPrice |
      | GZ-2644   | 1        | 38.40     |
    And the order is placed
    Then the order is created successfully
    And the persisted OrderHeader has status "ORDER_CREATED" and type "SALES_ORDER"
    And the order has 1 OrderItem
    And each OrderItem has status "ITEM_CREATED"
    And the order has 1 OrderPaymentPreference of type "CREDIT_CARD"
    And the persisted order grand total is "38.40"
    And at least 2 OrderStatus records exist
```

---

## 6. Running

```sh
# One-time: populate the demo/seed data the scenarios rely on (parties, products, store 9000).
./gradlew loadAll

# Run the whole order-flow harness (and the parity tests):
./gradlew test --tests 'org.apache.ofbiz.order.bdd.*'

# Or run everything:
./gradlew test
```

Expected result with data loaded: **6 scenarios / 49 steps pass**, plus the two parity tests.
Without data loaded (e.g. plain `./gradlew check` in CI), the suite **skips** rather than failing.

The Cucumber `pretty` + `summary` plugins print each scenario and a final tally to the test's
captured stdout (`build/test-results/test/TEST-...OrderFlowCucumberTest.xml`).

---

## 7. Adding a new scenario

1. **Write the `.feature`** in `applications/order/src/test/resources/features/order/` using the
   step vocabulary in section 4.
2. **Reuse existing steps** where possible. If you need a new step, add a method to
   `OrderStoreStepDefinitions` annotated with `@Given/@When/@Then/@And`. Keep methods on the `final`
   class so checkstyle stays happy.
3. **Pick deterministic prices.** Because the harness adds items with explicit unit prices and
   `triggerPriceRules = false`, `grandTotal = Σ(quantity × unitPrice) + adjustments`. Compute the
   expected total yourself and assert it with `the persisted order grand total is "..."`.
4. **Run** `./gradlew test --tests 'org.apache.ofbiz.order.bdd.*'` and
   `./gradlew checkstyleTest`.

---

## 8. Relationship to the parity framework (Phase 3)

The same `delegator`/`OfbizTestContainer` plumbing backs `OrderParitySnapshot`, which captures an
order's full entity graph and diffs two executions. A typical refactor-safety workflow:

```java
OrderParitySnapshot baseline  = OrderParitySnapshot.capture(delegator, orderIdBeforeRefactor);
OrderParitySnapshot candidate = OrderParitySnapshot.capture(delegator, orderIdAfterRefactor);
List<Difference> diffs = OrderParitySnapshot.diff(baseline, candidate);   // empty == at parity
String humanReadable   = OrderParitySnapshot.report(baseline, candidate);
```

Auto-generated surrogate ids and audit timestamps are normalised away so only meaningful business
state is compared. See `OrderParityTest` for a runnable example.
