package kofre.dotbased

import kofre.Defs.Id
import kofre.Lattice
import kofre.causality.{CausalContext, Dot}

/** Dot stores provide a generic way to merge datastructures,
  * implemented on top of one of the provided dot stores.
  * See: Delta state replicated data types (https://doi.org/10.1016/j.jpdc.2017.08.003)
  *
  * A dot store seems to essentially be a lattice of different causal instances,
  * with some bonus operations. But not quite.
  */
trait DotStore[Store] {
  def add(a: Store, d: Dot): Store

  def dots(a: Store): Set[Dot]

  def empty: Store

  /** The new element contains all the dots that are either
    * contained in both dotstores or contained in one of the dotstores but not in the causal context (history) of the
    * other one.
    */
  def merge(left: CausalStore[Store], right: CausalStore[Store]): CausalStore[Store]
}

object DotStore {
  type DotSet       = CausalContext
  type DotFun[V]    = Map[Dot, V]
  type DotMap[K, V] = Map[K, V]

  // Todo: V should be a SemiLattice according to paper
  implicit def dotFunDotStore[V]: DotStore[DotFun[V]] = new DotStore[DotFun[V]] {

    override def add(a: DotFun[V], d: Dot): DotFun[V] = ???
    override def empty: DotFun[V]                     = Map.empty

    override def merge(left: CausalStore[DotFun[V]], right: CausalStore[DotFun[V]]): CausalStore[DotFun[V]] = ???

    override def dots(dotStore: DotFun[V]): Set[Dot] = dotStore.keySet

  }

  def next[A: DotStore](id: Id, c: A): Dot = {
    val dotsWithId = DotStore[A].dots(c).filter(_.replicaId == id)
    val maxCount   = if (dotsWithId.isEmpty) 0 else dotsWithId.map(_.time).max
    Dot(id, maxCount + 1)
  }

  def merge[A: DotStore](left: CausalStore[A], right: CausalStore[A]): CausalStore[A] = {
    DotStore[A].merge(left, right)
  }

  def apply[A](implicit dotStore: DotStore[A]): dotStore.type = dotStore

  // instances

  implicit val CausalContextDotStoreInstance: DotStore[CausalContext] =
    new DotStore[CausalContext] {
      type Store = CausalContext

      override def add(a: Store, d: Dot): Store = a.add(d.replicaId, d.time)

      override def dots(a: Store): Set[Dot] = a.toSet

      override def empty: Store = CausalContext.empty

      override def merge(left: CausalStore[Store], right: CausalStore[Store]): CausalStore[Store] = {
        val common      = left.store intersect right.store
        val newElements = (left.store diff right.context) union (right.store diff left.context)
        CausalStore(common union newElements, left.context union right.context)
      }
    }

  implicit val DotSetInstance: DotStore[Set[Dot]] =
    new DotStore[Set[Dot]] {
      type Store = Set[Dot]

      override def add(a: Store, d: Dot): Store = a + d

      override def dots(a: Store): Store = a

      override def empty: Store = Set.empty

      override def merge(left: CausalStore[Store], right: CausalStore[Store]): CausalStore[Store] = {
        val common      = left.store intersect right.store
        val newElements = (left.store diff right.context.toSet) union (right.store diff left.context.toSet)
        CausalStore(common union newElements, left.context union right.context)
      }
    }

  implicit def DotMapInstance[Key, A](implicit dsl: DotStore[A]): DotStore[Map[Key, A]] =
    new DotStore[Map[Key, A]] {
      type Store = Map[Key, A]

      override def add(a: Store, d: Dot): Store = a.view.mapValues(v => dsl.add(v, d)).toMap

      override def dots(a: Store): Set[Dot] = a.valuesIterator.flatMap(dsl.dots).toSet

      override def empty: Store = Map.empty

      override def merge(left: CausalStore[Store], right: CausalStore[Store]): CausalStore[Store] = {

        val empty = DotStore[A].empty

        // The new store is everything both sides have seen and everything that is new.
        // If something is missing from the store (but in the context) it has been deleted.
        val newStore: Store = (left.store.keySet union right.store.keySet).map { id =>
          val value = DotStore[A].merge(
            CausalStore(left.store.getOrElse(id, empty), left.context),
            CausalStore(right.store.getOrElse(id, empty), right.context)
          )
          (id, value.store)
        }.filter { _._2 != empty }
          .toMap

        // the merged state has seen everything from both sides
        val newContext = left.context union right.context
        CausalStore(newStore, newContext)
      }
    }
}
