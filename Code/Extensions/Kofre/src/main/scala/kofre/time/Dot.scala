package kofre.time

import kofre.base.Defs.Id
import kofre.base.Defs

/** A lamport clock.
  * A globally unique counter that is used to track logical time in causal CRDTs. To guarantee global uniqueness,
  * it combines a globally unique replicaID with a locally unique counter.
  */
case class Dot(replicaId: Id, time: Defs.Time) {
  /**
   * Advance the logical time by one step.
   */
  def advance: Dot = Dot(replicaId, time + 1)
}
