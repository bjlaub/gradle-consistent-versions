/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.versions.lockstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.versions.GradleComparators;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.junit.jupiter.api.Test;

class LockStatesTest {
    @Test
    void grpc_should_have_its_square_bracket_version_stripped() {
        ComponentIdentifier grpcApi = componentIdentifier("io.grpc:grpc-api:1.27.1");
        ComponentIdentifier grpcCore = componentIdentifier("io.grpc:grpc-core:1.27.1");
        ComponentIdentifier grpcNetty = componentIdentifier("io.grpc:grpc-netty:1.27.1");

        VersionConstraint squareBracketConstraint = versionConstraint("[1.27.1]");
        VersionConstraint normalConstraint = versionConstraint("1.27.1");

        SortedMap<ComponentIdentifier, Set<VersionConstraint>> dependents =
                new TreeMap<>(GradleComparators.COMPONENT_IDENTIFIER_COMPARATOR);

        dependents.put(grpcApi, ImmutableSet.of(squareBracketConstraint));
        dependents.put(grpcCore, ImmutableSet.of(normalConstraint));
        dependents.put(grpcNetty, ImmutableSet.of(squareBracketConstraint, normalConstraint));

        assertThat(LockStates.prettyPrintConstraints(Dependents.of(dependents)))
                .containsExactly(
                        "io.grpc:grpc-api:1.27.1 -> 1.27.1",
                        "io.grpc:grpc-core:1.27.1 -> 1.27.1",
                        "io.grpc:grpc-netty:1.27.1 -> {1.27.1, 1.27.1}");
    }

    private ComponentIdentifier componentIdentifier(String componentIdentifier) {
        ComponentIdentifier grpcApi = mock(ComponentIdentifier.class);
        when(grpcApi.getDisplayName()).thenReturn(componentIdentifier);
        return grpcApi;
    }

    private VersionConstraint versionConstraint(String version) {
        VersionConstraint constraint = mock(VersionConstraint.class);
        when(constraint.toString()).thenReturn(version);
        return constraint;
    }
}
