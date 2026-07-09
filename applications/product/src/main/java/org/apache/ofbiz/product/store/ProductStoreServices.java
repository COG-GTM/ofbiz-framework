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

package org.apache.ofbiz.product.store;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Thin Service Engine wrappers around {@link ProductStoreWorker} read helpers. They allow callers in other
 * components (e.g. the order component) to obtain product store information through the Service Engine instead of
 * importing the product internal Java classes directly.
 */
public class ProductStoreServices {

    /**
     * Service wrapper exposing {@link ProductStoreWorker#getProductStore(String, Delegator)}.
     */
    public static Map<String, Object> getProductStore(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        String productStoreId = (String) context.get("productStoreId");
        GenericValue productStore = ProductStoreWorker.getProductStore(productStoreId, delegator);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("productStore", productStore);
        return result;
    }

    /**
     * Service wrapper exposing {@link ProductStoreWorker#getProductStorePaymentSetting}.
     */
    public static Map<String, Object> getProductStorePaymentSetting(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        String productStoreId = (String) context.get("productStoreId");
        String paymentMethodTypeId = (String) context.get("paymentMethodTypeId");
        String paymentServiceTypeEnumId = (String) context.get("paymentServiceTypeEnumId");
        boolean anyServiceType = Boolean.TRUE.equals(context.get("anyServiceType"));
        GenericValue paymentSetting = ProductStoreWorker.getProductStorePaymentSetting(delegator, productStoreId, paymentMethodTypeId,
                paymentServiceTypeEnumId, anyServiceType);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("paymentSetting", paymentSetting);
        return result;
    }

    /**
     * Service wrapper exposing {@link ProductStoreWorker#getProductStorePaymentProperties(Delegator, String, String, String, boolean)}.
     */
    public static Map<String, Object> getProductStorePaymentProperties(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        String productStoreId = (String) context.get("productStoreId");
        String paymentMethodTypeId = (String) context.get("paymentMethodTypeId");
        String paymentServiceTypeEnumId = (String) context.get("paymentServiceTypeEnumId");
        boolean anyServiceType = Boolean.TRUE.equals(context.get("anyServiceType"));
        String paymentProperties = ProductStoreWorker.getProductStorePaymentProperties(delegator, productStoreId, paymentMethodTypeId,
                paymentServiceTypeEnumId, anyServiceType);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("paymentProperties", paymentProperties);
        return result;
    }

    /**
     * Service wrapper exposing {@link ProductStoreWorker#getProductStoreShipmentMethod}.
     */
    public static Map<String, Object> getProductStoreShipmentMethod(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        String productStoreId = (String) context.get("productStoreId");
        String shipmentMethodTypeId = (String) context.get("shipmentMethodTypeId");
        String carrierPartyId = (String) context.get("carrierPartyId");
        String carrierRoleTypeId = (String) context.get("carrierRoleTypeId");
        GenericValue shipmentMethod = ProductStoreWorker.getProductStoreShipmentMethod(delegator, productStoreId, shipmentMethodTypeId,
                carrierPartyId, carrierRoleTypeId);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("shipmentMethod", shipmentMethod);
        return result;
    }

    /**
     * Service wrapper exposing {@link ProductStoreWorker#getAvailableStoreShippingMethods}.
     */
    public static Map<String, Object> getAvailableStoreShippingMethods(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        String productStoreId = (String) context.get("productStoreId");
        GenericValue shippingAddress = (GenericValue) context.get("shippingAddress");
        List<BigDecimal> itemSizes = UtilGenerics.cast(context.get("itemSizes"));
        Map<String, BigDecimal> featureIdMap = UtilGenerics.cast(context.get("featureIdMap"));
        BigDecimal weight = (BigDecimal) context.get("weight");
        BigDecimal orderTotal = (BigDecimal) context.get("orderTotal");
        List<GenericValue> shippingMethods = ProductStoreWorker.getAvailableStoreShippingMethods(delegator, productStoreId, shippingAddress,
                itemSizes, featureIdMap, weight, orderTotal);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("shippingMethods", shippingMethods);
        return result;
    }
}
