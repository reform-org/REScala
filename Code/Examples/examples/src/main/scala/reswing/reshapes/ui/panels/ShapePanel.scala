package reswing.reshapes.ui.panels

import scala.swing.BoxPanel
import scala.swing.Color
import scala.swing.Component
import scala.swing.Label
import scala.swing.Orientation
import scala.swing.ScrollPane
import rescala.default._
import rescala.operator.cutOutOfUserComputation
import reswing.reshapes.ReShapes
import reswing.reshapes.drawing.DeleteShape
import reswing.reshapes.drawing.DrawingSpaceState
import reswing.reshapes.figures.Shape
import reswing.reshapes.util.ReactiveUtil.UnionEvent
import reswing.ReBoxPanel
import reswing.ReButton

/** Lists all drawn shapes */
class ShapePanel extends BoxPanel(Orientation.Vertical) {
  @cutOutOfUserComputation
  def state = ReShapes.drawingSpaceState

  val shapes = Signal.dynamic { if (state() != null) state().shapes() else List.empty } // #SIG

  val shapeViews = Signal { shapes() map { shape => new ShapeView(shape, state()) } } // #SIG

  val shapesPanel = new ReBoxPanel(
    orientation = Orientation.Vertical,
    contents = Signal[Seq[Component]] { // #SIG
      shapeViews() map { (shapeView: ShapeView) => shapeView: Component }
    }
  )

  contents += new ScrollPane {
    contents = shapesPanel
  }

  val deleted = UnionEvent(Signal { shapeViews() map { shapeView => shapeView.deleted } }) // #SIG //#UE( //#EVT //#IF )
}

class ShapeView(shape: Shape, state: DrawingSpaceState) extends ReBoxPanel(Orientation.Horizontal) {
  val SELECTED_COLOR     = new Color(0, 153, 255)
  val NOT_SELECTED_COLOR = new Color(255, 255, 255)

  val deleteButton = new ReButton("delete")

  val deleted: Event[DeleteShape] = // #EVT
    deleteButton.clicked map { (_: Any) => new DeleteShape(shape) } // #EF

  peer.background = NOT_SELECTED_COLOR
  peer.contents += new Label(shape.toString)
  peer.contents += deleteButton

  mouse.clicks.clicked += { _ => // #HDL
    state.select.fire(if (state.selectedShape.now != shape) shape else null)
  }

  state.selectedShape.changed += { selected => // #HDL
    peer.background = if (selected == shape) SELECTED_COLOR else NOT_SELECTED_COLOR
  }
}
