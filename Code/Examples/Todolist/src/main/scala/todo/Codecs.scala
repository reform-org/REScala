package todo

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonReader, JsonValueCodec, JsonWriter}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import kofre.base.Defs
import kofre.datatypes.TimedVal
import kofre.time.Dot
import kofre.decompose.containers.DeltaBufferRDT
import kofre.decompose.interfaces.LWWRegisterInterface.LWWRegister
import kofre.decompose.interfaces.{LWWRegisterInterface, RGA}
import kofre.dotted.{DotFun, Dotted}
import loci.transmitter.IdenticallyTransmittable
import rescala.extra.lattices.delta.JsoniterCodecs._
import todo.Todolist.replicaId

object Codecs {

  implicit val taskRefCodec: JsonValueCodec[TaskRef] = JsonCodecMaker.make
  implicit val dotKeyCodec: JsonKeyCodec[Dot]   = new JsonKeyCodec[Dot] {
    override def decodeKey(in: JsonReader): Dot = {
      val Array(time, id) = in.readKeyAsString().split("-", 2)
      Dot(id.asInstanceOf[Defs.Id], time.asInstanceOf[Defs.Time])
    }
    override def encodeKey(x: Dot, out: JsonWriter): Unit = out.writeKey(s"${x.time}-${x.replicaId}")
  }

  implicit val codecState: JsonValueCodec[Dotted[RGA[TaskRef]]]       = RGAStateCodec
  implicit val codecRGA: JsonValueCodec[DeltaBufferRDT[RGA[TaskRef]]] =
    new JsonValueCodec[DeltaBufferRDT[RGA[TaskRef]]] {
      override def decodeValue(
          in: JsonReader,
          default: DeltaBufferRDT[RGA[TaskRef]]
      ): DeltaBufferRDT[RGA[TaskRef]] = {
        val state = codecState.decodeValue(in, default.state)
        new DeltaBufferRDT[RGA[TaskRef]](state, replicaId, List())
      }
      override def encodeValue(x: DeltaBufferRDT[RGA[TaskRef]], out: JsonWriter): Unit =
        codecState.encodeValue(x.state, out)
      override def nullValue: DeltaBufferRDT[RGA[TaskRef]] = DeltaBufferRDT(replicaId, RGA.empty[TaskRef])
    }

  implicit val transmittableList: IdenticallyTransmittable[Dotted[RGA[TaskRef]]] =
    IdenticallyTransmittable()

  implicit val todoTaskCodec: JsonValueCodec[TaskData] = JsonCodecMaker.make

  implicit val codecLwwState: JsonValueCodec[Dotted[DotFun[TimedVal[TaskData]]]] = JsonCodecMaker.make

  implicit val transmittableLWW: IdenticallyTransmittable[Dotted[LWWRegister[TaskData]]] =
    IdenticallyTransmittable()

  type LwC = DeltaBufferRDT[LWWRegister[TaskData]]
  implicit val codecLww: JsonValueCodec[LwC] =
    new JsonValueCodec[LwC] {
      override def decodeValue(in: JsonReader, default: LwC): LwC = {
        val state: Dotted[LWWRegister[TaskData]] = codecLwwState.decodeValue(in, default.state)
        new DeltaBufferRDT[LWWRegister[TaskData]](state, replicaId, List())
      }
      override def encodeValue(x: LwC, out: JsonWriter): Unit = codecLwwState.encodeValue(x.state, out)
      override def nullValue: LwC = {
        println(s"reading null")
        DeltaBufferRDT(replicaId, LWWRegisterInterface.empty[TaskData])
      }
    }



}
