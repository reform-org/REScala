package kofre.dotted

import kofre.base.{Bottom, DecomposeLattice, Lattice}
import kofre.causality.{CausalContext, Dot}
import kofre.contextual.ContextDecompose.FromConlattice
import kofre.contextual.{AsCausalContext, ContextDecompose, ContextLattice, WithContext}
import kofre.decompose.interfaces

import scala.annotation.targetName

/** The context describes dots that have been seen.
  * The store describes which value is associated for a given dot.
  * Dots that are removed from the store are considered deleted.
  * All others are merged as for normal maps.
  *
  * The delta CRDT paper calls this a DotFun
  */
case class DotFun[A](repr: Map[Dot, A]) {
  def dots: CausalContext = CausalContext.fromSet(repr.keySet)
  @targetName("add")
  def +(tup: (Dot, A)): DotFun[A] = DotFun(repr + tup)
  export repr.{+ as _, repr as _, *}
}

object DotFun {

  def empty[A]: DotFun[A] = DotFun(Map.empty)

  given bottom[A]: Bottom[DotFun[A]] with
    override def empty: DotFun[A] = DotFun.empty

  given perDotLattice[A: Lattice]: ContextLattice[DotFun[A]] = (left, right) => {
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

  given dotStore[V]: AsCausalContext[DotFun[V]] with {
    override def dots(dotStore: DotFun[V]): CausalContext = CausalContext.fromSet(dotStore.repr.keySet)
  }

  /** DotFun is a dot store implementation that maps dots to values of a Lattice type. See [[interfaces.MVRegisterInterface]]
    * for a usage example.
    */
  given perDotDecompose[A: DecomposeLattice]: ContextDecompose[DotFun[A]] =
    new FromConlattice[DotFun[A]](perDotLattice[A]) {
      private def dots(a: DotFun[A]): CausalContext = dotStore.dots(a)

      override def empty: WithContext[DotFun[A]] = WithContext(DotFun.empty)

      override def lteq(left: WithContext[DotFun[A]], right: WithContext[DotFun[A]]): Boolean = {
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

      override def decompose(state: WithContext[DotFun[A]]): Iterable[WithContext[DotFun[A]]] = {
        val added: Iterator[WithContext[DotFun[A]]] = for {
          d <- dots(state.store).iterator
          v <- DecomposeLattice[A].decompose(state.store.repr(d))
        } yield WithContext(DotFun(Map(d -> v)), CausalContext.single(d))

        val removed =
          state.context.subtract(dots(state.store)).decomposed.map(WithContext(
            DotFun.empty[A],
            _
          ))

        removed ++ added
      }
    }
}