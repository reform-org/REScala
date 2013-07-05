package texteditor.signalsAndEventsFromEventsOnly

import scala.swing.BorderPanel
import scala.swing.BorderPanel.Position
import scala.swing.Dimension
import scala.swing.GridPanel
import scala.swing.MainFrame
import scala.swing.ScrollPane
import scala.swing.SimpleSwingApplication

import macro.SignalMacro.{SignalM => Signal}
import react.SignalSynt
import reswing.ReButton
import reswing.ReLabel

object Application extends SimpleSwingApplication {
  // reactive components
  val textArea = new TextArea("Lorem ipsum dolor sit amet\nconsectetur adipisicing elit\nsed do eiusmod")
  
  val positionLabel = ReLabel(Signal { //#SIG
    val pos = textArea.caret.position()
    "Ln " + (pos.row + 1) + " : " + textArea.lineCount() + "    Col " + (pos.col + 1)
  })
  
  val selectionLabel = ReLabel(
    Signal { "Sel " + textArea.selected().size }) //#SIG
  
  val charCountLabel = ReLabel(Signal { "Ch " + textArea.charCount() })  //#SIG
  
  val wordCountLabel = ReLabel(Signal { "Ch " + textArea.wordCount() })  //#SIG
  
  val selectAllButton = ReButton("Select All")
  selectAllButton.clicked += { _ => textArea.selectAll; textArea.requestFocus } //#HDL
  
  val copyButton = ReButton("Copy")
  copyButton.clicked += { _ => textArea.copy; textArea.requestFocus } //#HDL
  
  val pasteButton = ReButton("Paste")
  pasteButton.clicked += { _ => textArea.paste; textArea.requestFocus } //#HDL
  
  // layout
  def top = new MainFrame {
    title = "TextEditor (signals1)"
    preferredSize = new Dimension(400, 400)
    contents = new BorderPanel {
      layout(new ScrollPane(textArea)) = Position.Center
      layout(new GridPanel(1, 0) {
        contents += selectAllButton
        contents += copyButton
        contents += pasteButton
      }) = Position.North
      layout(new GridPanel(1, 0) {
        contents += positionLabel
        contents += selectionLabel
        contents += charCountLabel
        contents += wordCountLabel
      }) = Position.South
    }
  }
}
