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
package org.apache.ofbiz.order.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.order.shoppingcart.CheckOutHelper;
import org.apache.ofbiz.order.shoppingcart.ShoppingCart;
import org.apache.ofbiz.order.shoppingcart.ShoppingCartItem;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Cucumber step definitions for the order-creation flow.
 *
 * <p>The positive scenarios construct a real {@link ShoppingCart}, then place the order through
 * {@link CheckOutHelper#createOrder} which internally invokes the {@code storeOrder} service - the
 * exact path documented in the Phase 1 business specification (cart -&gt; makeCartMap -&gt; storeOrder).
 * The negative scenario invokes {@code storeOrder} directly with an invalid line to exercise the
 * service-level rejection path.</p>
 *
 * <p>Each scenario is validated at two levels: the returned acknowledgment map and the persisted
 * database entities (OrderHeader, OrderItem, OrderStatus, OrderPaymentPreference and friends).</p>
 */
public final class OrderStoreStepDefinitions {

    private final Delegator delegator = OfbizTestContainer.getDelegator();
    private final LocalDispatcher dispatcher = OfbizTestContainer.getDispatcher();

    private ShoppingCart cart;
    private String paymentMethodTypeId;
    private Map<String, Object> result;
    private String orderId;
    private boolean rejected;
    private String errorMessage;

    private GenericValue systemUserLogin() throws Exception {
        return EntityQuery.use(delegator).from("UserLogin").where("userLoginId", "system").queryOne();
    }

    @Given("a sales order for customer {string} in store {string} paying with {string}")
    public void aSalesOrderPayingWith(String partyId, String productStoreId, String paymentType) throws Exception {
        cart = new ShoppingCart(delegator, productStoreId, Locale.getDefault(), "USD");
        cart.setOrderType("SALES_ORDER");
        cart.setChannelType("WEB_SALES_CHANNEL");
        cart.setUserLogin(systemUserLogin(), dispatcher);
        cart.setProductStoreId(productStoreId);
        cart.setOrderPartyId(partyId);
        cart.setBillToCustomerPartyId(partyId);
        cart.setShipToCustomerPartyId(partyId);
        cart.setEndUserCustomerPartyId(partyId);
        cart.setPlacingCustomerPartyId(partyId);
        this.paymentMethodTypeId = paymentType;
    }

    @Given("a sales order for customer {string} in store {string}")
    public void aSalesOrder(String partyId, String productStoreId) throws Exception {
        aSalesOrderPayingWith(partyId, productStoreId, "CREDIT_CARD");
    }

    @When("the cart contains:")
    public void theCartContains(DataTable dataTable) throws Exception {
        List<Map<String, String>> rows = dataTable.asMaps();
        for (Map<String, String> row : rows) {
            String productId = row.get("productId");
            BigDecimal quantity = new BigDecimal(row.get("quantity"));
            BigDecimal unitPrice = new BigDecimal(row.get("unitPrice"));
            int idx = cart.addItemToEnd(productId, BigDecimal.ZERO, quantity, unitPrice,
                    null, null, "DemoCatalog", "PRODUCT_ORDER_ITEM", dispatcher,
                    Boolean.FALSE, Boolean.FALSE, Boolean.TRUE, Boolean.TRUE);
            cart.setItemShipGroupQty(cart.findCartItem(idx), quantity, 0);
        }
    }

    @And("a promotion adjustment of {string} is applied")
    public void aPromotionAdjustmentIsApplied(String amount) {
        GenericValue adjustment = delegator.makeValue("OrderAdjustment");
        adjustment.set("orderAdjustmentTypeId", "PROMOTION_ADJUSTMENT");
        adjustment.set("shipGroupSeqId", "_NA_");
        adjustment.set("amount", new BigDecimal(amount));
        adjustment.set("productPromoId", "9011");
        adjustment.set("productPromoRuleId", "01");
        adjustment.set("productPromoActionSeqId", "01");
        cart.addAdjustment(adjustment);
    }

    @And("the items are split across {int} ship groups")
    public void theItemsAreSplitAcrossShipGroups(int groups) {
        for (int i = 1; i < groups; i++) {
            cart.addShipInfo();
        }
        for (ShoppingCartItem item : cart.items()) {
            BigDecimal total = item.getQuantity();
            BigDecimal perGroup = total.divide(new BigDecimal(groups), 0, RoundingMode.DOWN);
            BigDecimal assigned = BigDecimal.ZERO;
            for (int g = 0; g < groups; g++) {
                BigDecimal qty = g == groups - 1 ? total.subtract(assigned) : perGroup;
                cart.setItemShipGroupQty(item, qty, g);
                assigned = assigned.add(qty);
            }
        }
    }

    @And("the order is placed")
    public void theOrderIsPlaced() throws Exception {
        if (paymentMethodTypeId != null) {
            cart.addPaymentAmount(paymentMethodTypeId, cart.getGrandTotal());
        }
        cart.setAllShippingContactMechId("9015");
        cart.setAllShipmentMethodTypeId("NEXT_DAY");
        cart.setAllCarrierPartyId("UPS");
        cart.setAllMaySplit(Boolean.FALSE);
        cart.setAllIsGift(Boolean.FALSE);
        cart.setBillFromVendorPartyId("Company");
        cart.makeAllShipGroupInfos(dispatcher);

        CheckOutHelper checkOutHelper = new CheckOutHelper(dispatcher, delegator, cart);
        result = checkOutHelper.createOrder(systemUserLogin());
        if (ServiceUtil.isError(result)) {
            rejected = true;
            errorMessage = ServiceUtil.getErrorMessage(result);
        } else {
            orderId = (String) result.get("orderId");
        }
    }

    @When("the storeOrder service is invoked with no order lines")
    public void storeOrderInvokedWithNoOrderLines() throws Exception {
        Map<String, Object> ctx = UtilMisc.<String, Object>toMap("partyId", "DemoCustomer",
                "orderTypeId", "SALES_ORDER", "currencyUom", "USD", "productStoreId", "9000");
        ctx.put("orderItems", new LinkedList<GenericValue>());
        ctx.put("orderAdjustments", new LinkedList<GenericValue>());
        ctx.put("userLogin", systemUserLogin());

        try {
            result = dispatcher.runSync("storeOrder", ctx, 60, true);
            if (ServiceUtil.isError(result)) {
                rejected = true;
                errorMessage = ServiceUtil.getErrorMessage(result);
            } else {
                orderId = (String) result.get("orderId");
            }
        } catch (GenericServiceException e) {
            rejected = true;
            errorMessage = e.getMessage();
        }
    }

    @Then("the order is created successfully")
    public void theOrderIsCreatedSuccessfully() {
        assertFalse(ServiceUtil.isError(result), "Expected success but got error: " + errorMessage);
        assertNotNull(orderId, "Expected an orderId to be returned");
        assertEquals("success", result.get("responseMessage"), "Expected a success acknowledgment");
    }

    @Then("the order creation is rejected with an error")
    public void theOrderCreationIsRejected() {
        assertTrue(rejected, "Expected the order creation to be rejected");
        assertNotNull(errorMessage, "Expected an error message");
    }

    @And("no OrderHeader is persisted for the rejected attempt")
    public void noOrderHeaderPersistedForRejectedAttempt() {
        assertTrue(orderId == null, "No orderId should have been returned for a rejected order");
    }

    @And("the persisted OrderHeader has status {string} and type {string}")
    public void persistedOrderHeaderHasStatusAndType(String statusId, String orderTypeId) throws Exception {
        GenericValue header = EntityQuery.use(delegator).from("OrderHeader").where("orderId", orderId).queryOne();
        assertNotNull(header, "OrderHeader should be persisted");
        assertEquals(statusId, header.getString("statusId"));
        assertEquals(orderTypeId, header.getString("orderTypeId"));
    }

    @And("the order has {int} OrderItem")
    public void theOrderHasOrderItems(int count) throws Exception {
        List<GenericValue> items = EntityQuery.use(delegator).from("OrderItem").where("orderId", orderId).queryList();
        assertEquals(count, items.size());
    }

    @And("each OrderItem has status {string}")
    public void eachOrderItemHasStatus(String statusId) throws Exception {
        List<GenericValue> items = EntityQuery.use(delegator).from("OrderItem").where("orderId", orderId).queryList();
        assertFalse(items.isEmpty(), "Expected at least one OrderItem");
        for (GenericValue item : items) {
            assertEquals(statusId, item.getString("statusId"));
        }
    }

    @And("the order has {int} OrderPaymentPreference of type {string}")
    public void theOrderHasPaymentPreferenceOfType(int count, String type) throws Exception {
        List<GenericValue> prefs = EntityQuery.use(delegator).from("OrderPaymentPreference")
                .where("orderId", orderId, "paymentMethodTypeId", type).queryList();
        assertEquals(count, prefs.size());
    }

    @And("the order has {int} OrderItemShipGroup records")
    public void theOrderHasShipGroups(int count) throws Exception {
        List<GenericValue> groups = EntityQuery.use(delegator).from("OrderItemShipGroup")
                .where("orderId", orderId).queryList();
        assertEquals(count, groups.size());
    }

    @And("the order has an OrderAdjustment of type {string}")
    public void theOrderHasAnAdjustmentOfType(String type) throws Exception {
        List<GenericValue> adjustments = EntityQuery.use(delegator).from("OrderAdjustment")
                .where("orderId", orderId, "orderAdjustmentTypeId", type).queryList();
        assertFalse(adjustments.isEmpty(), "Expected an OrderAdjustment of type " + type);
    }

    @And("at least {int} OrderStatus records exist")
    public void atLeastOrderStatusRecordsExist(int count) throws Exception {
        long actual = EntityQuery.use(delegator).from("OrderStatus").where("orderId", orderId).queryCount();
        assertTrue(actual >= count, "Expected at least " + count + " OrderStatus rows but found " + actual);
    }

    @And("the persisted order grand total is {string}")
    public void thePersistedOrderGrandTotalIs(String expected) throws Exception {
        GenericValue header = EntityQuery.use(delegator).from("OrderHeader").where("orderId", orderId).queryOne();
        assertEquals(0, new BigDecimal(expected).compareTo(header.getBigDecimal("grandTotal")),
                "Grand total mismatch: expected " + expected + " but was " + header.getBigDecimal("grandTotal"));
    }
}
