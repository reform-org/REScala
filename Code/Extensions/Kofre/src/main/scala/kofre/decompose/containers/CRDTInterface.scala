package kofre.decompose.containers

import kofre.base.Defs.Id
import kofre.base.{Bottom, DecomposeLattice, Defs, Lattice}
import kofre.time.Dots
import kofre.syntax.DottedName
import kofre.syntax.{PermCausal, PermCausalMutate, PermIdMutate, PermQuery}
import kofre.dotted.{Dotted, DottedDecompose, DottedLattice}

trait CRDTInterface[State, Wrapper] {

  def state: Dotted[State]

  val replicaID: Defs.Id

  def applyDelta(delta: DottedName[State])(implicit u: DecomposeLattice[Dotted[State]]): Wrapper
}

object CRDTInterface {
  def dottedPermissions[L: DottedDecompose, B <: CRDTInterface[L, B]]: PermIdMutate[B, L] with PermCausalMutate[B, L] =
    new PermIdMutate[B, L] with PermCausalMutate[B, L] {
      override def replicaId(c: B): Id       = c.replicaID
      override def mutate(c: B, delta: L): B = c.applyDelta(DottedName(c.replicaID, Dotted(delta, Dots.empty)))
      override def query(c: B): L            = c.state.store

      // this is interesting
      override def mutateContext(
          container: B,
          withContext: Dotted[L]
      ): B = container.applyDelta(DottedName(container.replicaID, withContext))
      override def context(c: B): Dots = c.state.context
    }

  def fullPermissions[L: DecomposeLattice: Bottom, B <: CRDTInterface[L, B]]: PermIdMutate[B, L] =
    new PermIdMutate[B, L] {
      override def replicaId(c: B): Id       = c.replicaID
      override def mutate(c: B, delta: L): B = c.applyDelta(DottedName(c.replicaID, Dotted(delta)))(using Dotted.latticeLift)
      override def query(c: B): L            = c.state.store
    }

  /** workaround to make existing syntax compile with different context decomposition */
  // given focussedPermission[C, L](using outer: PermQuery[C, Dotted[L]]): PermQuery[C, L] = outer.focus(_.store)
}
