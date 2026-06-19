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
package org.apache.ofbiz.order.bdd.parity;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.order.bdd.OfbizTestContainer;
import org.apache.ofbiz.order.shoppingcart.CheckOutHelper;
import org.apache.ofbiz.order.shoppingcart.ShoppingCart;
import org.apache.ofbiz.service.LocalDispatcher;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates and validates {@link OrderParitySnapshot}.
 *
 * <p>Two orders are created from identical input through the same order flow, a snapshot is captured
 * for each, and the diff is asserted to be empty - i.e. the persisted database state is at parity.
 * This is exactly the workflow a refactoring effort would use: capture a baseline before the change,
 * capture a candidate after, and assert there are no field-level differences.</p>
 */
class OrderParityTest {

    private static final String MODULE = OrderParityTest.class.getName();

    private String placeStandardOrder(Delegator delegator, LocalDispatcher dispatcher, GenericValue userLogin)
            throws Exception {
        ShoppingCart cart = new ShoppingCart(delegator, "9000", Locale.getDefault(), "USD");
        cart.setOrderType("SALES_ORDER");
        cart.setChannelType("WEB_SALES_CHANNEL");
        cart.setUserLogin(userLogin, dispatcher);
        cart.setProductStoreId("9000");
        cart.setOrderPartyId("DemoCustomer");
        cart.setBillToCustomerPartyId("DemoCustomer");
        cart.setShipToCustomerPartyId("DemoCustomer");
        cart.setEndUserCustomerPartyId("DemoCustomer");
        cart.setPlacingCustomerPartyId("DemoCustomer");

        int idx = cart.addItemToEnd("GZ-2644", BigDecimal.ZERO, new BigDecimal("2"), new BigDecimal("38.40"),
                null, null, "DemoCatalog", "PRODUCT_ORDER_ITEM", dispatcher,
                Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);
        cart.setItemShipGroupQty(cart.findCartItem(idx), new BigDecimal("2"), 0);
        cart.addPaymentAmount("CREDIT_CARD", cart.getGrandTotal());
        cart.setAllShippingContactMechId("9015");
        cart.setAllShipmentMethodTypeId("NEXT_DAY");
        cart.setAllCarrierPartyId("UPS");
        cart.setBillFromVendorPartyId("Company");
        cart.makeAllShipGroupInfos(dispatcher);

        CheckOutHelper checkOutHelper = new CheckOutHelper(dispatcher, delegator, cart);
        Map<String, Object> result = checkOutHelper.createOrder(userLogin);
        return (String) result.get("orderId");
    }

    @Test
    void identicalOrdersAreAtParity() throws Exception {
        assumeTrue(OfbizTestContainer.isAvailable(),
                "OFBiz runtime not available (run './gradlew loadAll' first): " + OfbizTestContainer.unavailableReason());
        Delegator delegator = OfbizTestContainer.getDelegator();
        LocalDispatcher dispatcher = OfbizTestContainer.getDispatcher();
        GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").queryOne();

        String baselineOrderId = placeStandardOrder(delegator, dispatcher, userLogin);
        String candidateOrderId = placeStandardOrder(delegator, dispatcher, userLogin);
        assertNotNull(baselineOrderId);
        assertNotNull(candidateOrderId);
        assertNotEquals(baselineOrderId, candidateOrderId, "The two executions should produce distinct order ids");

        OrderParitySnapshot baseline = OrderParitySnapshot.capture(delegator, baselineOrderId);
        OrderParitySnapshot candidate = OrderParitySnapshot.capture(delegator, candidateOrderId);

        assertTrue(baseline.totalRows() > 0, "Baseline snapshot should capture rows");

        List<OrderParitySnapshot.Difference> differences = OrderParitySnapshot.diff(baseline, candidate);
        String report = OrderParitySnapshot.report(baseline, candidate);
        Debug.logInfo(report, MODULE);
        assertTrue(differences.isEmpty(),
                "Expected the two identical orders to be at parity but found differences:\n" + report);
    }

    @Test
    void differingOrdersAreReported() throws Exception {
        assumeTrue(OfbizTestContainer.isAvailable(),
                "OFBiz runtime not available (run './gradlew loadAll' first): " + OfbizTestContainer.unavailableReason());
        Delegator delegator = OfbizTestContainer.getDelegator();
        LocalDispatcher dispatcher = OfbizTestContainer.getDispatcher();
        GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").queryOne();

        String baselineOrderId = placeStandardOrder(delegator, dispatcher, userLogin);

        ShoppingCart cart = new ShoppingCart(delegator, "9000", Locale.getDefault(), "USD");
        cart.setOrderType("SALES_ORDER");
        cart.setChannelType("WEB_SALES_CHANNEL");
        cart.setUserLogin(userLogin, dispatcher);
        cart.setProductStoreId("9000");
        cart.setOrderPartyId("DemoCustomer");
        cart.setBillToCustomerPartyId("DemoCustomer");
        cart.setShipToCustomerPartyId("DemoCustomer");
        cart.setEndUserCustomerPartyId("DemoCustomer");
        cart.setPlacingCustomerPartyId("DemoCustomer");
        int idx = cart.addItemToEnd("GZ-2644", BigDecimal.ZERO, new BigDecimal("5"), new BigDecimal("38.40"),
                null, null, "DemoCatalog", "PRODUCT_ORDER_ITEM", dispatcher,
                Boolean.FALSE, Boolean.TRUE, Boolean.TRUE, Boolean.TRUE);
        cart.setItemShipGroupQty(cart.findCartItem(idx), new BigDecimal("5"), 0);
        cart.addPaymentAmount("CREDIT_CARD", cart.getGrandTotal());
        cart.setAllShippingContactMechId("9015");
        cart.setAllShipmentMethodTypeId("NEXT_DAY");
        cart.setAllCarrierPartyId("UPS");
        cart.setBillFromVendorPartyId("Company");
        cart.makeAllShipGroupInfos(dispatcher);
        CheckOutHelper checkOutHelper = new CheckOutHelper(dispatcher, delegator, cart);
        String candidateOrderId = (String) checkOutHelper.createOrder(userLogin).get("orderId");

        OrderParitySnapshot baseline = OrderParitySnapshot.capture(delegator, baselineOrderId);
        OrderParitySnapshot candidate = OrderParitySnapshot.capture(delegator, candidateOrderId);

        List<OrderParitySnapshot.Difference> differences = OrderParitySnapshot.diff(baseline, candidate);
        assertTrue(!differences.isEmpty(), "Different orders should produce reported differences");
    }
}
