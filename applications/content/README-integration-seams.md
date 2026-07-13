# Content component — integration seams & decoupling contract

The `content` component is a reusable CMS core (entities `DataResource`, `Content`,
`ContentAssoc`, plus keyword indexing, rendering and permission helpers). It is
consumed by many business modules. This document records the dependency-direction
contract for `content` and enumerates every integration seam so the decoupling does
not silently regress.

## Direction rule

- `content` **PROVIDES** the shared content-access seam
  (`org.apache.ofbiz.content.content.ContentWrapper`); the business modules
  (party / product / order / accounting / workeffort) **DEPEND ON** `content`.
  That is the allowed direction and must be preserved.
- `content` **MUST NOT** depend on a specific business module. Its core entities and
  Java/Groovy code must not reference `org.apache.ofbiz.party`,
  `org.apache.ofbiz.product`, `org.apache.ofbiz.order` or
  `org.apache.ofbiz.accounting`.

## Java/Groovy core: proven free of outbound business-module dependencies

The content component's Java has **zero** imports of or references to
party/product/order/accounting. Re-verify with:

```bash
# Expected: no output (no matching files)
grep -rlE "import org\.apache\.ofbiz\.(party|product|order|accounting)\." \
    applications/content/src/main/java

# Sibling-package histogram: only framework + content packages appear
grep -rhoE "org\.apache\.ofbiz\.[a-z]+\." applications/content/src/main/java \
    | sort | uniq -c | sort -rn
# 305 base   214 entity   95 content   82 service   25 widget
#  24 webapp  13 security  10 minilang   4 common
```

This is enforced automatically by the unit test
`org.apache.ofbiz.content.ContentOutboundCouplingTest`
(`applications/content/src/test/java/...`), which runs under `./gradlew test` (no DB
required) and fails the build if such a reference is ever added.

## Runtime / config seams

Much OFBiz coupling is not visible in Java imports. Below is every remaining
content→business-module edge that is expressed in configuration.

### 1. Entity-ECA keyword indexing — INVERTED (fixed)

Previously `applications/content/entitydef/eecas.xml` declared ECAs on junction
entities owned by other domains (`ProductContent`, `ProductCategoryContent`,
`PartyContent`, `WorkEffortContent`) that call content's `indexContentKeywords`.
That made content reach out to product/party/workeffort entities.

These ECAs were moved to the owning modules, inverting the dependency (each module
already depends on content, so it may reference the `indexContentKeywords` content
service):

| Entity                  | Now declared in                              |
| ----------------------- | -------------------------------------------- |
| `ProductContent`        | `applications/product/entitydef/eecas.xml`   |
| `ProductCategoryContent`| `applications/product/entitydef/eecas.xml`   |
| `PartyContent`          | `applications/party/entitydef/eecas.xml`     |
| `WorkEffortContent`     | `applications/workeffort/entitydef/eecas.xml`|

`content/entitydef/eecas.xml` now references only content-owned entities
(`Content*`, `ElectronicText`, `WebSiteContent`). Behavior is identical: entity ECAs
are registered in a single global registry regardless of which component declares
them.

### 2. Party role-integrity seam — documented (kept)

`applications/content/servicedef/secas.xml` invokes the generic party service
`ensurePartyRole` from the SECAs on `createContentRole` and `createDataResourceRole`.
This exists only for the optional *role-linking* entities `ContentRole` /
`DataResourceRole`, whose `partyId`+`roleTypeId` foreign keys require a matching
`PartyRole` row before insert. It is inherent to those party-integration entities
and is **not** part of the CMS core (`DataResource` / `Content` / `ContentAssoc`),
which remains self-contained. It is retained to preserve referential integrity and
existing behavior; if it must be decoupled further, replace `ensurePartyRole` with a
configurable "ensure role" service name resolved from a property.

### 3. Webapp lookup screens — documented (kept)

`applications/content/webapp/content/WEB-INF/controller.xml` has `view-map` entries
that render lookup pop-ups owned by other components:

- party: `LookupUserLoginAndPartyDetails`, `LookupPerson`,
  `LookupPartyAndUserLoginAndPerson`, `LookupPartyName`
  (`component://party/widget/partymgr/LookupScreens.xml`)
- product: `LookupProductFeature`
  (`component://product/widget/catalog/LookupScreens.xml`)
- workeffort: `LookupWorkEffort` (`component://workeffort/...`)
- ecommerce: blog response views (`component://ecommerce/widget/blog/...`)

These are admin-UI conveniences referenced by content forms via absolute
`component://` screen paths — the standard OFBiz cross-app lookup mechanism. They are
UI-only (no Java/service dependency) and are retained to preserve the existing admin
screens. Because a `component://` reference is resolved lazily at render time, it does
not create a startup/classpath dependency; if content is deployed without those
components the affected lookup pop-ups are simply unavailable.

## Validation

- `./gradlew compileJava` — compiles.
- `./gradlew test` — runs `ContentOutboundCouplingTest` (the decoupling guard) plus
  the other unit tests.
- Content integration tests (`org.apache.ofbiz.content.content.ContentTests`) run via
  the OFBiz test runner (`./gradlew loadAll ofbiz --test`).
