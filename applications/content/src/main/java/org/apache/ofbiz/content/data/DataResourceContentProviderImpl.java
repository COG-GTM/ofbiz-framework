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
package org.apache.ofbiz.content.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.common.content.DataResourceContentProvider;
import org.apache.ofbiz.entity.Delegator;

/**
 * Content-component implementation of the {@link DataResourceContentProvider} seam. Registered via
 * {@code META-INF/services} and discovered at runtime, it lets other components read DataResource
 * bytes without importing the {@code content} component. The direction stays one-way:
 * {@code content} implements the framework seam.
 */
public class DataResourceContentProviderImpl implements DataResourceContentProvider {

    @Override
    public ByteBuffer getContentAsByteBuffer(Delegator delegator, String dataResourceId, String https, String webSiteId, Locale locale,
            String rootDir) throws IOException, GeneralException {
        return DataResourceWorker.getContentAsByteBuffer(delegator, dataResourceId, https, webSiteId, locale, rootDir);
    }
}
