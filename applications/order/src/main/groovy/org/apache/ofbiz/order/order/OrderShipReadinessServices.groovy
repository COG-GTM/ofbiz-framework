/*
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
*/
package org.apache.ofbiz.order.order

import org.apache.ofbiz.entity.GenericValue

/*
 * Gets the order status and, for each order line not cancelled, whether there is enough inventory to ship it.
 * Read-only: only entity queries and read-only services are used.
 */
Map getOrderShipReadiness() {
    GenericValue orderHeader = from('OrderHeader').where(orderId: parameters.orderId).queryOne()
    if (!orderHeader) {
        return error(label('OrderErrorUiLabels', 'OrderOrderIdDoesNotExists', [parameters: parameters]))
    }
    List<Map> orderItems = from('OrderItem')
            .where(orderId: orderHeader.orderId)
            .orderBy('orderItemSeqId')
            .queryList()
            .findAll { GenericValue orderItem -> orderItem.statusId != 'ITEM_CANCELLED' }
            .collect { GenericValue orderItem -> getOrderItemShipReadiness(orderItem) }
    return success([orderId: orderHeader.orderId,
                    externalId: orderHeader.externalId,
                    statusId: orderHeader.statusId,
                    orderItems: orderItems])
}

private Map getOrderItemShipReadiness(GenericValue orderItem) {
    BigDecimal quantityToShip = (orderItem.quantity ?: BigDecimal.ZERO) - (orderItem.cancelQuantity ?: BigDecimal.ZERO)
    List<GenericValue> reservations = from('OrderItemShipGrpInvRes')
            .where(orderId: orderItem.orderId, orderItemSeqId: orderItem.orderItemSeqId)
            .queryList()
    BigDecimal quantityReserved = reservations.sum(BigDecimal.ZERO) { GenericValue res -> res.quantity ?: BigDecimal.ZERO }
    BigDecimal quantityNotAvailable = reservations.sum(BigDecimal.ZERO) { GenericValue res -> res.quantityNotAvailable ?: BigDecimal.ZERO }

    Boolean hasEnoughInventory = null
    String inventoryBasis = 'UNKNOWN'
    if (reservations) {
        hasEnoughInventory = quantityNotAvailable == BigDecimal.ZERO && quantityReserved >= quantityToShip
        inventoryBasis = 'RESERVATION'
    } else {
        String facilityId = getOrderItemFacilityId(orderItem)
        if (facilityId && orderItem.productId) {
            Map inventory = run service: 'getInventoryAvailableByFacility', with: [productId: orderItem.productId,
                                                                                facilityId: facilityId]
            hasEnoughInventory = inventory.availableToPromiseTotal >= quantityToShip
            inventoryBasis = 'FACILITY_ATP'
        }
    }
    return [orderItemSeqId: orderItem.orderItemSeqId,
            productId: orderItem.productId,
            statusId: orderItem.statusId,
            quantityToShip: quantityToShip,
            quantityReserved: quantityReserved,
            quantityNotAvailable: quantityNotAvailable,
            hasEnoughInventory: hasEnoughInventory,
            inventoryBasis: inventoryBasis]
}

/*
 * Returns the facility of the ship group(s) of the order item, or null when there is none or several distinct ones
 */
private String getOrderItemFacilityId(GenericValue orderItem) {
    List<String> shipGroupSeqIds = from('OrderItemShipGroupAssoc')
            .where(orderId: orderItem.orderId, orderItemSeqId: orderItem.orderItemSeqId)
            .queryList()*.shipGroupSeqId
    if (!shipGroupSeqIds) {
        return null
    }
    List<String> facilityIds = shipGroupSeqIds.collect { String shipGroupSeqId ->
        from('OrderItemShipGroup')
                .where(orderId: orderItem.orderId, shipGroupSeqId: shipGroupSeqId)
                .queryOne()?.facilityId
    }.unique()
    return facilityIds.size() == 1 ? facilityIds[0] : null
}
