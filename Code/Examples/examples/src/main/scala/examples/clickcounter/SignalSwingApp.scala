package examples.clickcounter

import rescala.default._

import scala.swing._
import scala.swing.event._

// Wrap a bit of Swing, as if it were an FRP library
trait ReactiveText {
  def text_=(s: String): Unit
  def text_=(value: Signal[String]): Unit = {
    this.text_=(value.now)
    value.changed += { (t: String) => this.text_=(t) }
    ()
  }
}
class ReactiveLabel extends Label with ReactiveText
class ReactiveButton extends Button with ReactiveText {
  val clicked = Evt[ButtonClicked]() // wrap the event to escala
  reactions += { case c @ ButtonClicked(_) => clicked.fire(c) }
}

// The application
object SignalSwingApp extends SimpleSwingApplication {

  def top =
    new MainFrame {
      title = "Reactive Swing App"

      val label  = new ReactiveLabel
      val button = new ReactiveButton

      val nClicks = button.clicked.fold(0) { (x, _) => x + 1 }

      // Signal to set label text
      label.text = Signal { (if (nClicks() == 0) "No" else nClicks()).toString + " button clicks registered" }

      // Alternative with switch
      // label.text = Signal {"No clicks"}.switchOnce(button.clicked)( Signal{ nClicks() + " clicks registered" } )

      button.text = Signal { "Click me" + (if (nClicks() == 0) "!" else " again") }

      contents = new BoxPanel(Orientation.Vertical) {
        contents += button
        contents += label
        border = Swing.EmptyBorder(30, 30, 10, 30)
      }
    }
}
