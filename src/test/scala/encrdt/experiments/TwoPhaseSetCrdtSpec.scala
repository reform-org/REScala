package de.ckuessner
package encrdt.experiments

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString, writeToString}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class TwoPhaseSetCrdtSpec extends AnyFlatSpec {

  "A TwoPhaseSetCrdt" should "merge with empty crdt" in {
    val left = new TwoPhaseSetCrdt[String](1)
    val right = new TwoPhaseSetCrdt[String](2)

    assertResult(TwoPhaseSetState()) {
      left.merge(right.state)
      left.state
    }

  }

  it should "handle add correctly" in {
    val left = new TwoPhaseSetCrdt[Int](1)

    assertResult(Set(1)) {
      left.add(1)
      left.values
    }

    for {i <- 1 to 100} {
      assertResult((1 to i).toSet) {
        left.add(i)
        left.values
      }
    }
  }

  it should "handle simple remove correctly" in {
    val left = new TwoPhaseSetCrdt[Int](1)

    left.add(1)

    assertResult(Set()) {
      left.remove(1)
      left.values
    }

    assertResult(Set(2)) {
      left.add(2)
      left.values
    }

    assertResult(Set()) {
      left.remove(2)
      left.values
    }

  }

  it should "not add element after removal of same element" in {
    val left = new TwoPhaseSetCrdt[Int](1)
    left.add(1)
    left.remove(1)
    assertResult(Set()) {
      left.add(1)
      left.values
    }
  }

  it should "serialize and deserialize" in {
    val crdtState = TwoPhaseSetState[Int]()
    implicit val intCode: JsonValueCodec[Int] = JsonCodecMaker.make

    val serialized = writeToString(crdtState)
    val deserialized = readFromString[TwoPhaseSetState[Int]](serialized)

    deserialized shouldBe crdtState
  }

}
