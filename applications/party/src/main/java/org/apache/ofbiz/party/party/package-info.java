/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/

/**
 * Core services of the <b>party</b> bounded context — the foundational identity context for
 * {@code Party}/{@code Person}/{@code PartyGroup}, their roles and relationships, and their
 * contact mechanisms ({@code ContactMech}).
 *
 * <p>{@link org.apache.ofbiz.party.party.PartyServices} is the Java entry point for the published
 * identity services ({@code createPerson}, {@code createPartyGroup}, {@code createPartyRole},
 * {@code getPartyNameForDate}, ...). Consumers should invoke these through the service engine by
 * name rather than calling the Java methods directly.</p>
 *
 * <p>The full published contract (entities, services, and the outward dependency posture of this
 * context) is documented in {@code applications/party/PARTY_BOUNDED_CONTEXT.md}. In short: party is
 * a low-outbound-coupling context — no class here imports another business application module,
 * except {@code PartyContentWrapper}, which goes through the shared content-access seam
 * {@code org.apache.ofbiz.content.content.AbstractContentWrapper}. Cross-module data concerns such
 * as reading {@code DataResource} bytes are inverted behind framework seams (see
 * {@code org.apache.ofbiz.common.content.DataResourceContentProvider}).</p>
 */
package org.apache.ofbiz.party.party;
