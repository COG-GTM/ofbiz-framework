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

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.product.product.ProductWorker;
import org.apache.ofbiz.product.store.ProductStoreWorker;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Outbound boundary (adapter/gateway) for the manufacturing component's dependencies on the
 * {@code product} component.
 *
 * <p>This is the single place inside manufacturing that is allowed to reference product classes
 * ({@link ProductWorker}, {@link ProductStoreWorker}) and product-owned service names. Manufacturing
 * business logic must route all outbound product calls through this class so the coupling on the
 * product component is explicit and centralized. This is a pure structural boundary: every method
 * simply forwards to the underlying product worker or product-owned service, preserving behavior.</p>
 */
public final class ProductGateway {

    private ProductGateway() {
    }

    // ---- Direct (compile-time) product worker calls ----

    /** Routes {@link ProductWorker#getAggregatedInstanceId(Delegator, String, String)}. */
    public static String getAggregatedInstanceId(Delegator delegator, String aggregatedProductId, String configId)
            throws GenericEntityException {
        return ProductWorker.getAggregatedInstanceId(delegator, aggregatedProductId, configId);
    }

    /** Routes {@link ProductStoreWorker#getProductStore(String, Delegator)}. */
    public static GenericValue getProductStore(String productStoreId, Delegator delegator) {
        return ProductStoreWorker.getProductStore(productStoreId, delegator);
    }

    // ---- Product-owned service-engine calls ----

    public static Map<String, Object> getProductVariant(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("getProductVariant", context);
    }

    public static Map<String, Object> getProductCost(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("getProductCost", context);
    }

    public static Map<String, Object> getProductInventoryAvailable(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("getProductInventoryAvailable", context);
    }

    public static Map<String, Object> getInventoryAvailableByFacility(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("getInventoryAvailableByFacility", context);
    }

    public static Map<String, Object> getMktgPackagesAvailable(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("getMktgPackagesAvailable", context);
    }

    public static Map<String, Object> createInventoryItem(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createInventoryItem", context);
    }

    public static Map<String, Object> createInventoryItemDetail(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createInventoryItemDetail", context);
    }

    public static Map<String, Object> balanceInventoryItems(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("balanceInventoryItems", context);
    }

    public static Map<String, Object> createLot(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createLot", context);
    }

    public static Map<String, Object> createProductFacility(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createProductFacility", context);
    }

    public static Map<String, Object> createCostComponent(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createCostComponent", context);
    }

    public static Map<String, Object> createShipmentPackage(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createShipmentPackage", context);
    }

    public static Map<String, Object> createShipmentPackageContent(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createShipmentPackageContent", context);
    }
}
