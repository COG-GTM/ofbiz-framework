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
package org.apache.ofbiz.content;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Regression guard for the content component's decoupling contract.
 *
 * <p>The {@code content} component PROVIDES the shared content-access seam
 * ({@code ContentWrapper}); the business modules (party / product / order /
 * accounting / workeffort) DEPEND ON content. This is the allowed direction.
 * This test fails if the content component's own Java/Groovy sources ever add an
 * outbound dependency in the opposite direction, i.e. reference
 * {@code org.apache.ofbiz.party}, {@code product}, {@code order} or
 * {@code accounting}.</p>
 *
 * <p>This is a pure source scan; it needs no database or running framework, so it
 * runs as part of the fast {@code ./gradlew test} unit-test task.</p>
 */
public class ContentOutboundCouplingTest {

    /** Business modules the content core must not reach into. */
    private static final Pattern FORBIDDEN =
            Pattern.compile("org\\.apache\\.ofbiz\\.(party|product|order|accounting)\\.");

    private static final List<String> SOURCE_ROOTS = List.of(
            "applications/content/src/main/java",
            "applications/content/src/main/groovy");

    @Test
    public void contentSourcesHaveNoOutboundBusinessModuleDependency() {
        Path repoRoot = findRepoRoot();
        List<String> offenders = new ArrayList<>();
        boolean scannedSomething = false;

        for (String root : SOURCE_ROOTS) {
            Path dir = repoRoot.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            scannedSomething = true;
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.endsWith(".java") || n.endsWith(".groovy");
                        })
                        .forEach(p -> {
                            for (String line : readLines(p)) {
                                if (FORBIDDEN.matcher(line).find()) {
                                    offenders.add(repoRoot.relativize(p) + " -> " + line.trim());
                                }
                            }
                        });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        assertTrue(scannedSomething,
                "Could not locate content sources under " + repoRoot + "; SOURCE_ROOTS=" + SOURCE_ROOTS);

        if (!offenders.isEmpty()) {
            fail("The content component must not depend on party/product/order/accounting. "
                    + "Found outbound reference(s):\n" + String.join("\n", offenders)
                    + "\nMove the integration behind an interface/service seam consumed by the "
                    + "business module instead. See applications/content/README-integration-seams.md.");
        }
    }

    private static List<String> readLines(Path p) {
        try {
            return Files.readAllLines(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Resolve the repository root robustly, regardless of the working directory
     * the test is launched from, by walking up from {@code user.dir} until a
     * directory containing {@code applications/content} is found.
     */
    private static Path findRepoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("applications/content"))) {
                return candidate;
            }
        }
        // Fall back to user.dir; the assertion in the test reports if nothing was scanned.
        return start;
    }
}
