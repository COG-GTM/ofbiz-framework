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

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.ofbiz.base.container.ComponentContainer;
import org.apache.ofbiz.base.start.Config;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.start.Start;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceContainer;

/**
 * Boots a minimal in-process OFBiz runtime (component registry + entity engine + service engine)
 * so that the Cucumber order-flow scenarios can invoke real services such as {@code storeOrder}
 * and assert against the persisted database state.
 *
 * <p>The catalina/web container is intentionally <em>not</em> started; only the pieces required to
 * run services against the already-loaded database are initialised. The runtime database must have
 * been populated beforehand with {@code ./gradlew loadAll}.</p>
 *
 * <p>When the runtime cannot be started (for example in a CI job that runs {@code ./gradlew check}
 * without loading data) {@link #isAvailable()} returns {@code false} and {@link #unavailableReason()}
 * explains why, allowing callers to skip the scenarios gracefully instead of failing the build.</p>
 */
public final class OfbizTestContainer {

    private static final String MODULE = OfbizTestContainer.class.getName();
    private static boolean initialised;
    private static boolean available;
    private static String unavailableReason;
    private static Delegator delegator;
    private static LocalDispatcher dispatcher;

    private OfbizTestContainer() { }

    /** Starts the runtime once per JVM. Subsequent calls are no-ops. */
    public static synchronized void ensureStarted() {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            Path ofbizHome = locateOfbizHome();
            System.setProperty("ofbiz.home", ofbizHome.toString());

            // Config holds the resolved ofbiz.home that ComponentContainer reads from Start.
            // Its constructor is package-private, so it is created reflectively here.
            Constructor<Config> configCtor = Config.class.getDeclaredConstructor(List.class);
            configCtor.setAccessible(true);
            Config config = configCtor.newInstance(new ArrayList<>());
            Start.getInstance().setConfig(config);

            ComponentContainer componentContainer = new ComponentContainer();
            componentContainer.init(null, "component-container", null);

            delegator = DelegatorFactory.getDelegator("default");
            if (delegator == null) {
                throw new IllegalStateException("Could not obtain the 'default' delegator");
            }

            // Initialise the service engine (sets up the LocalDispatcherFactory) before requesting a dispatcher.
            ServiceContainer serviceContainer = new ServiceContainer();
            serviceContainer.init(null, "service-container", null);
            dispatcher = ServiceContainer.getLocalDispatcher("order-bdd", delegator);

            // Sanity check: the seed/demo data must be present.
            GenericValue systemUser = EntityQuery.use(delegator).from("UserLogin")
                    .where("userLoginId", "system").queryOne();
            if (systemUser == null) {
                throw new IllegalStateException("Demo/seed data not loaded (no 'system' UserLogin found). "
                        + "Run './gradlew loadAll' before running these scenarios.");
            }
            available = true;
        } catch (Throwable t) {
            available = false;
            unavailableReason = t.getClass().getSimpleName() + ": " + t.getMessage();
            if (Boolean.getBoolean("ofbiz.bdd.debug")) {
                Debug.logError(t, MODULE);
            }
        }
    }

    /** @return whether the OFBiz runtime is available for executing scenarios. */
    public static boolean isAvailable() {
        ensureStarted();
        return available;
    }

    /** @return a human readable explanation of why the runtime is unavailable, or {@code null}. */
    public static String unavailableReason() {
        return unavailableReason;
    }

    /** @return the booted delegator. */
    public static Delegator getDelegator() {
        ensureStarted();
        return delegator;
    }

    /** @return the booted dispatcher. */
    public static LocalDispatcher getDispatcher() {
        ensureStarted();
        return dispatcher;
    }

    private static Path locateOfbizHome() {
        String configured = System.getProperty("ofbiz.home");
        if (configured != null && Files.exists(Paths.get(configured, "framework"))) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        // Walk up from the working directory until the framework directory is found.
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("framework")) && Files.exists(dir.resolve("applications"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }
}
