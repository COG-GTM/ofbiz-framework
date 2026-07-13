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

import java.util.Iterator;
import java.util.ServiceLoader;

import org.apache.ofbiz.base.util.Debug;

/**
 * DataResourceContentProviderFactory
 *
 * <p>Resolves the runtime {@link DataResourceContentProvider} implementation via
 * {@link java.util.ServiceLoader}, mirroring the pattern already used for
 * {@code org.apache.ofbiz.common.authentication.AuthHelper}. The implementation is contributed by
 * the {@code content} component through a {@code META-INF/services} descriptor, so consumers never
 * reference the {@code content} component directly.</p>
 */
public final class DataResourceContentProviderFactory {

    private static final String MODULE = DataResourceContentProviderFactory.class.getName();

    private static volatile DataResourceContentProvider provider;

    private DataResourceContentProviderFactory() {
    }

    /**
     * Returns the registered {@link DataResourceContentProvider}, loading it lazily on first use.
     * @return the provider
     * @throws IllegalStateException if no implementation is registered on the classpath
     */
    public static DataResourceContentProvider getProvider() {
        DataResourceContentProvider result = provider;
        if (result == null) {
            result = loadProvider();
        }
        if (result == null) {
            throw new IllegalStateException("No DataResourceContentProvider implementation registered; is the content component loaded?");
        }
        return result;
    }

    private static synchronized DataResourceContentProvider loadProvider() {
        if (provider == null) {
            Iterator<DataResourceContentProvider> it = ServiceLoader.load(DataResourceContentProvider.class, getContextClassLoader()).iterator();
            if (it.hasNext()) {
                provider = it.next();
            } else {
                Debug.logWarning("No DataResourceContentProvider implementation found on the classpath", MODULE);
            }
        }
        return provider;
    }

    private static ClassLoader getContextClassLoader() {
        try {
            return Thread.currentThread().getContextClassLoader();
        } catch (SecurityException e) {
            Debug.logError(e, e.getMessage(), MODULE);
            return null;
        }
    }
}
