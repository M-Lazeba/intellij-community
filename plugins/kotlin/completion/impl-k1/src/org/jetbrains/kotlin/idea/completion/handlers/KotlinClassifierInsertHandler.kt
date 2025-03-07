// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.analysis.canAddRootPrefix
import org.jetbrains.kotlin.idea.caches.resolve.allowResolveInDispatchThread
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.completion.PsiClassLookupObject
import org.jetbrains.kotlin.idea.completion.isAfterDot
import org.jetbrains.kotlin.idea.completion.isArtificialImportAliasedDescriptor
import org.jetbrains.kotlin.idea.completion.shortenReferences
import org.jetbrains.kotlin.idea.completion.smart.SMART_COMPLETION_ITEM_PRIORITY_KEY
import org.jetbrains.kotlin.idea.completion.smart.SmartCompletionItemPriority
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.QualifiedExpressionResolver.Companion.ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE_WITH_DOT
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.util.unwrapIfTypeAlias
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object KotlinClassifierInsertHandler : BaseDeclarationInsertHandler() {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        surroundWithBracesIfInStringTemplate(context)

        super.handleInsert(context, item)

        val file = context.file
        if (file is KtFile) {
            if (!context.isAfterDot()) {
                val project = context.project
                val psiDocumentManager = PsiDocumentManager.getInstance(project)
                psiDocumentManager.commitDocument(context.document)

                val startOffset = context.startOffset
                val document = context.document

                val lookupObject = item.`object` as DescriptorBasedDeclarationLookupObject
                // never need to insert import or use qualified name for import-aliased class
                val descriptor = lookupObject.descriptor
                if (descriptor?.isArtificialImportAliasedDescriptor == true) return

                val qualifiedName = qualifiedName(lookupObject)

                val position = file.findElementAt(context.startOffset)?.getParentOfType<KtElement>(strict = false) ?: file

                val importAction: (() -> ImportDescriptorResult?)? =
                    descriptor?.takeIf { DescriptorUtils.isTopLevelDeclaration(it) }
                        ?.let {
                            fun(): ImportDescriptorResult = ImportInsertHelper.getInstance(project).importDescriptor(position, it)
                        } ?: lookupObject.safeAs<PsiClassLookupObject>()
                        ?.let {
                            fun(): ImportDescriptorResult = ImportInsertHelper.getInstance(project).importPsiClass(position, it.psiClass)
                        }

                val importDescriptorResult = importAction?.invoke()
                if (importDescriptorResult == null || importDescriptorResult == ImportDescriptorResult.FAIL) {
                    // first try to resolve short name for faster handling
                    val token = file.findElementAt(startOffset)!!
                    val nameRef = token.parent as? KtNameReferenceExpression
                    if (nameRef != null) {
                        val bindingContext = allowResolveInDispatchThread { nameRef.analyze(BodyResolveMode.PARTIAL) }
                        val target = bindingContext[BindingContext.SHORT_REFERENCE_TO_COMPANION_OBJECT, nameRef]
                            ?: bindingContext[BindingContext.REFERENCE_TARGET, nameRef] as? ClassDescriptor
                        if (target != null && IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(target) == qualifiedName) return
                    }

                    val tempPrefix = if (nameRef != null) {
                        val isAnnotation = CallTypeAndReceiver.detect(nameRef) is CallTypeAndReceiver.ANNOTATION
                        // we insert space so that any preceding spaces inserted by formatter on reference shortening are deleted
                        // (but not for annotations where spaces are not allowed after @)
                        if (isAnnotation) "" else " "
                    } else {
                        "$;val v:"  // if we have no reference in the current context we have a more complicated prefix to get one
                    }
                    val tempSuffix = ".xxx" // we add "xxx" after dot because of KT-9606
                    val qualifierNameWithRootPrefix = qualifiedName.let {
                        if (FqName(it).canAddRootPrefix())
                            ROOT_PREFIX_FOR_IDE_RESOLUTION_MODE_WITH_DOT + it
                        else
                            it
                    }
                    document.replaceString(startOffset, context.tailOffset, tempPrefix + qualifierNameWithRootPrefix + tempSuffix)

                    psiDocumentManager.commitDocument(document)

                    val classNameStart = startOffset + tempPrefix.length
                    val classNameEnd = classNameStart + qualifierNameWithRootPrefix.length
                    val rangeMarker = document.createRangeMarker(classNameStart, classNameEnd)
                    val wholeRangeMarker = document.createRangeMarker(startOffset, classNameEnd + tempSuffix.length)

                    shortenReferences(context, classNameStart, classNameEnd)
                    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)

                    if (rangeMarker.isValid && wholeRangeMarker.isValid) {
                        document.deleteString(wholeRangeMarker.startOffset, rangeMarker.startOffset)
                        document.deleteString(rangeMarker.endOffset, wholeRangeMarker.endOffset)
                    }
                } else {
                    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
                }

                val expression = position as? KtSimpleNameExpression
                if (expression != null) {
                    insertParentheses(expression, descriptor, document, context)
                }
            }
        }
    }

    private fun insertParentheses(
        expression: KtSimpleNameExpression,
        descriptor: DeclarationDescriptor?,
        document: Document,
        context: InsertionContext
    ) {
        val editor = context.editor
        if (!editor.settings.isInsertParenthesesAutomatically) return
        if (context.completionChar != '\n') return
        // do not insert any parenthesis when smart completion strategy is used
        if (context.elements.any {
            val smartCompletionItemPriority = it.getUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY)
            smartCompletionItemPriority!= null && smartCompletionItemPriority != SmartCompletionItemPriority.DEFAULT
        }) return

        val callTypeAndReceiver = CallTypeAndReceiver.detect(expression)
        val callType = callTypeAndReceiver.callType
        if (callType != CallType.DEFAULT &&
            // `class F: Foo<caret>` has callType == TYPE
            (expression.parentOfType<KtSuperTypeEntry>() == null ||
                    // do not add parenthesis when caret within generic like `class F: Foo<Str<caret>`
                    expression.parentOfType<KtTypeArgumentList>() != null)
        ) return

        val classDescriptor = (descriptor?.unwrapIfTypeAlias() as? ClassDescriptor)?.takeIf { it.kind == ClassKind.CLASS }
        val constructorsWithMinValueParams =
            classDescriptor?.constructors?.minByOrNull { it.valueParameters.size }
        if (constructorsWithMinValueParams != null) {
            var parenthesesOffset = 0
            val parenthesesText = buildString {
                // add `<>` and put caret within then if ctor with some generics
                if (constructorsWithMinValueParams.typeParameters.isNotEmpty()) {
                    append("<>")
                    parenthesesOffset = 1
                } else {
                    // otherwise put a caret within `()` if there is at least one non-empty ctor
                    // or, put a caret right after `()`
                    parenthesesOffset =
                        if (constructorsWithMinValueParams.valueParameters.isNotEmpty() ||
                            classDescriptor.constructors.any { it.valueParameters.isNotEmpty() }
                        ) {
                            1
                        } else {
                            2
                        }
                }
                append("()")
            }
            val offset = editor.caretModel.offset
            document.insertString(offset, parenthesesText)
            editor.caretModel.moveToOffset(offset + parenthesesOffset)
            PsiDocumentManager.getInstance(context.project).commitDocument(context.document)
        }
    }

    private fun qualifiedName(lookupObject: DescriptorBasedDeclarationLookupObject): String {
        return if (lookupObject.descriptor != null) {
            IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(lookupObject.descriptor as ClassifierDescriptor)
        } else {
            val qualifiedName = (lookupObject.psiElement as PsiClass).qualifiedName!!
            if (FqNameUnsafe.isValid(qualifiedName)) FqNameUnsafe(qualifiedName).render() else qualifiedName
        }
    }
}
