package todo

import io.circe.generic.auto._
import io.circe.syntax._
import loci.communicator.experimental.webrtc.WebRTC.ConnectorFactory
import loci.communicator.experimental.webrtc._
import loci.registry.Registry
import org.scalajs.dom.{UIEvent, document}
import rescala.debuggable.ChromeDebuggerInterface
import rescala.lattices.sequences.RGA.RGA
import rescala.restoration.LocalStorageStore
import scalatags.JsDom.all._
import scalatags.JsDom.tags2.section

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


object Todolist {


  type TodoTransfer = RGA[taskHandling.Taskref]

  implicit val storingEngine: LocalStorageStore = new LocalStorageStore()

  val taskHandling = new TaskHandling
  val todoApp = TodoApp(taskHandling)

  val registry = new Registry

  def main(args: Array[String]): Unit = {

    ChromeDebuggerInterface.setup(storingEngine)

    val todores = todoApp.getContents()



    document.body.replaceChild(todores.div.render, document.body.firstElementChild)
    document.body.appendChild(webrtchandlingArea.render)


    ChromeDebuggerInterface.finishedLoading()
  }




  def webrtchandlingArea: Tag = {

    val renderedTa = textarea().render
    val renderedPre = pre().render

    var pendingServer: Option[PendingConnection] = None

    def connected() = {
      renderedPre.textContent = ""
      renderedTa.value = ""
    }

    def showSession(s: WebRTC.CompleteSession) = {
      val message = s.asJson.noSpaces
      renderedPre.textContent = message
      org.scalajs.dom.window.getSelection().selectAllChildren(renderedPre)
    }

    val hb = button("host", onclick := { uie: UIEvent =>
      val res = webrtcIntermediate(WebRTC.offer())
      res.session.foreach(showSession)
      pendingServer = Some(res)
      registry.connect(res.connector).foreach(_ => connected())
    })


    val cb = button("connect", onclick := { uie: UIEvent =>
      val cs = io.circe.parser.decode[WebRTC.CompleteSession](renderedTa.value).right.get
      val connector = pendingServer match {
        case None     => // we are client
          val res = webrtcIntermediate(WebRTC.answer())
          res.session.foreach(showSession)
          res.connector
        case Some(ss) => // we are server
          pendingServer = None
          ss.connector
      }
      connector.set(cs)
      registry.connect(connector).foreach(_ => connected())
    })

    section(hb, cb, renderedPre, renderedTa)
  }




  case class PendingConnection(connector: WebRTC.Connector,
                               session: Future[WebRTC.CompleteSession])

  def webrtcIntermediate(cf: ConnectorFactory) = {
    val p = Promise[WebRTC.CompleteSession]()
    val answer = cf complete p.success
    PendingConnection(answer, p.future)
  }


}