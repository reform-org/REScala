package examples.demo

import java.awt.event.KeyEvent

import examples.demo.LFullyModularBall.BouncingBall
import examples.demo.MPlayingFieldBall.PlayingField
import examples.demo.ORacketMultiBall.Racket
import examples.demo.ui.{Shape, ShapesPanel}
import rescala.default._
import rescala.operator.Pulse

import scala.swing.{Dimension, MainFrame, SimpleSwingApplication}
import scala.util.Try

object RRecovery extends Main {
  class Opponent(panelSize: Signal[Dimension], shapes: Signal[List[Shape]]) extends SimpleSwingApplication {
    val panel2 = new ShapesPanel(shapes)

    override lazy val top = new MainFrame {
      title = "Player 2"
      contents = panel2
      resizable = false
      override def closeOperation(): Unit = {
        val s = panel2.Mouse._position
        transaction(s) { t =>
          s.admitPulse(Pulse.Exceptional(new IllegalArgumentException))(t)
        }

      }
    }

    panelSize.observe { d =>
      panel2.preferredSize = d
      top.pack()
    }
  }
  val shapes         = Var[List[Shape]](List.empty)
  val filteredShapes = Signal.dynamic { shapes().filter { q => Try(q.changed()).isSuccess } }
  filteredShapes.observe(shapes.set)
  val panel = new ShapesPanel(filteredShapes)

  val playingField = new PlayingField(panel.width.map(_ - 25), panel.height.map(_ - 25))
  val racket       = new Racket(playingField.width, true, playingField.height, panel.Mouse.y)
  shapes.transform(playingField.shape :: racket.shape :: _)

  val balls = List(
    new BouncingBall(200d, 150d, Var(50), panel.Mouse.middleButton.pressed),
    new BouncingBall(-200d, 100d, Var(50), panel.Mouse.middleButton.pressed)
  )

  for (bouncingBall <- balls) {
    shapes.transform(bouncingBall.shape :: _)

    val fieldCollisions = playingField.colliders(bouncingBall.shape)
    bouncingBall.horizontalBounceSources.transform(fieldCollisions.left :: fieldCollisions.right :: _)
    bouncingBall.verticalBounceSources.transform(fieldCollisions.top :: fieldCollisions.bottom :: _)

    val racketCollision = racket.collisionWith(bouncingBall.shape)
    bouncingBall.horizontalBounceSources.transform(racketCollision :: _)
  }

  def addOpponent() = {

    val opponent = new Opponent(panel.sigSize, filteredShapes)
    opponent.main(Array())
    val racket2 = new Racket(playingField.width, false, playingField.height, opponent.panel2.Mouse.y)
    shapes.transform(racket2.shape :: _)
    racket2.posY.observe(
      _ => (),
      { _ =>
        shapes.transform(_.filter(_ != racket2.shape))
      }
    )

    for (bouncingBall <- balls) {
      bouncingBall.horizontalBounceSources.transform(racket2.collisionWith(bouncingBall.shape) :: _)
    }
  }

  panel.Keyboard.released.map(_.getKeyChar).observe {
    case KeyEvent.VK_ENTER => addOpponent()
    case _                 =>
  }
}
