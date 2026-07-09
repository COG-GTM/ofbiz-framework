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

import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Outbound boundary (adapter/gateway) for the manufacturing component's dependencies on the
 * {@code workeffort} component.
 *
 * <p>Manufacturing has no compile-time (import) coupling on workeffort; its dependency is entirely
 * through the service engine (string-based {@code runSync} calls into workeffort-owned services). This
 * is the single place inside manufacturing that names those workeffort-owned services, so the coupling
 * is explicit and centralized. Each method simply forwards to the underlying service, preserving
 * behavior.</p>
 */
public final class WorkEffortGateway {

    private WorkEffortGateway() {
    }

    public static Map<String, Object> createWorkEffort(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createWorkEffort", context);
    }

    public static Map<String, Object> updateWorkEffort(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("updateWorkEffort", context);
    }

    public static Map<String, Object> createWorkEffortAssoc(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createWorkEffortAssoc", context);
    }

    public static Map<String, Object> createWorkEffortGoodStandard(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createWorkEffortGoodStandard", context);
    }

    public static Map<String, Object> updateWorkEffortGoodStandard(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("updateWorkEffortGoodStandard", context);
    }

    public static Map<String, Object> createWorkEffortInventoryProduced(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createWorkEffortInventoryProduced", context);
    }

    public static Map<String, Object> createWorkEffortNote(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createWorkEffortNote", context);
    }

    public static Map<String, Object> assignPartyToWorkEffort(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("assignPartyToWorkEffort", context);
    }

    public static Map<String, Object> createTimeEntry(LocalDispatcher dispatcher, Map<String, ? extends Object> context)
            throws GenericServiceException {
        return dispatcher.runSync("createTimeEntry", context);
    }
}
