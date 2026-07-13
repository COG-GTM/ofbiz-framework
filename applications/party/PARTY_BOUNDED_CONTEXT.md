<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# The `party` Bounded Context — Published API

`party` is a **foundational identity context**: it owns the entities and services that model
individuals and organizations (`Party`, `Person`, `PartyGroup`), their roles and relationships,
and the ways to reach them (`ContactMech` and friends). Nearly every other business module
consumes party; party in turn is meant to be **low-outbound-coupling** — changeable without
chasing dependencies into other business modules.

This document is the *published contract*: the surface other modules are meant to depend on. If
it is not listed here, treat it as internal to party and subject to change.

---

## Published entities

Party's entity model is defined in `applications/datamodel/entitydef/party-entitymodel.xml`. The
identity core that other modules may reference:

| Entity | Purpose |
| --- | --- |
| `Party` | Abstract identity — the shared PK (`partyId`) for a person or a group. |
| `Person` | Individual-specific attributes for a `Party`. |
| `PartyGroup` | Organization/group-specific attributes for a `Party`. |
| `PartyRole` / `RoleType` | The roles a party plays (customer, supplier, employee, ...). |
| `PartyRelationship` / `PartyRelationshipType` | Relationships between parties. |
| `PartyStatus`, `PartyType`, `PartyClassificationGroup` | Lifecycle, typing and classification. |
| `ContactMech` / `ContactMechType` | Generic container for a way to reach a party. |
| `PostalAddress`, `TelecomNumber`, `ContactMech` (email `infoString`) | Concrete contact mechanisms. |
| `PartyContactMech`, `PartyContactMechPurpose` | Association of contact mechanisms to a party and their purpose. |
| `PartyContent`, `PartyContentType` | Association of `Content` to a party (see the content seam below). |

Consumers should reference these entities and their **primary/foreign keys** (`partyId`,
`roleTypeId`, `contactMechId`, ...). Party does **not** publish its internal view entities as a
contract.

---

## Published services

Party exposes ~180 services (`applications/party/servicedef/`). The core, stable surface other
modules build on (non-exhaustive):

**Identity**
- `createPerson`, `updatePerson`, `createPersonAndUserLogin`
- `createPartyGroup`, `updatePartyGroup`
- `createParty*` role/relationship helpers: `createPartyRole`, `createPartyRelationship`,
  `createPartyRelationshipAndRole`
- `getPartyNameForDate` / `getPartyNameForDateArray` — canonical display name resolution
- `ensurePartyRole`

**Contact mechanisms**
- `createPartyContactMech`, `createPartyContactMechPurpose`, `createPartyContactMechs`
- `createPartyPostalAddress`, `createPartyTelecomNumber`, `createPartyEmailAddress`,
  `createUpdatePartyEmailAddress`

**Content**
- `createPartyContent`, `createPartyTextContent` — attach `Content` to a party.

The Java implementation of the identity core lives in
`applications/party/src/main/java/org/apache/ofbiz/party/party/PartyServices.java`
(`createPerson` et al.). Prefer invoking these **through the service engine** (by service name)
rather than calling the Java methods directly.

---

## Dependency posture (outbound)

The goal for this context is that party does **not** reach *into* other business modules. Two
Java-level outbound couplings existed and have been removed/inverted:

1. **`PartyContentWrapper`** now extends the shared content-access seam
   `org.apache.ofbiz.content.content.AbstractContentWrapper` (owned by the `content` component).
   Party depending on the *content seam* is the accepted, explicit direction — the point is that
   the localized-content plumbing goes through the shared seam instead of being copy-pasted into
   party. All existing public `static` helpers on `PartyContentWrapper`
   (`getPartyContentAsText`, `getFirstPartyContentByType`, `getPartyContentTextList`, ...) keep
   their signatures for external callers.

2. **`CommunicationEventServices`** no longer imports `content.data.DataResourceWorker`. The
   email/notification data-resource concern is now *provided to* party through the framework seam
   `org.apache.ofbiz.common.content.DataResourceContentProvider` (in `framework/common`),
   resolved at runtime via `DataResourceContentProviderFactory` (a `ServiceLoader` lookup,
   mirroring `AuthHelper`). The `content` component contributes the implementation
   (`DataResourceContentProviderImpl`) via `META-INF/services`, so the dependency direction is
   one-way: **`content` implements the framework seam; party depends only on the framework seam.**

After these changes, no class under `applications/party/src/main/java` imports another business
application module except `PartyContentWrapper` (the accepted content seam direction).

### Required collaborator (runtime)

Because coupling #2 is inverted rather than removed outright, party's `sendCommEventAsEmail`
service requires a `DataResourceContentProvider` implementation to be present on the classpath at
runtime (the `content` component supplies one). This is an explicit, documented collaborator
rather than a hidden compile-time dependency.

### Known non-Java (runtime/UI) couplings — out of scope, documented for transparency

These are runtime references that are **not** Java imports and are intentionally left in place:

- **Admin UI composition** — the `partymgr` webapp screens/forms embed screens from
  `order`, `accounting`, `humanres`, `workeffort`, `product`, `marketing` and `content` via
  `component://...` includes (e.g. order history, billing accounts, employment apps shown on a
  party profile). This is view-layer composition in the management console, not core party logic.
- **`createCommEventWorkEffort`** — a party service whose implementation `location` points at
  `component://workeffort/minilang/...`. A runtime (service-definition) coupling; not a Java
  import.

---

## How to validate

- Compile: `./gradlew compileJava`
- Tests: `./gradlew test` (party tests in particular).
