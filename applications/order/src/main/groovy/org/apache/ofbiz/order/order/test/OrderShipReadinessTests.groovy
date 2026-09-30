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
package org.apache.ofbiz.order.order.test

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceAuthException
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

class OrderShipReadinessTests extends OFBizTestCase {

    private static final String ORDER_ID = 'TEST_SHIPRDY_ORD1'
    private static final List<String> WATCHED_ENTITIES = ['OrderHeader', 'OrderItem', 'OrderItemShipGrpInvRes', 'InventoryItem']

    OrderShipReadinessTests(String name) {
        super(name)
    }

    void testGetOrderShipReadinessHeader() {
        Map serviceResult = runShipReadiness(ORDER_ID)
        assert ServiceUtil.isSuccess(serviceResult)
        assert serviceResult.orderId == ORDER_ID
        assert serviceResult.externalId == 'TEST_SHIPRDY_EXT1'
        assert serviceResult.statusId == 'ORDER_APPROVED'
    }

    void testFullyReservedLine() {
        Map line = getLine('00001')
        assert line.hasEnoughInventory == true
        assert line.inventoryBasis == 'RESERVATION'
        assert line.productId == 'TEST_SHIPRDY_PROD'
        assert line.statusId == 'ITEM_APPROVED'
        assert line.quantityToShip == 3
        assert line.quantityReserved == 3
        assert line.quantityNotAvailable == 0
    }

    void testBackorderedLine() {
        Map line = getLine('00002')
        assert line.hasEnoughInventory == false
        assert line.inventoryBasis == 'RESERVATION'
        assert line.quantityReserved == 4
        assert line.quantityNotAvailable == 2
    }

    void testFacilityAtpEnoughLine() {
        Map line = getLine('00003')
        assert line.hasEnoughInventory == true
        assert line.inventoryBasis == 'FACILITY_ATP'
        assert line.quantityToShip == 5
        assert line.quantityReserved == 0
    }

    void testFacilityAtpNotEnoughLine() {
        Map line = getLine('00004')
        assert line.hasEnoughInventory == false
        assert line.inventoryBasis == 'FACILITY_ATP'
        assert line.quantityToShip == 20
    }

    void testNoFacilityLine() {
        Map line = getLine('00005')
        assert line.hasEnoughInventory == null
        assert line.inventoryBasis == 'UNKNOWN'
    }

    void testNoProductLine() {
        Map line = getLine('00006')
        assert line.productId == null
        assert line.hasEnoughInventory == null
        assert line.inventoryBasis == 'UNKNOWN'
    }

    void testCancelledLineExcluded() {
        List<Map> orderItems = runShipReadiness(ORDER_ID).orderItems
        assert orderItems*.orderItemSeqId == ['00001', '00002', '00003', '00004', '00005', '00006']
    }

    void testUnknownOrderId() {
        Map serviceResult = runShipReadiness('TEST_SHIPRDY_UNKNOWN')
        assert ServiceUtil.isError(serviceResult)
        assert ServiceUtil.getErrorMessage(serviceResult).contains('TEST_SHIPRDY_UNKNOWN')
    }

    void testMissingPermission() {
        GenericValue noPermissionUserLogin = getUserLogin('TEST_SHIPRDY_NOPERM')
        assert noPermissionUserLogin
        try {
            dispatcher.runSync('getOrderShipReadiness', [orderId: ORDER_ID, userLogin: noPermissionUserLogin])
            fail('getOrderShipReadiness must fail without ORDERMGR_VIEW')
        } catch (ServiceAuthException e) {
            assert e.message
        }
    }

    void testIsReadOnly() {
        Map countsBefore = countWatchedEntities()
        Map stampsBefore = fixtureStamps()
        assert ServiceUtil.isSuccess(runShipReadiness(ORDER_ID))
        assert ServiceUtil.isError(runShipReadiness('TEST_SHIPRDY_UNKNOWN'))
        assert countWatchedEntities() == countsBefore
        assert fixtureStamps() == stampsBefore
    }

    private Map runShipReadiness(String orderId) {
        return dispatcher.runSync('getOrderShipReadiness', [orderId: orderId, userLogin: userLogin])
    }

    private Map getLine(String orderItemSeqId) {
        Map serviceResult = runShipReadiness(ORDER_ID)
        assert ServiceUtil.isSuccess(serviceResult)
        Map line = serviceResult.orderItems.find { Map item -> item.orderItemSeqId == orderItemSeqId }
        assert line
        return line
    }

    private Map countWatchedEntities() {
        return WATCHED_ENTITIES.collectEntries { String entityName -> [entityName, from(entityName).queryCount()] }
    }

    private Map fixtureStamps() {
        List<GenericValue> fixtureRows = []
        fixtureRows << from('OrderHeader').where(orderId: ORDER_ID).queryOne()
        fixtureRows.addAll(from('OrderItem').where(orderId: ORDER_ID).queryList())
        fixtureRows.addAll(from('OrderItemShipGrpInvRes').where(orderId: ORDER_ID).queryList())
        fixtureRows << from('InventoryItem').where(inventoryItemId: 'TEST_SHIPRDY_INV1').queryOne()
        return fixtureRows.collectEntries { GenericValue row -> [row.getPrimaryKey().toString(), row.lastUpdatedStamp] }
    }

}
