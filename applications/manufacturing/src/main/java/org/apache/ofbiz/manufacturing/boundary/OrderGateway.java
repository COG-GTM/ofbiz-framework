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
package org.apache.ofbiz.manufacturing.boundary;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.order.order.OrderContentWrapper;
import org.apache.ofbiz.order.order.OrderReadHelper;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Outbound boundary (adapter/gateway) for the manufacturing component's dependencies on the
 * {@code order} component.
 *
 * <p>This is the single place inside manufacturing that is allowed to reference order classes
 * ({@link OrderReadHelper}, {@link OrderContentWrapper}) and order-owned service names. Manufacturing
 * business logic must route all outbound order calls through this class so the coupling on the order
 * component is explicit and centralized.</p>
 *
 * <p>{@link OrderReadHelper} and {@link OrderContentWrapper} are order-domain value/helper types that
 * flow across the boundary as data contracts (mirroring the {@code *ContentWrapper} contract). Their
 * construction is centralized here through factory methods; their public API is consumed as-is.</p>
 */
public final class OrderGateway {

    private OrderGateway() {
    }

    // ---- Order helper/value construction (compile-time dependency, centralized here) ----

    /** Routes construction of {@link OrderReadHelper#OrderReadHelper(Delegator, String)}. */
    public static OrderReadHelper makeOrderReadHelper(Delegator delegator, String orderId) {
        return new OrderReadHelper(delegator, orderId);
    }

    /** Routes construction of {@link OrderReadHelper#OrderReadHelper(GenericValue)}. */
    public static OrderReadHelper makeOrderReadHelper(GenericValue orderHeader) {
        return new OrderReadHelper(orderHeader);
    }

    /** Routes {@link OrderContentWrapper#makeOrderContentWrapper(GenericValue, HttpServletRequest)}. */
    public static OrderContentWrapper makeOrderContentWrapper(GenericValue order, HttpServletRequest request) {
        return OrderContentWrapper.makeOrderContentWrapper(order, request);
    }

    // ---- Order-owned service-engine calls ----

    public static Map<String, Object> createRequirement(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createRequirement", context);
    }

    public static Map<String, Object> updateRequirement(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("updateRequirement", context);
    }
}
