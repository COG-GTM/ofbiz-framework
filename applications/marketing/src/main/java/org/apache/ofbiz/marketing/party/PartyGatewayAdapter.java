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
import org.apache.ofbiz.party.contact.ContactHelper;
import org.apache.ofbiz.party.party.PartyHelper;
import org.apache.ofbiz.party.party.PartyWorker;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Default {@link PartyGateway} implementation.
 *
 * <p>This is the single class in the {@code marketing} component that is allowed to import
 * {@code org.apache.ofbiz.party.*}. It adapts the marketing-facing {@link PartyGateway} contract
 * onto the {@code party} component's helpers, workers and published services, keeping all
 * marketing&rarr;party coupling in one place.</p>
 */
public final class PartyGatewayAdapter implements PartyGateway {

    /** Shared singleton instance, exposed through {@link PartyGateway#getInstance()}. */
    static final PartyGateway INSTANCE = new PartyGatewayAdapter();

    private PartyGatewayAdapter() {
    }

    @Override
    public String getPartyName(Delegator delegator, String partyId, boolean lastNameFirst) {
        return PartyHelper.getPartyName(delegator, partyId, lastNameFirst);
    }

    @Override
    public String getPartyName(GenericValue partyObject, boolean lastNameFirst) {
        return PartyHelper.getPartyName(partyObject, lastNameFirst);
    }

    @Override
    public GenericValue findPartyLatestPostalAddress(String partyId, Delegator delegator) {
        return PartyWorker.findPartyLatestPostalAddress(partyId, delegator);
    }

    @Override
    public GenericValue findPartyLatestTelecomNumber(String partyId, Delegator delegator) {
        return PartyWorker.findPartyLatestTelecomNumber(partyId, delegator);
    }

    @Override
    public GenericValue findPartyLatestContactMech(String partyId, String contactMechTypeId, Delegator delegator) {
        return PartyWorker.findPartyLatestContactMech(partyId, contactMechTypeId, delegator);
    }

    @Override
    public Collection<GenericValue> getContactMech(GenericValue party, String contactMechPurposeTypeId, String contactMechTypeId,
            boolean includeOld) {
        return ContactHelper.getContactMech(party, contactMechPurposeTypeId, contactMechTypeId, includeOld);
    }

    @Override
    public Map<String, Object> createPartyEmailAddress(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createPartyEmailAddress", context);
    }

    @Override
    public Map<String, Object> createPartyIdentification(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createPartyIdentification", context);
    }
}
