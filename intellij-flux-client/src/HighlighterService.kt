package org.intellij.flux

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.flux.EditorTopics
import org.jetbrains.flux.MessageConnector
import org.jetbrains.json.ArrayMemberWriter
import java.awt.Font

fun highlighterService(project: Project) = ServiceManager.getService(project, javaClass<HighlighterService>())

data class Entry(val highlighter: EditorHighlighter, val document: Document)

class FluxHighlighterClient(private val project: Project, private val document: Document) : HighlighterClient {
  override fun getDocument() = document

  override fun getProject() = project

  override fun repaint(start: Int, end: Int) {
  }
}

class HighlighterService(private val project: Project) : Disposable {
  val pathToEditor = ContainerUtil.newConcurrentMap<String, Entry>()

  private fun getHighlighter(document: Document, file: VirtualFile, resourcePath: String): EditorHighlighter {
    var entry = pathToEditor.get(resourcePath)
    if (entry == null) {
      val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
      document.addDocumentListener(highlighter)
      highlighter.setText(document.getImmutableCharSequence())
      highlighter.setEditor(FluxHighlighterClient(project, document))

      entry = Entry(highlighter, document)
      val previous = pathToEditor.putIfAbsent(resourcePath, entry)
      if (previous != null) {
        document.removeDocumentListener(highlighter)
        entry = previous
      }
    }
    return entry.highlighter
  }

  override fun dispose() {
    val list = pathToEditor.values().copyToArray()
    try {
      pathToEditor.clear()
    }
    finally {
      for (i in list) {
        try {
          i.document.removeDocumentListener(i.highlighter)
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    }
  }

  // see HTMLTextPainter
  fun sendHighlighting(document: Document, file: VirtualFile, resourcePath: String, replyTo: String, changedStart: Int, changedEnd: Int, messageConnector: MessageConnector) {
    messageConnector.replyToEvent(replyTo, EditorTopics.styleReady.name) {
      // we provide style per line, so, we cannot stop at changed end offset, we must compute style for the whole line
      val maxLine = document.getLineNumber(changedEnd)
      val minLine = if (changedStart == 0) 0 else document.getLineNumber(changedStart)
      val iterator = getHighlighter(document, file, resourcePath).createIterator(if (changedStart == 0) 0 else document.getLineStartOffset(minLine))
      var prevLine = -1
      map("lineStyles") {
        while (!iterator.atEnd()) {
          val start = iterator.getStart()
          val end = iterator.getEnd()

          // createIterator(87) (start of line 4 " ") -> iterator.getStart() -> 86 (end of line 3 "\n")
          // to solve this problem we just ignore it - don't describe line 3, use initially computed line range
          // for now it doesn't lead to any issues, but may be in the future some fix could be required
          //
          // 0 package com.company;
          // 1
          // 2 public class Main {
          // 3  public static void main(String[] args) {_
          // 4 __int f = 12313;
          // 5  }
          // 6 }
          val startLine = Math.max(minLine, document.getLineNumber(start))
          if (startLine > maxLine) {
            break
          }

          // the same problem as above. range: "\n  "
          val endLine = Math.min(maxLine, document.getLineNumber(end))

          for (line in startLine..endLine) {
            if (line != prevLine) {
              if (prevLine != -1) {
                // end ranges array
                endArray()
                // end line style map
                endObject()
              }

              // begin line style map
              name(line.toString())
              beginObject()
              // begin ranges array
              name("ranges")
              beginArray()
            }

            val lineStartOffset = document.getLineStartOffset(line)
            (this as ArrayMemberWriter).map {
              // if start less than lineStartOffset, it means that range overlaps several lines and current line is not the first in the overlapped lines
              val relativeStart = if (start < lineStartOffset) 0 else (start - lineStartOffset)
              "start"(relativeStart)

              val effectiveEnd = if (line == endLine) end else Math.min(end, document.getLineEndOffset(line))
              val relativeEnd = effectiveEnd - lineStartOffset
              "end"(relativeEnd)

              map("style") {
                val textAttributes = iterator.getTextAttributes()
                map("style") {
                  val foreColor = textAttributes.getForegroundColor()
                  if (foreColor != null) {
                    "color"(foreColor.toHex())
                  }
                  if ((textAttributes.getFontType() and Font.BOLD) != 0) {
                    "font-weight"("bold")
                  }
                  if ((textAttributes.getFontType() and Font.ITALIC) != 0) {
                    "font-style"("italic")
                  }
                }
              }
            }

            prevLine = line
          }

          iterator.advance()
        }

        if (prevLine != -1) {
          // end ranges array
          endArray()
          // end line style map
          endObject()
        }
      }
    }
  }
}