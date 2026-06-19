# Order-flow BDD harness & parity framework

This directory documents a three-phase deliverable that traces, exercises, and guards the OFBiz
order-creation flow (`CheckOutEvents.createOrder` -> `CheckOutHelper.createOrder` -> `storeOrder` /
`OrderServices.createOrder`).

## Phase 1 - Business specification

[`ORDER_FLOW_SPECIFICATION.md`](ORDER_FLOW_SPECIFICATION.md) is a plain-English specification of the
full order flow: the call tree, what is persisted at each step, every branching point, the SECAs that
fire automatically, and a stakeholder review checklist.

## Phase 2 - Cucumber (BDD) + JUnit test harness

See [`CUCUMBER_JUNIT_HARNESS.md`](CUCUMBER_JUNIT_HARNESS.md) for the full harness documentation
(architecture, the step-definition vocabulary, the six scenarios, how to run, and how to add new
scenarios).

Located under `applications/order/src/test`:

- **Feature files** (`src/test/resources/features/order/*.feature`) - six representative scenarios:
  single-item, multi-item, promotion/adjustment, multiple ship groups, alternate payment type
  (cash-on-delivery), and a negative/rejection scenario.
- **Step definitions** (`OrderStoreStepDefinitions`) - build a real `ShoppingCart`, place the order
  through `CheckOutHelper.createOrder` (which invokes the `storeOrder` service), and validate at two
  levels: (a) the returned acknowledgment map and (b) the persisted entities (`OrderHeader`,
  `OrderItem`, `OrderStatus`, `OrderPaymentPreference`, `OrderItemShipGroup`, `OrderAdjustment`).
- **Runner** (`OrderFlowCucumberTest`) - a JUnit 5 test that runs the scenarios through Cucumber's own
  runtime, so it does not depend on the JUnit Platform engine version OFBiz pins.
- **Bootstrap** (`OfbizTestContainer`) - boots an in-process delegator + dispatcher once per JVM.

## Phase 3 - Parity validation framework

`OrderParitySnapshot` (in `org.apache.ofbiz.order.bdd.parity`) captures a baseline snapshot of an
order's `OrderHeader` plus every related child entity, and diffs it against a second execution,
reporting field-level differences. Auto-generated surrogate ids and audit timestamps are normalised
away so only meaningful business state is compared. `OrderParityTest` demonstrates capturing a
baseline and a candidate from the same input and asserting parity.

## Running

```sh
./gradlew loadAll      # one-time: populate the demo/seed data the scenarios rely on
./gradlew test         # runs the harness (and every other unit test)
# or just this harness:
./gradlew test --tests 'org.apache.ofbiz.order.bdd.*'
```

When the runtime cannot be booted (for example a `./gradlew check` run on a machine where `loadAll`
has not been run), the scenarios skip gracefully instead of failing the build.
