package org.intellij.flux

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.SettingsImpl
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import java.awt.Insets
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.border.Border
import kotlin.properties.Delegates

class FakeEditor(private var document: Document, private val project: Project) : Editor, UserDataHolderBase() {
  private val _settings: EditorSettings by Delegates.lazy {
    SettingsImpl(null, project)
  }

  override fun getScrollingModel(): ScrollingModel {
    throw UnsupportedOperationException()
  }

  override fun getDocument(): Document = document

  override fun visualPositionToXY(visible: VisualPosition): Point {
    throw UnsupportedOperationException()
  }

  override fun addEditorMouseMotionListener(listener: EditorMouseMotionListener) {
    throw UnsupportedOperationException()
  }

  override fun visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition {
    throw UnsupportedOperationException()
  }

  override fun getCaretModel(): CaretModel {
    throw UnsupportedOperationException()
  }

  override fun offsetToVisualPosition(offset: Int): VisualPosition {
    throw UnsupportedOperationException()
  }

  override fun setBorder(border: Border?) {
    throw UnsupportedOperationException()
  }

  override fun isInsertMode(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getProject() = project

  override fun getSoftWrapModel(): SoftWrapModel {
    throw UnsupportedOperationException()
  }

  override fun isOneLineMode() = false

  override fun isDisposed() = project.isDisposed()

  override fun xyToVisualPosition(p: Point): VisualPosition {
    throw UnsupportedOperationException()
  }

  override fun logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition {
    throw UnsupportedOperationException()
  }

  override fun getSettings() = _settings

  override fun getMouseEventArea(e: MouseEvent): EditorMouseEventArea? {
    throw UnsupportedOperationException()
  }

  override fun offsetToLogicalPosition(offset: Int): LogicalPosition {
    throw UnsupportedOperationException()
  }

  override fun logicalPositionToOffset(pos: LogicalPosition): Int {
    throw UnsupportedOperationException()
  }

  override fun getLineHeight(): Int {
    throw UnsupportedOperationException()
  }

  override fun getSelectionModel(): SelectionModel {
    throw UnsupportedOperationException()
  }

  override fun removeEditorMouseListener(listener: EditorMouseListener) {
    throw UnsupportedOperationException()
  }

  override fun getInsets(): Insets? {
    throw UnsupportedOperationException()
  }

  override fun removeEditorMouseMotionListener(listener: EditorMouseMotionListener) {
    throw UnsupportedOperationException()
  }

  override fun getFoldingModel(): FoldingModel {
    throw UnsupportedOperationException()
  }

  override fun getIndentsModel(): IndentsModel {
    throw UnsupportedOperationException()
  }

  override fun addEditorMouseListener(listener: EditorMouseListener) {
    throw UnsupportedOperationException()
  }

  override fun isViewer(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getGutter(): EditorGutter {
    throw UnsupportedOperationException()
  }

  override fun xyToLogicalPosition(p: Point): LogicalPosition {
    throw UnsupportedOperationException()
  }

  override fun getColorsScheme(): EditorColorsScheme {
    throw UnsupportedOperationException()
  }

  override fun isColumnMode(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getContentComponent(): JComponent {
    throw UnsupportedOperationException()
  }

  override fun getHeaderComponent(): JComponent? {
    throw UnsupportedOperationException()
  }

  override fun setHeaderComponent(header: JComponent?) {
    throw UnsupportedOperationException()
  }

  override fun hasHeaderComponent(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun logicalPositionToXY(pos: LogicalPosition): Point {
    throw UnsupportedOperationException()
  }

  override fun getMarkupModel(): MarkupModel {
    throw UnsupportedOperationException()
  }

  override fun getComponent(): JComponent {
    throw UnsupportedOperationException()
  }
}