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
package org.apache.ofbiz.common.content;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.entity.Delegator;

/**
 * DataResourceContentProvider
 *
 * <p>Inversion seam for reading the raw bytes of a {@code DataResource} without a compile-time
 * dependency on the {@code content} component. The concern is <em>provided to</em> collaborators
 * (e.g. the {@code party} communication logic) rather than reached <em>into</em> the content
 * component from them.</p>
 *
 * <p>The interface lives in the neutral {@code common} framework component; the implementation is
 * supplied by the {@code content} component and discovered at runtime via
 * {@link java.util.ServiceLoader} (see {@link DataResourceContentProviderFactory}). This keeps the
 * dependency direction one-way: consumers depend on this framework seam, and {@code content}
 * implements it without any consumer depending on {@code content} at compile time.</p>
 */
public interface DataResourceContentProvider {

    /**
     * Returns the content of the given data resource as a {@link ByteBuffer}.
     * @param delegator the delegator
     * @param dataResourceId the data resource id
     * @param https the https flag (may be {@code null})
     * @param webSiteId the web site id (may be {@code null})
     * @param locale the locale (may be {@code null})
     * @param rootDir the root directory for file-backed resources (may be {@code null})
     * @return the data resource content
     * @throws IOException on I/O error
     * @throws GeneralException on general error
     */
    ByteBuffer getContentAsByteBuffer(Delegator delegator, String dataResourceId, String https, String webSiteId, Locale locale,
            String rootDir) throws IOException, GeneralException;
}
