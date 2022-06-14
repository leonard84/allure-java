/*
 *  Copyright 2019 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.qameta.allure.spock;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.aspects.AttachmentsAspects;
import io.qameta.allure.aspects.StepsAspects;
import io.qameta.allure.model.*;
import io.qameta.allure.spock.samples.*;
import io.qameta.allure.test.AllureResults;
import io.qameta.allure.test.AllureResultsWriterStub;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.spockframework.runtime.RunContext;
import org.spockframework.util.ReflectionUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * @author charlie (Dmitry Baev).
 */
@SuppressWarnings("unchecked")
class AllureSpock2Test {

    @Test
    void shouldStoreTestsInformation() {
        final AllureResults results = run(OneTest.class);
        assertThat(results.getTestResults())
                .hasSize(1);
    }

    @Test
    void shouldSetTestStart() {
        final long before = Instant.now().toEpochMilli();
        final AllureResults results = run(OneTest.class);

        final long after = Instant.now().toEpochMilli();

        assertThat(results.getTestResults())
                .extracting(TestResult::getStart)
                .allMatch(v -> v >= before && v <= after);
    }

    @Test
    void shouldSetTestStop() {
        final long before = Instant.now().toEpochMilli();
        final AllureResults results = run(OneTest.class);
        final long after = Instant.now().toEpochMilli();

        assertThat(results.getTestResults())
                .extracting(TestResult::getStop)
                .allMatch(v -> v >= before && v <= after);
    }

    @Test
    void shouldSetTestFullName() {
        final AllureResults results = run(OneTest.class);
        assertThat(results.getTestResults())
                .extracting(TestResult::getFullName)
                .containsExactly("io.qameta.allure.spock.samples.OneTest.Simple Test");
    }

    @Test
    void shouldSetStageFinished() {
        final AllureResults results = run(OneTest.class);
        assertThat(results.getTestResults())
                .extracting(TestResult::getStage)
                .containsExactly(Stage.FINISHED);
    }

    @Test
    void shouldProcessFailedTest() {
        final AllureResults results = run(FailedTest.class);
        assertThat(results.getTestResults())
                .extracting(TestResult::getStatus)
                .containsExactly(Status.FAILED);
    }

    @Test
    void shouldProcessBrokenTest() {
        final AllureResults results = run(BrokenTest.class);
        assertThat(results.getTestResults())
                .extracting(TestResult::getStatus)
                .containsExactly(Status.BROKEN);
    }

    @Test
    void shouldAddStepsToTest() {
        final AllureResults results = run(TestWithSteps.class);
        assertThat(results.getTestResults())
                .flatExtracting(TestResult::getSteps)
                .extracting(StepResult::getName)
                .containsExactly("step1", "step2", "step3");
    }

    @Test
    void shouldProcessMethodAnnotations() {
        final AllureResults results = run(TestWithAnnotations.class);
        assertThat(results.getTestResults())
                .hasSize(1)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getValue)
                .contains(
                        "epic1", "epic2", "epic3",
                        "feature1", "feature2", "feature3",
                        "story1", "story2", "story3",
                        "some-owner"
                );
    }

    @Test
    void shouldProcessClassAnnotations() {
        final AllureResults results = run(TestWithAnnotationsOnClass.class);
        assertThat(results.getTestResults())
                .hasSize(1)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getValue)
                .contains(
                        "epic1", "epic2", "epic3",
                        "feature1", "feature2", "feature3",
                        "story1", "story2", "story3",
                        "some-owner"
                );
    }

    @Test
    void shouldProcessCustomAnnotations() {
        final AllureResults results = run(TestWithCustomAnnotations.class);
        assertThat(results.getTestResults())
                .hasSize(1)
                .flatExtracting(TestResult::getLabels)
                .extracting(Label::getValue)
                .contains(
                        "epic", "feature", "story", "AS-1", "XRT-1"
                );
    }

    @Test
    void shouldProcessFlakyAnnotation() {
        final AllureResults results = run(TestWithAnnotations.class);
        assertThat(results.getTestResults())
                .filteredOn(flakyPredicate())
                .hasSize(1);
    }

    @Test
    void shouldProcessMutedAnnotation() {
        final AllureResults results = run(TestWithAnnotations.class);
        assertThat(results.getTestResults())
                .filteredOn(mutedPredicate())
                .hasSize(1);
    }

    @Test
    void shouldSetDisplayName() {
        final AllureResults results = run(OneTest.class);
        assertThat(results.getTestResults())
                .extracting(TestResult::getName)
                .containsExactly("Simple Test");
    }

    @Test
    void shouldSetLinks() {
        final AllureResults results = run(FailedTest.class);
        assertThat(results.getTestResults())
                .flatExtracting(TestResult::getLinks)
                .extracting(Link::getName)
                .containsExactlyInAnyOrder("link-1", "link-2", "issue-1", "issue-2", "tms-1", "tms-2");
    }

    @Test
    void shouldSetParameters() {
        final AllureResults results = run(ParametersTest.class);
        assertThat(results.getTestResults())
                .flatExtracting(TestResult::getParameters)
                .extracting(Parameter::getName, Parameter::getValue)
                .containsExactlyInAnyOrder(
                        tuple("a", "1"),
                        tuple("b", "3"),
                        tuple("c", "3")
                );
    }

    @Test
    void shouldSupportDataDrivenTests() {
        final AllureResults results = run(DataDrivenTest.class);
        assertThat(results.getTestResults())
                .hasSize(3);
    }

    private AllureResults run(final Class<?>... classes) {
        final AllureResultsWriterStub writerStub = new AllureResultsWriterStub();
        final AllureLifecycle lifecycle = new AllureLifecycle(writerStub);

        final ClassSelector[] classSelectors = Stream.of(classes)
            .map(DiscoverySelectors::selectClass)
            .toArray(ClassSelector[]::new);

        final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(classSelectors)
            .build();

        final LauncherConfig config = LauncherConfig.builder()
            .enableTestExecutionListenerAutoRegistration(false)
            .build();
        final Launcher launcher = LauncherFactory.create(config);

        final AllureLifecycle defaultLifecycle = Allure.getLifecycle();
        try {
            Allure.setLifecycle(lifecycle);
            StepsAspects.setLifecycle(lifecycle);
            AttachmentsAspects.setLifecycle(lifecycle);
            RunContext.withNewContext("AllureEmbedded",
                new File("."),
                null,
                singletonList(AllureSpock.class),
                emptyList(),
                true,
                ctx -> {
                launcher.execute(request);
                return null;
            });
            return writerStub;
        } finally {
            Allure.setLifecycle(defaultLifecycle);
            StepsAspects.setLifecycle(defaultLifecycle);
            AttachmentsAspects.setLifecycle(defaultLifecycle);
        }
    }

    private static Predicate<TestResult> mutedPredicate() {
        return testResult -> Optional.of(testResult)
                .map(TestResult::getStatusDetails)
                .map(StatusDetails::isMuted)
                .filter(m -> m)
                .isPresent();
    }

    private static Predicate<TestResult> flakyPredicate() {
        return testResult -> Optional.of(testResult)
                .map(TestResult::getStatusDetails)
                .map(StatusDetails::isFlaky)
                .filter(m -> m)
                .isPresent();
    }

}
