package rescala.extra.encrdt.encrypted.deltabased

import kofre.causality.CausalContext

import scala.collection.mutable

trait DeltaPruning {
  protected var dottedVersionVector: CausalContext
  protected var encryptedDeltaGroupStore: mutable.Set[EncryptedDeltaGroup]

  protected def prune(receivedDeltaGroup: EncryptedDeltaGroup): Unit = {
    encryptedDeltaGroupStore.filterInPlace(subsumedDeltaGroup =>
      !(subsumedDeltaGroup.dottedVersionVector <= receivedDeltaGroup.dottedVersionVector)
    )
    ()
  }
}
