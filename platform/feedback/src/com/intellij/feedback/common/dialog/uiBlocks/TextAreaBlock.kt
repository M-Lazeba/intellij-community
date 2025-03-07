// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.dialog.TEXT_AREA_COLUMN_SIZE
import com.intellij.feedback.common.dialog.TEXT_AREA_ROW_SIZE
import com.intellij.feedback.common.dialog.adjustBehaviourForFeedbackForm
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

class TextAreaBlock(@NlsContexts.Label private val myLabel: String,
                    private val myJsonElementName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {
  private var myProperty: String = ""
  private var myTextAreaRowSize: Int = TEXT_AREA_ROW_SIZE
  private var myTextAreaColumnSize: Int = TEXT_AREA_COLUMN_SIZE
  private var myRequireNotEmptyMessage: String? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        textArea()
          .bindText(::myProperty)
          .rows(myTextAreaRowSize)
          .columns(myTextAreaColumnSize)
          .label(myLabel, LabelPosition.TOP)
          .applyToComponent {
            adjustBehaviourForFeedbackForm()
          }
          .apply {
            if (myRequireNotEmptyMessage != null) {
              errorOnApply(myRequireNotEmptyMessage!!) {
                myProperty.isBlank()
              }
            }
          }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myLabel)
      appendLine(myProperty)
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonElementName, myProperty)
    }
  }

  fun setRowSize(rowSize: Int): TextAreaBlock {
    myTextAreaRowSize = rowSize
    return this
  }

  fun setColumnSize(columnSize: Int): TextAreaBlock {
    myTextAreaColumnSize = columnSize
    return this
  }

  fun requireNotEmpty(@NlsContexts.Label requireNotEmptyMessage: String): TextAreaBlock {
    myRequireNotEmptyMessage = requireNotEmptyMessage
    return this
  }
}