package de.ckuessner
package encrdt.benchmarks

import encrdt.benchmarks.Codecs.awlwwmapJsonCodec
import encrdt.causality.VectorClock
import encrdt.crdts.AddWinsLastWriterWinsMap
import encrdt.crdts.AddWinsLastWriterWinsMap.LatticeType
import encrdt.encrypted.statebased
import encrdt.encrypted.statebased.{DecryptedState, EncryptedState}

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray

import java.io.PrintWriter
import java.nio.file.{Files, Paths}

object StateBasedUntrustedReplicaSizeBenchmark extends App {
  val csvFile = new PrintWriter(Files.newOutputStream(Paths.get("./benchmarks/out/state_size_benchmark.csv")))
  csvFile.println("concurrentUpdates,commonElements,uniqueElements,untrustedReplicaSize,mergedSize")

  val aead = Helper.setupAead("AES128_GCM")

  for (parallelStates <- 1 to 4) {
    for (commonElements <- (1 to 4).map(i => math.pow(10, i).toInt - parallelStates)) {
      val dummyKeyValuePairs = Helper.dummyKeyValuePairs(commonElements + parallelStates)

      val crdt = new AddWinsLastWriterWinsMap[String, String]("0")
      var versionVector: VectorClock = VectorClock()

      for (i <- 0 until commonElements) {
        val entry = dummyKeyValuePairs(i)
        crdt.put(entry._1, entry._2)
        versionVector = versionVector.advance("0")
      }

      val commonState = crdt.state
      val commonStateDec = DecryptedState(commonState, versionVector)
      val commonStateEnc = commonStateDec.encrypt(Helper.setupAead("AES128_GCM"))
      val untrustedReplica = new statebased.UntrustedReplica(Set(commonStateEnc)) {
        override protected def disseminate(encryptedState: EncryptedState): Unit = {}

        def size: Int = {
          stateStore.map { encState =>
            encState.serialVersionVector.length + encState.stateCiphertext.length
          }.sum
        }
      }

      var decryptedStatesMerged: DecryptedState[LatticeType[String, String]] = commonStateDec

      for (replicaId <- 1 to parallelStates) {
        val entry = dummyKeyValuePairs(commonElements + replicaId - 1)
        val replicaSpecificCrdt = new AddWinsLastWriterWinsMap[String, String](replicaId.toString, commonState)
        replicaSpecificCrdt.put(entry._1, entry._2)
        val replicaSpecificVersionVector = versionVector.advance(replicaId.toString)
        val replicaSpecificDecState: DecryptedState[LatticeType[String, String]] = DecryptedState(replicaSpecificCrdt.state, replicaSpecificVersionVector)
        val replicaSpecificEncState = replicaSpecificDecState.encrypt(aead)
        untrustedReplica.receive(replicaSpecificEncState)
        decryptedStatesMerged = DecryptedState.lattice[LatticeType[String, String]].merged(decryptedStatesMerged, replicaSpecificDecState)
      }

      val mergedSize = writeToArray(decryptedStatesMerged.state).length
      csvFile.println(s"$parallelStates,$commonElements,${parallelStates + commonElements},${untrustedReplica.size},$mergedSize")
    }

  }

  csvFile.close()
}
