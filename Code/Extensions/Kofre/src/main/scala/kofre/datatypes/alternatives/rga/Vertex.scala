package kofre.datatypes.alternatives.rga

import kofre.base.{Id, Time}
import Vertex.Timestamp

case class Vertex(timestamp: Timestamp, id: Id)

object Vertex {
  type Timestamp = Long

  val start: Vertex = Vertex(-1, Id.predefined("start"))
  val end: Vertex   = Vertex(0, Id.predefined("end"))

  def fresh[A](): Vertex = Vertex(Time.current(), Id.gen())
}
