/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.documentsui

import android.platform.test.rule.ArtifactSaver
import java.util.stream.Stream
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.TestWithParameters

/**
 * A customized runner that combines the functionality of the ArtifactSaver with JUnit's
 * parameterized test support.
 *
 * This runner extends `BlockJUnit4ClassRunnerWithParameters` to natively handle `@Parameter` and
 * `@Parameters`, while incorporating the features of the `Functional` runner:
 * - Early artifact saving on test failures.
 */
open class ArtifactSaverRunnerWithParameters(val test: TestWithParameters) :
    BlockJUnit4ClassRunnerWithParameters(test) {

    private val methodsWithSavedArtifacts: MutableSet<FrameworkMethod> = HashSet()

    private fun artifactSaver(statement: Statement, methods: Stream<FrameworkMethod>): Statement {
        return object : Statement() {
            override fun evaluate() {
                try {
                    statement.evaluate()
                } catch (e: Throwable) {
                    methods.forEach { method ->
                        if (methodsWithSavedArtifacts.contains(method)) return@forEach
                        methodsWithSavedArtifacts.add(method)
                        ArtifactSaver.onError(describeChild(method), e)
                    }
                    throw e
                }
            }
        }
    }

    override fun methodBlock(method: FrameworkMethod): Statement {
        // Error artifact saver for exceptions thrown outside "method-afters", i.e. in method rules.
        return artifactSaver(super.methodBlock(method), Stream.of(method))
    }
}
