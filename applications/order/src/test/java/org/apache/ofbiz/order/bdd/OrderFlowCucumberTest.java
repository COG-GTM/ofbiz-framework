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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.cucumber.core.cli.CommandlineOptions;
import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.runtime.Runtime;

import org.junit.jupiter.api.Test;

/**
 * JUnit 5 entry point that runs the order-flow Cucumber scenarios programmatically.
 *
 * <p>Cucumber is driven through its own {@link Runtime} (rather than the JUnit Platform engine) so the
 * harness stays independent of the JUnit Platform version that OFBiz pins. The whole suite is skipped
 * gracefully when the OFBiz runtime cannot be booted (for example a {@code ./gradlew check} run on a
 * CI box without a loaded database), so it never breaks the build.</p>
 */
class OrderFlowCucumberTest {

    @Test
    void runOrderFlowScenarios() {
        assumeTrue(OfbizTestContainer.isAvailable(),
                "OFBiz runtime not available (run './gradlew loadAll' first): " + OfbizTestContainer.unavailableReason());

        RuntimeOptions runtimeOptions = new CommandlineOptionsParser(System.out)
                .parse(
                        CommandlineOptions.GLUE, "org.apache.ofbiz.order.bdd",
                        CommandlineOptions.PLUGIN, "pretty",
                        CommandlineOptions.PLUGIN, "summary",
                        "classpath:features/order")
                .build();

        Runtime runtime = Runtime.builder().withRuntimeOptions(runtimeOptions).build();
        runtime.run();
        assertEquals((byte) 0x0, runtime.exitStatus(), "One or more order-flow Cucumber scenarios failed");
    }
}
