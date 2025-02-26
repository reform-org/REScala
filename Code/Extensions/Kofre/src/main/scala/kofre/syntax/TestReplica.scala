package kofre.syntax

import kofre.base.{Bottom, DecomposeLattice, Id, Lattice}
import kofre.base.Id

import scala.annotation.targetName

class TestReplica[A](val replicaId: Id, var anon: A) {
  def apply(delta: A)(using Lattice[A]): TestReplica[A] =
    anon = anon merge delta
    this
}

object TestReplica {

  @targetName("fromString")
  def apply[L](replicaId: String, anon: L): TestReplica[L] = apply(Id.predefined(replicaId), anon)
  def apply[L](replicaID: Id, anon: L): TestReplica[L]     = new TestReplica(replicaID, anon)
  def unapply[L](wnc: TestReplica[L]): Some[(Id, L)]       = Some((wnc.replicaId, wnc.anon))

  given permissions[L](using DecomposeLattice[L]): PermIdMutate[TestReplica[L], L]
    with {
    override def replicaId(c: TestReplica[L]): Id                    = c.replicaId
    override def mutate(c: TestReplica[L], delta: L): TestReplica[L] = c.apply(delta)
    override def query(c: TestReplica[L]): L                         = c.anon
  }
}
