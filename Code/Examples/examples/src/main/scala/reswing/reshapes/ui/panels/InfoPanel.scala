package reswing.reshapes.ui.panels

import scala.swing.FlowPanel

import rescala.default._
import reswing.reshapes.ReShapes
import reswing.ReLabel

/** Small info panel which displays information like how many shapes are drawn
  * or which shape is currently selected
  */
class InfoPanel extends FlowPanel {
  def state = ReShapes.drawingSpaceState

  val shapeCount = Signal.dynamic { // #SIG
    if (state() != null) "#elements: %d" format state().shapes().size else ""
  }

  val color = Signal.dynamic { // #SIG
    if (state() != null)
      "color: %d-%d-%d".format(state().color().getRed, state().color().getGreen, state().color().getBlue)
    else ""
  }

  val strokeWidth = Signal.dynamic { // #SIG
    if (state() != null) "stroke width: %d" format state().strokeWidth() else ""
  }

  val nextShape = Signal.dynamic { // #SIG
    if (state() != null) "next shape: %s" format state().nextShape().toString else ""
  }

  val selectedShape = Signal.dynamic { // #SIG
    if (state() != null && state().selectedShape() != null)
      "selected: %s".format(state().selectedShape().toString)
    else ""
  }

  contents += new ReLabel(Signal.dynamic { // #SIG //#IS( //)
    "%s | %s | %s | %s | %s".format(shapeCount(), color(), strokeWidth(), nextShape(), selectedShape())
  })
}
