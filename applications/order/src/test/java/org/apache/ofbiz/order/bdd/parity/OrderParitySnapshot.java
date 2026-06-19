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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;

/**
 * Reusable parity-validation utility for the order-creation flow.
 *
 * <p>A snapshot captures the {@code OrderHeader} plus every related child entity written for a given
 * order id. Two snapshots (for example one produced by the current order flow and one produced by a
 * refactored version, both fed the same input) can then be {@link #diff(OrderParitySnapshot,
 * OrderParitySnapshot) diffed} to obtain a list of field-level differences.</p>
 *
 * <p>Because each execution generates a different {@code orderId} and fresh audit timestamps, those
 * volatile fields are normalised away before comparison so that only the meaningful, business-level
 * persisted state is compared. The set of ignored fields is configurable.</p>
 *
 * <p>This class is self-contained: it only needs a {@link Delegator} and an order id, so it can be
 * driven from a test, a refactoring harness, or an ad-hoc script.</p>
 */
public final class OrderParitySnapshot {

    /** Order-related entities captured by a snapshot, in a stable order. */
    public static final List<String> ORDER_ENTITY_NAMES = Collections.unmodifiableList(Arrays.asList(
            "OrderHeader",
            "OrderItem",
            "OrderStatus",
            "OrderItemShipGroup",
            "OrderItemShipGroupAssoc",
            "OrderAdjustment",
            "OrderPaymentPreference",
            "OrderContactMech",
            "OrderRole",
            "OrderItemPriceInfo",
            "OrderItemBilling",
            "OrderProductPromoUse",
            "OrderProductPromoCode",
            "OrderTerm",
            "OrderAttribute",
            "OrderItemAttribute",
            "OrderNote",
            "OrderItemShipGrpInvRes"));

    /** Fields that legitimately differ between two independent executions and must not be compared. */
    public static final Set<String> DEFAULT_IGNORED_FIELDS = Collections.unmodifiableSet(new TreeSet<>(Arrays.asList(
            // The order id itself is regenerated on every execution.
            "orderId",
            // Auto-generated surrogate sequence ids that legitimately differ between two executions.
            "orderStatusId",
            "orderAdjustmentId",
            "orderPaymentPreferenceId",
            "orderItemPriceInfoId",
            "orderContactMechId",
            // Audit / event timestamps.
            "orderDate",
            "entryDate",
            "statusDatetime",
            "createdDate",
            "createdDatetime",
            "reservedDatetime",
            "authDate",
            "settlementDate",
            "estimatedShipDate",
            "estimatedDeliveryDate",
            "lastUpdatedStamp",
            "lastUpdatedTxStamp",
            "createdStamp",
            "createdTxStamp")));

    private final String orderId;
    private final Map<String, List<Map<String, Object>>> entities;
    private final Set<String> ignoredFields;

    private OrderParitySnapshot(String orderId, Map<String, List<Map<String, Object>>> entities, Set<String> ignoredFields) {
        this.orderId = orderId;
        this.entities = entities;
        this.ignoredFields = ignoredFields;
    }

    /**
     * Captures a snapshot of the order and all its child entities using {@link #DEFAULT_IGNORED_FIELDS}.
     * @param delegator the entity engine delegator
     * @param orderId the order to snapshot
     * @return the captured snapshot
     * @throws GenericEntityException if a query fails
     */
    public static OrderParitySnapshot capture(Delegator delegator, String orderId) throws GenericEntityException {
        return capture(delegator, orderId, DEFAULT_IGNORED_FIELDS);
    }

    /**
     * Captures a snapshot of the order and all its child entities.
     * @param delegator the entity engine delegator
     * @param orderId the order to snapshot
     * @param ignoredFields fields to drop before comparison (e.g. surrogate ids and timestamps)
     * @return the captured snapshot
     * @throws GenericEntityException if a query fails
     */
    public static OrderParitySnapshot capture(Delegator delegator, String orderId, Set<String> ignoredFields)
            throws GenericEntityException {
        Map<String, List<Map<String, Object>>> captured = new LinkedHashMap<>();
        for (String entityName : ORDER_ENTITY_NAMES) {
            if (!delegator.getModelReader().getEntityNames().contains(entityName)) {
                continue;
            }
            List<GenericValue> rows = EntityQuery.use(delegator).from(entityName).where("orderId", orderId).queryList();
            List<Map<String, Object>> normalisedRows = new ArrayList<>();
            for (GenericValue row : rows) {
                normalisedRows.add(toComparableMap(row, ignoredFields));
            }
            normalisedRows.sort(OrderParitySnapshot::compareRows);
            captured.put(entityName, normalisedRows);
        }
        return new OrderParitySnapshot(orderId, captured, ignoredFields);
    }

    private static Map<String, Object> toComparableMap(GenericValue row, Set<String> ignoredFields) {
        Map<String, Object> map = new TreeMap<>();
        for (String fieldName : row.getModelEntity().getAllFieldNames()) {
            if (ignoredFields.contains(fieldName)) {
                continue;
            }
            Object value = row.get(fieldName);
            map.put(fieldName, value == null ? null : value.toString());
        }
        return map;
    }

    private static int compareRows(Map<String, Object> left, Map<String, Object> right) {
        return left.toString().compareTo(right.toString());
    }

    /** @return the order id this snapshot was captured from. */
    public String getOrderId() {
        return orderId;
    }

    /** @return the captured, normalised entity rows keyed by entity name. */
    public Map<String, List<Map<String, Object>>> getEntities() {
        return entities;
    }

    /** @return total number of captured rows across all entities. */
    public int totalRows() {
        return entities.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Computes the field-level differences between a baseline and a candidate snapshot.
     * @param baseline the reference snapshot (e.g. the current order flow)
     * @param candidate the snapshot to compare (e.g. the refactored order flow)
     * @return an ordered list of differences; empty when the two snapshots are at parity
     */
    public static List<Difference> diff(OrderParitySnapshot baseline, OrderParitySnapshot candidate) {
        List<Difference> differences = new ArrayList<>();
        Set<String> entityNames = new TreeSet<>();
        entityNames.addAll(baseline.entities.keySet());
        entityNames.addAll(candidate.entities.keySet());

        for (String entityName : entityNames) {
            List<Map<String, Object>> baseRows = baseline.entities.getOrDefault(entityName, Collections.emptyList());
            List<Map<String, Object>> candRows = candidate.entities.getOrDefault(entityName, Collections.emptyList());

            if (baseRows.size() != candRows.size()) {
                differences.add(new Difference(entityName, -1, "<rowCount>",
                        String.valueOf(baseRows.size()), String.valueOf(candRows.size())));
            }

            int rowCount = Math.max(baseRows.size(), candRows.size());
            for (int i = 0; i < rowCount; i++) {
                Map<String, Object> baseRow = i < baseRows.size() ? baseRows.get(i) : null;
                Map<String, Object> candRow = i < candRows.size() ? candRows.get(i) : null;
                if (baseRow == null) {
                    differences.add(new Difference(entityName, i, "<extraRow>", null, candRow.toString()));
                    continue;
                }
                if (candRow == null) {
                    differences.add(new Difference(entityName, i, "<missingRow>", baseRow.toString(), null));
                    continue;
                }
                Set<String> fieldNames = new TreeSet<>();
                fieldNames.addAll(baseRow.keySet());
                fieldNames.addAll(candRow.keySet());
                for (String fieldName : fieldNames) {
                    Object baseValue = baseRow.get(fieldName);
                    Object candValue = candRow.get(fieldName);
                    if (!java.util.Objects.equals(baseValue, candValue)) {
                        differences.add(new Difference(entityName, i, fieldName,
                                baseValue == null ? null : baseValue.toString(),
                                candValue == null ? null : candValue.toString()));
                    }
                }
            }
        }
        return differences;
    }

    /**
     * Renders a human-readable parity report.
     * @param baseline the reference snapshot
     * @param candidate the snapshot to compare
     * @return a multi-line report
     */
    public static String report(OrderParitySnapshot baseline, OrderParitySnapshot candidate) {
        List<Difference> differences = diff(baseline, candidate);
        StringBuilder sb = new StringBuilder();
        sb.append("Order parity report\n");
        sb.append("  baseline  order ").append(baseline.orderId).append(" (").append(baseline.totalRows()).append(" rows)\n");
        sb.append("  candidate order ").append(candidate.orderId).append(" (").append(candidate.totalRows()).append(" rows)\n");
        if (differences.isEmpty()) {
            sb.append("  RESULT: PARITY - no field-level differences\n");
            return sb.toString();
        }
        sb.append("  RESULT: ").append(differences.size()).append(" difference(s)\n");
        for (Difference difference : differences) {
            sb.append("    ").append(difference).append('\n');
        }
        return sb.toString();
    }

    /** A single field-level difference between two snapshots. */
    public static final class Difference {
        private final String entityName;
        private final int rowIndex;
        private final String fieldName;
        private final String baselineValue;
        private final String candidateValue;

        public Difference(String entityName, int rowIndex, String fieldName, String baselineValue, String candidateValue) {
            this.entityName = entityName;
            this.rowIndex = rowIndex;
            this.fieldName = fieldName;
            this.baselineValue = baselineValue;
            this.candidateValue = candidateValue;
        }

        public String getEntityName() {
            return entityName;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getBaselineValue() {
            return baselineValue;
        }

        public String getCandidateValue() {
            return candidateValue;
        }

        @Override
        public String toString() {
            return entityName + "[" + rowIndex + "]." + fieldName
                    + ": baseline=" + baselineValue + " candidate=" + candidateValue;
        }
    }

    @Override
    public String toString() {
        String entitySummary = entities.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + e.getValue().size())
                .collect(Collectors.joining(", "));
        return "OrderParitySnapshot{orderId=" + orderId + ", ignoredFields=" + ignoredFields.size()
                + ", rows=[" + entitySummary + "]}";
    }
}
