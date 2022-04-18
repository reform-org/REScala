package rescala.extra.encrdt.encrypted.deltabased

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, writeToArray}
import com.google.crypto.tink.Aead
import kofre.causality.CausalContext
import kofre.Lattice

case class DecryptedDeltaGroup[T](deltaGroup: T, dottedVersionVector: CausalContext) {
  def encrypt(aead: Aead)(implicit
      tJsonCodec: JsonValueCodec[T],
      dotSetJsonCodec: JsonValueCodec[CausalContext]
  ): EncryptedDeltaGroup = {
    val serialDeltaGroup          = writeToArray(deltaGroup)
    val serialDottedVersionVector = writeToArray(dottedVersionVector)
    val deltaGroupCipherText      = aead.encrypt(serialDeltaGroup, serialDottedVersionVector)

    EncryptedDeltaGroup(deltaGroupCipherText, serialDottedVersionVector)
  }
}

object DecryptedDeltaGroup {
  implicit def decryptedDeltaGroupSemiLattice[T](implicit
      tLattice: Lattice[T]
  ): Lattice[DecryptedDeltaGroup[T]] = (l, r) =>
    DecryptedDeltaGroup(
      Lattice[T].merge(l.deltaGroup, r.deltaGroup),
      l.dottedVersionVector.union(r.dottedVersionVector)
    )
}
