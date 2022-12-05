package kofre.dotted

import kofre.base.{Bottom, DecomposeLattice, Lattice}
import kofre.dotted.DottedDecompose.FromConlattice
import kofre.decompose.interfaces
import kofre.time.{Dot, Dots}

import scala.annotation.targetName

/** The context describes lamport clocks that have been seen.
  * The store describes which value is associated for a given lamport clock.
  * Vector clocks that are removed from the store are considered deleted.
  * All others are merged as for normal maps.
  *
  * The delta CRDT paper calls this a DotFun
  */
case class DotFun[A](repr: Map[Dot, A]) {
  def dots: Dots = Dots.from(repr.keySet)
  @targetName("add")
  def +(tup: (Dot, A)): DotFun[A] = DotFun(repr + tup)
  export repr.{+ as _, repr as _, *}
}

/** The context describes lamport clocks that have been seen.
  * The store describes which value is associated for a given lamport clock.
  * Vector clocks that are removed from the store are considered deleted.
  * All others are merged as for normal maps.
  *
  * The delta CRDT paper calls this a DotFun
  */
object DotFun {

  def empty[A]: DotFun[A] = DotFun(Map.empty)

  given perDotLattice[A: Lattice]: DottedLattice[DotFun[A]] = (left, right) => {
    val fromLeft = left.store.repr.filter { case (dot, _) => !right.context.contains(dot) }

    DotFun(right.store.repr.foldLeft(fromLeft) {
      case (m, (dot, r)) =>
        left.store.repr.get(dot) match {
          case None =>
            if (left.context.contains(dot)) m
            else m.updated(dot, r)
          case Some(l) => m.updated(dot, Lattice[A].merge(l, r))
        }
    })
  }

  given dotStore[V]: HasDots[DotFun[V]] with {
    override def dots(dotStore: DotFun[V]): Dots = Dots.from(dotStore.repr.keySet)
  }

  /** DotFun is a dot store implementation that maps dots to values of a Lattice type. See [[interfaces.MVRegisterInterface]]
    * for a usage example.
    */
  given perDotDecompose[A: DecomposeLattice]: DottedDecompose[DotFun[A]] =
    new FromConlattice[DotFun[A]](perDotLattice[A]) {
      private def dots(a: DotFun[A]): Dots = dotStore.dots(a)

      override def lteq(left: Dotted[DotFun[A]], right: Dotted[DotFun[A]]): Boolean = {
        val firstCondition = left.context.forall(right.context.contains)
        val secondCondition = right.store.repr.keySet.forall { k =>
          left.store.repr.get(k).forall { l => DecomposeLattice[A].lteq(l, right.store.repr(k)) }
        }
        val thirdCondition = {
          val diff = left.context.diff(dots(left.store))
          dots(right.store).intersect(diff).isEmpty
        }

        firstCondition && secondCondition && thirdCondition
      }

      override def decompose(state: Dotted[DotFun[A]]): Iterable[Dotted[DotFun[A]]] = {
        val added: Iterator[Dotted[DotFun[A]]] = for {
          d <- dots(state.store).iterator
          v <- DecomposeLattice[A].decompose(state.store.repr(d))
        } yield Dotted(DotFun(Map(d -> v)), Dots.single(d))

        val removed =
          state.context.subtract(dots(state.store)).decomposed.map(Dotted(
            DotFun.empty[A],
            _
          ))

        removed ++ added
      }
    }
}
