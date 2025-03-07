// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.junit.runner.RunWith;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("jvm-debugger/test")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("testData/highlighting")
public class IndyLambdaBreakpointHighlightingTestGenerated extends AbstractIndyLambdaBreakpointHighlightingTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doCustomTest, this, testDataFilePath);
    }

    @TestMetadata("lambdasOnSameLine.kt")
    public void testLambdasOnSameLine() throws Exception {
        runTest("testData/highlighting/lambdasOnSameLine.kt");
    }

    @TestMetadata("multiLineLambda.kt")
    public void testMultiLineLambda() throws Exception {
        runTest("testData/highlighting/multiLineLambda.kt");
    }

    @TestMetadata("oneLineLambda.kt")
    public void testOneLineLambda() throws Exception {
        runTest("testData/highlighting/oneLineLambda.kt");
    }
}
