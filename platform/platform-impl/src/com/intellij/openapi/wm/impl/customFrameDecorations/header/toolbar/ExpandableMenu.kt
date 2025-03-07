// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceRangeToWithUntil")

package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.IJSwingUtilities
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private const val ALPHA = (255 * 0.6).toInt()

internal class ExpandableMenu(private val headerContent: JComponent) {

  val ideMenu: IdeMenuBar = IdeMenuBar.createMenuBar()
  private val ideMenuHelper = IdeMenuHelper(ideMenu, null)
  private var expandedMenuBar: JPanel? = null
  private var headerColorfulPanel: HeaderColorfulPanel? = null
  private val shadowComponent = ShadowComponent()
  private val rootPane: JRootPane?
    get() = SwingUtilities.getRootPane(headerContent)
  private var hideMenu = false

  init {
    MenuSelectionManager.defaultManager().addChangeListener {
      if (MenuSelectionManager.defaultManager().selectedPath.isNullOrEmpty()) {
        // After resetting selectedPath another menu can be shown right after that, so don't hide main menu immediately
        hideMenu = true
        ApplicationManager.getApplication().invokeLater {
          if (hideMenu) {
            hideMenu = false
            hideExpandedMenuBar()
          }
        }
      } else {
        hideMenu = false
      }
    }

    ideMenuHelper.installListeners()

    headerContent.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateBounds()
      }
    })
  }

  fun isEnabled(): Boolean {
    return !SystemInfoRt.isMac && Registry.`is`("ide.main.menu.expand.horizontal")
  }

  private fun isShowing(): Boolean {
    return expandedMenuBar != null
  }

  private fun updateUI() {
    IJSwingUtilities.updateComponentTreeUI(ideMenu)
    ideMenu.border = null
    ideMenuHelper.updateUI()
  }

  fun switchState(actionToShow: AnAction? = null) {
    if (isShowing() && actionToShow == null) {
      hideExpandedMenuBar()
      return
    }

    hideExpandedMenuBar()
    val layeredPane = rootPane?.layeredPane ?: return

    expandedMenuBar = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          headerColorfulPanel = cell(HeaderColorfulPanel(ideMenu))
            .align(AlignY.FILL)
            .component
          cell(shadowComponent).align(Align.FILL)
        }.resizableRow()
      }
    }.apply { isOpaque = false }

    // menu wasn't a part of component's tree, updateUI is needed
    updateUI()
    updateBounds()
    updateColor()
    layeredPane.add(expandedMenuBar!!, (JLayeredPane.DEFAULT_LAYER - 2) as Any)

    // First menu usage has no selection in menu. Fix it by invokeLater
    ApplicationManager.getApplication().invokeLater {
      selectMenu(actionToShow)
    }
  }

  private fun selectMenu(action: AnAction? = null) {
    var menu = ideMenu.getMenu(0)
    if (action != null) {
      for (i in 0..ideMenu.menuCount - 1) {
        val m = ideMenu.getMenu(i)
        if (m.mnemonic == action.templatePresentation.mnemonic) {
          menu = m
          break
        }
      }
    }

    val subElements = menu.popupMenu.subElements
    if (subElements.isEmpty()) {
      MenuSelectionManager.defaultManager().selectedPath = arrayOf(ideMenu, menu)
    }
    else {
      MenuSelectionManager.defaultManager().selectedPath = arrayOf(ideMenu, menu, menu.popupMenu, subElements[0])
    }
  }

  fun updateColor() {
    val color = headerContent.background
    headerColorfulPanel?.background = color
    @Suppress("UseJBColor")
    shadowComponent.background = Color(color.red, color.green, color.blue, ALPHA)
  }

  private fun updateBounds() {
    val location = SwingUtilities.convertPoint(headerContent, 0, 0, rootPane ?: return)
    if (location == null) {
      headerColorfulPanel?.horizontalOffset = 0
    } else {
      val insets = headerContent.insets
      headerColorfulPanel?.horizontalOffset = location.x + insets.left
      expandedMenuBar?.let {
        it.bounds = Rectangle(location.x + insets.left, location.y + insets.top,
                              headerContent.width - insets.left - insets.right,
                              headerContent.height - insets.top - insets.bottom)
      }
    }
  }

  private fun hideExpandedMenuBar() {
    if (isShowing()) {
      rootPane?.layeredPane?.remove(expandedMenuBar)
      expandedMenuBar = null
      headerColorfulPanel = null

      rootPane?.repaint()
    }
  }

  private class HeaderColorfulPanel(component: JComponent): JPanel() {

    var horizontalOffset = 0

    init {
      // Deny background painting by super.paint()
      isOpaque = false
      layout = BorderLayout()
      add(component, BorderLayout.CENTER)
    }

    override fun paint(g: Graphics?) {
      g as Graphics2D
      g.color = background
      g.fillRect(0, 0, width, height)
      g.translate(-horizontalOffset, 0)
      val root = SwingUtilities.getRoot(this) as? Window
      if (root != null) ProjectWindowCustomizerService.getInstance().paint(root, this, g)
      g.translate(horizontalOffset, 0)
      super.paint(g)
    }
  }

  private inner class ShadowComponent : JComponent() {

    init {
      isOpaque = false

      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent?) {
          hideExpandedMenuBar()
        }
      })
    }

    override fun paint(g: Graphics?) {
      g ?: return
      g.color = background
      g.fillRect(0, 0, width, height)
    }
  }
}