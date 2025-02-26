package kofre.datatypes.alternatives

import kofre.base.Lattice.{Operators, derived}
import kofre.base.{Bottom, DecomposeLattice, Id, Lattice}
import kofre.time.VectorClock

import scala.annotation.tailrec

/** Keeps all concurrent writes */
case class MultiValueRegister[T](versions: Map[VectorClock, T]) {
  lazy val currentTime: VectorClock = {
    if (versions.isEmpty) VectorClock.zero
    else versions.keys.reduce((a, b) => a.merge(b))
  }

  def values: Iterable[T] = versions.values

  def write(replica: Id, value: T): MultiValueRegister[T] = {
    val timeOfUpdate = currentTime merge currentTime.inc(replica)
    MultiValueRegister(Map(timeOfUpdate -> value))
  }
}

object MultiValueRegister {
  given lattice[T]: DecomposeLattice[MultiValueRegister[T]] = new DecomposeLattice[MultiValueRegister[T]] {
    override def merge(left: MultiValueRegister[T], right: MultiValueRegister[T]): MultiValueRegister[T] =
      val both   = left.versions ++ right.versions
      val toKeep = parallelVersionSubset(both.keySet.toList, List.empty)
      MultiValueRegister(both.filter { case (k, v) => toKeep.contains(k) })

    override def decompose(a: MultiValueRegister[T]): Iterable[MultiValueRegister[T]] = {
      a.versions.view.map(pair => MultiValueRegister(Map(pair)))
    }
    override def lteq(left: MultiValueRegister[T], right: MultiValueRegister[T]): Boolean = {
      left.versions.forall((k, v) => right.versions.get(k).contains(v))
    }
  }

  given bottom[T]: Bottom[MultiValueRegister[T]] = Bottom.derived

  @tailrec
  def parallelVersionSubset(remaining: List[VectorClock], acc: List[VectorClock]): List[VectorClock] =
    remaining match
      case Nil       => acc
      case h :: tail =>
        // remove smaller ones from the list we operate on
        val rem = tail.filterNot(e => e <= h)
        // remove smaller ones from acc
        val nacc = acc.filterNot(e => e <= h)
        // continue
        parallelVersionSubset(rem, h :: nacc)
}
