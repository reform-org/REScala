package kofre.primitives

import kofre.base.Lattice
import kofre.base.Lattice.Operators
import kofre.base.Defs
import kofre.causality.{CausalContext, Dot, VectorClock}
import kofre.primitives.CausalQueue.QueueElement

import scala.collection.immutable.Queue

case class CausalQueue[T](values: Queue[QueueElement[T]], latest: VectorClock, removed: CausalContext) {
  def enqueue(e: T, replicaID: Defs.Id): CausalQueue[T] =
    val dot  = latest.inc(replicaID)
    val time = latest merge dot
    CausalQueue(values.enqueue(QueueElement(e, Dot(replicaID, dot.timestamps(replicaID)), time)), time, removed)

  def dequeue(): CausalQueue[T] =
    val (QueueElement(_, dot, _), tail) = values.dequeue
    CausalQueue(tail, latest, removed.add(dot))
}

object CausalQueue:
  case class QueueElement[T](value: T, dot: Dot, order: VectorClock)

  def empty[T]: CausalQueue[T] = CausalQueue(Queue(), VectorClock.zero, CausalContext.empty)

  given lattice[A: Ordering]: Lattice[CausalQueue[A]] = (left, right) =>
    val removed = left.removed merge right.removed
    def it      = left.values.iterator ++ right.values.iterator
    val res =
      it.filter { case QueueElement(_, d, _) => !removed.contains(d) }.to(Queue)
        .sortBy { case QueueElement(_, _, order) => order }(using VectorClock.vectorClockTotalOrdering).distinct
    CausalQueue(res, left.latest merge right.latest, removed)