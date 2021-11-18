package central

import calendar.{Appointment, Token}
import loci.registry.Binding
import loci.transmitter.transmittable.IdenticallyTransmittable
import loci.serializer.jsoniterScala._
import rescala.extra.lattices.RaftState
import rescala.extra.lattices.delta.CContext.DietMapCContext
import rescala.extra.lattices.delta.crdt.reactive.AWSet
import rescala.extra.lattices.delta.Codecs._

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._

import scala.concurrent.Future

sealed trait SyncMessage
object SyncMessage {
  type CalendarState = AWSet.State[Appointment, DietMapCContext]

  case class AppointmentMessage(state: CalendarState, target: String) extends SyncMessage
  case class RaftMessage(state: RaftState[Token]) extends SyncMessage
  case class WantMessage(state: AWSet.State[Token, DietMapCContext]) extends SyncMessage
  case class FreeMessage(state: AWSet.State[Token, DietMapCContext]) extends SyncMessage

}

object Bindings {

  implicit val AppointmentCodec: JsonValueCodec[Appointment] = JsonCodecMaker.make
  implicit val TokenCodec: JsonValueCodec[Token] = JsonCodecMaker.make
  //implicit val RaftCodec: JsonValueCodec[RaftState[Token]] = JsonCodecMaker.make
  //
  //implicit val RaftMessageCodec: JsonValueCodec[RaftMessage] = JsonCodecMaker.make
  //implicit val WantMessageCodec: JsonValueCodec[WantMessage] = JsonCodecMaker.make
  //implicit val FreeMessageCodec: JsonValueCodec[FreeMessage] = JsonCodecMaker.make
  //implicit val AppointmentMessageCodec: JsonValueCodec[AppointmentMessage] = JsonCodecMaker.make


  implicit val SyncMessageCodec: JsonValueCodec[SyncMessage] = JsonCodecMaker.make


  implicit val transmittableSyncMessage: IdenticallyTransmittable[SyncMessage] = IdenticallyTransmittable()

  val receiveSyncMessageBinding: Binding[SyncMessage => Unit, SyncMessage => Future[Unit]] =
    Binding[SyncMessage => Unit]("receiveDelta")

}