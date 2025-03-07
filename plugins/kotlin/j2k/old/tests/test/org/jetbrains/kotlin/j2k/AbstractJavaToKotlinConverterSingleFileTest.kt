// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.j2k.IdeaJavaToKotlinServices
import org.jetbrains.kotlin.idea.j2k.J2kPostProcessor
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.types.FlexibleTypeImpl
import java.io.File
import java.util.regex.Pattern

abstract class AbstractJavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterTest() {
    private val testHeaderPattern: Pattern = Pattern.compile("//(element|expression|statement|method|class|file|comp)\n")

    // FIXME: remove after KTIJ-5630
    private fun doTestWithSlowAssertion(directives: Directives, block: () -> Unit) {
        val oldValue = FlexibleTypeImpl.RUN_SLOW_ASSERTIONS
        try {
            FlexibleTypeImpl.RUN_SLOW_ASSERTIONS = directives["WITHOUT_SLOW_ASSERTIONS"] == null
            block()
        } finally {
            FlexibleTypeImpl.RUN_SLOW_ASSERTIONS = oldValue
        }
    }

    open fun doTest(javaPath: String) {
        val javaFile = File(javaPath)
        val fileContents = FileUtil.loadFile(javaFile, true)
        val matcher = testHeaderPattern.matcher(fileContents)

        val (prefix, javaCode) = if (matcher.find()) {
            Pair(matcher.group().trim().substring(2), matcher.replaceFirst(""))
        } else {
            Pair("file", fileContents)
        }

        val settings = ConverterSettings.defaultSettings.copy()
        val directives = KotlinTestUtils.parseDirectives(javaCode)
        doTestWithSlowAssertion(directives) {
            directives["FORCE_NOT_NULL_TYPES"]?.let {
                settings.forceNotNullTypes = it.toBoolean()
            }
            directives["SPECIFY_LOCAL_VARIABLE_TYPE_BY_DEFAULT"]?.let {
                settings.specifyLocalVariableTypeByDefault = it.toBoolean()
            }
            directives["SPECIFY_FIELD_TYPE_BY_DEFAULT"]?.let {
                settings.specifyFieldTypeByDefault = it.toBoolean()
            }
            directives["OPEN_BY_DEFAULT"]?.let {
                settings.openByDefault = it.toBoolean()
            }
            directives["PUBLIC_BY_DEFAULT"]?.let {
                settings.publicByDefault = it.toBoolean()
            }

            val rawConverted = WriteCommandAction.runWriteCommandAction(project, Computable {
                PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
                return@Computable when (prefix) {
                    "expression" -> expressionToKotlin(javaCode, settings, project)
                    "statement" -> statementToKotlin(javaCode, settings, project)
                    "method" -> methodToKotlin(javaCode, settings, project)
                    "class" -> fileToKotlin(javaCode, settings, project)
                    "file" -> fileToKotlin(javaCode, settings, project)
                    else -> throw IllegalStateException(
                        "Specify what is it: file, class, method, statement or expression using the first line of test data file"
                    )
                }
            })

            val reformatInFun = prefix in setOf("element", "expression", "statement")

            var actual = reformat(rawConverted, project, reformatInFun)

            if (prefix == "file") {
                actual = createKotlinFile(actual).dumpTextWithErrors()
            }

            val expectedFile = provideExpectedFile(javaPath)
            compareResults(expectedFile, actual)
        }
    }

    open fun provideExpectedFile(javaPath: String): File {
        val kotlinPath = javaPath.replace(".java", ".kt")
        return File(kotlinPath)
    }

    open fun compareResults(expectedFile: File, actual: String) {
        KotlinTestUtils.assertEqualsToFile(expectedFile, actual)
    }

    private fun reformat(text: String, project: Project, inFunContext: Boolean): String {
        val funBody = text.lines().joinToString(separator = "\n", transform = { "  $it" })
        val textToFormat = if (inFunContext) "fun convertedTemp() {\n$funBody\n}" else text

        val convertedFile = KotlinTestUtils.createFile("converted", textToFormat, project)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project)!!.reformat(convertedFile)
        }

        val reformattedText = convertedFile.text!!

        return if (inFunContext)
            reformattedText.removeFirstLine().removeLastLine().trimIndent()
        else
            reformattedText
    }

    protected open fun fileToKotlin(text: String, settings: ConverterSettings, project: Project): String {
        val file = createJavaFile(text)
        val converter = OldJavaToKotlinConverter(project, settings, IdeaJavaToKotlinServices)
        return converter.filesToKotlin(listOf(file), J2kPostProcessor(formatCode = true)).results.single()
    }

    private fun methodToKotlin(text: String, settings: ConverterSettings, project: Project): String {
        val result = fileToKotlin("final class C {$text}", settings, project)
        return result
            .substringBeforeLast("}")
            .replace("internal class C {", "\n")
            .replace("internal object C {", "\n")
            .trimIndent().trim()
    }

    private fun statementToKotlin(text: String, settings: ConverterSettings, project: Project): String {
        val funBody = text.lines().joinToString(separator = "\n", transform = { "  $it" })
        val result = methodToKotlin("public void main() {\n$funBody\n}", settings, project)

        return result
            .substringBeforeLast("}")
            .replaceFirst("fun main() {", "\n")
            .trimIndent().trim()
    }

    private fun expressionToKotlin(code: String, settings: ConverterSettings, project: Project): String {
        val result = statementToKotlin("final Object o =$code}", settings, project)
        return result
            .replaceFirst("val o: Any? = ", "")
            .replaceFirst("val o: Any = ", "")
            .replaceFirst("val o = ", "")
            .trim()
    }

    override fun getProjectDescriptor(): LightProjectDescriptor {
        val testName = getTestName(false)
        return if (testName.contains("WithFullJdk") || testName.contains("withFullJdk"))
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()
        else
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    private fun String.removeFirstLine() = substringAfter('\n', "")

    private fun String.removeLastLine() = substringBeforeLast('\n', "")

    protected fun createJavaFile(text: String): PsiJavaFile {
        return myFixture.configureByText("converterTestFile.java", text) as PsiJavaFile
    }

    private fun createKotlinFile(text: String): KtFile {
        return myFixture.configureByText("converterTestFile.kt", text) as KtFile
    }
}
