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
package org.apache.ofbiz.marketing.party;

import java.util.Collection;
import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Boundary between the {@code marketing} component and the {@code party} component.
 *
 * <p>Every piece of {@code party}-directed coupling that used to be scattered across marketing
 * (static calls into {@code PartyHelper}/{@code PartyWorker}/{@code ContactHelper} and hard-coded
 * {@code party} service names passed to the dispatcher) is expressed here as a small, explicit
 * contract. Marketing code depends only on this interface; the single implementation
 * ({@link PartyGatewayAdapter}) is the only class in the marketing component allowed to import
 * {@code org.apache.ofbiz.party.*}.</p>
 *
 * <p>Obtain the singleton with {@link #getInstance()} from Java, Groovy and widget expressions.</p>
 */
public interface PartyGateway {

    /**
     * @return the shared {@link PartyGateway} implementation.
     */
    static PartyGateway getInstance() {
        return PartyGatewayAdapter.INSTANCE;
    }

    /**
     * Resolve a party's display name from its id.
     * @param delegator the delegator
     * @param partyId the party id
     * @param lastNameFirst whether to render the last name first
     * @return the formatted party name (or the {@code partyId} when the party cannot be found)
     */
    String getPartyName(Delegator delegator, String partyId, boolean lastNameFirst);

    /**
     * Resolve a party's display name from a party/person/group value.
     * @param partyObject the Party, Person or PartyGroup value
     * @param lastNameFirst whether to render the last name first
     * @return the formatted party name
     */
    String getPartyName(GenericValue partyObject, boolean lastNameFirst);

    /**
     * @param partyId the party id
     * @param delegator the delegator
     * @return the party's latest postal address, or {@code null}
     */
    GenericValue findPartyLatestPostalAddress(String partyId, Delegator delegator);

    /**
     * @param partyId the party id
     * @param delegator the delegator
     * @return the party's latest telecom number, or {@code null}
     */
    GenericValue findPartyLatestTelecomNumber(String partyId, Delegator delegator);

    /**
     * @param partyId the party id
     * @param contactMechTypeId the contact mech type id to look up
     * @param delegator the delegator
     * @return the party's latest contact mech of the given type, or {@code null}
     */
    GenericValue findPartyLatestContactMech(String partyId, String contactMechTypeId, Delegator delegator);

    /**
     * @param party the party value to inspect
     * @param contactMechPurposeTypeId the purpose to filter on (may be {@code null})
     * @param contactMechTypeId the contact mech type to filter on (may be {@code null})
     * @param includeOld whether to include expired contact mechs
     * @return the matching contact mechs, or {@code null} when {@code party} is {@code null}
     */
    Collection<GenericValue> getContactMech(GenericValue party, String contactMechPurposeTypeId, String contactMechTypeId,
            boolean includeOld);

    /**
     * Invoke the {@code party} component's {@code createPartyEmailAddress} service.
     * @param dispatcher the dispatcher to run the service on
     * @param context the service input
     * @return the service result
     * @throws GenericServiceException if the service invocation fails
     */
    Map<String, Object> createPartyEmailAddress(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException;

    /**
     * Invoke the {@code party} component's {@code createPartyIdentification} service.
     * @param dispatcher the dispatcher to run the service on
     * @param context the service input
     * @return the service result
     * @throws GenericServiceException if the service invocation fails
     */
    Map<String, Object> createPartyIdentification(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException;
}
