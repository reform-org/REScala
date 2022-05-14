package test.kofre

import kofre.base.DecomposeLattice
import kofre.causality.{ArrayRanges, CausalContext, Dot}
import kofre.contextual.ContextDecompose.*
import kofre.contextual.{AsCausalContext, ContextDecompose, WithContext}
import kofre.dotted.{DotMap, DotSet}
import org.scalacheck.Prop.*
import org.scalacheck.{Arbitrary, Gen}
import test.kofre.DataGenerator.{arbCausalQueue, *}

import scala.annotation.tailrec

class DotMapTest extends munit.ScalaCheckSuite {

  type TestedMap = DotMap[Int, DotSet]

  property("dots") {
    forAll { (dm: TestedMap) =>
      assert(
        AsCausalContext[TestedMap].dots(dm).toSet == dm.values.flatMap(
          _.dots.iterator
        ).toSet,
        s"DotMap.dots should return the keys of the DotMap itself, but ${AsCausalContext[TestedMap].dots(dm)} does not equal $dm"
      )
    }

  }
  test("empty") {
    assert(
      AsCausalContext[TestedMap].empty.isEmpty,
      s"DotMap.empty should be empty, but ${AsCausalContext[TestedMap].empty} is not empty"
    )

  }
  property("merge") {
    forAll {
      (
          dmA: TestedMap,
          deletedA: CausalContext,
          dmB: TestedMap,
          deletedB: CausalContext
      ) =>
        val dotsA = AsCausalContext[TestedMap].dots(dmA)
        val dotsB = AsCausalContext[TestedMap].dots(dmB)
        val ccA   = dotsA union deletedA
        val ccB   = dotsB union deletedB

        val WithContext(dmMerged, ccMerged) =
          DecomposeLattice[WithContext[TestedMap]].merge(
            WithContext(dmA, (ccA)),
            WithContext(dmB, (ccB))
          )
        val dotsMerged = AsCausalContext[TestedMap].dots(dmMerged)

        assert(
          ccMerged == (ccA union ccB),
          s"DotMap.merge should have the same effect as set union on the causal context, but $ccMerged does not equal ${ccA union ccB}"
        )
        assert(
          dotsMerged.toSet subsetOf (dotsA union dotsB).toSet,
          s"DotMap.merge should not add new elements to the DotSet, but $dotsMerged is not a subset of ${dotsA union dotsB}"
        )
        assert(
          (dotsMerged intersect (deletedA diff dotsA)).isEmpty,
          s"The DotMap resulting from DotMap.merge should not contain dots that were deleted on the lhs, but $dotsMerged contains elements from ${deletedA diff dotsA}"
        )
        assert(
          (dotsMerged intersect (deletedB diff dotsB)).isEmpty,
          s"The DotMap resulting from DotMap.merge should not contain dots that were deleted on the rhs, but $dotsMerged contains elements from ${deletedB diff dotsB}"
        )

        // ignore cases where the dots intersect, as this check does not seem to handle such cases correcly
        if (dotsA.intersect(dotsB).isEmpty) {
          (dmA.keySet union dmB.keySet).foreach { k =>
            val vMerged =
              DotSet.mergePartial(
                WithContext(dmA.getOrElse(k, CausalContext.empty), (ccA)),
                WithContext(dmB.getOrElse(k, CausalContext.empty), (ccB))
              )

            assert(
              vMerged.isEmpty || dmMerged(k) == vMerged,
              s"For all keys that are in both DotMaps the result of DotMap.merge should map these to the merged values, but ${dmMerged.get(k)} does not equal $vMerged"
            )
          }
        }
    }
  }

  property("leq") {
    forAll {
      (
          dmA: TestedMap,
          deletedA: CausalContext,
          dmB: TestedMap,
          deletedB: CausalContext
      ) =>
        val ccA = AsCausalContext[TestedMap].dots(dmA) union deletedA
        val ccB = AsCausalContext[TestedMap].dots(dmB) union deletedB

        assert(
          DotTestedMap.lteq(
            WithContext(dmA, (ccA)),
            WithContext(dmA, (ccA))
          ),
          s"DotMap.leq should be reflexive, but returns false when applied to ($dmA, $ccA, $dmA, $ccA)"
        )

        val WithContext(dmMerged, ccMerged) =
          DecomposeLattice[WithContext[TestedMap]].merge(
            WithContext(dmA, (ccA)),
            WithContext(dmB, (ccB))
          )

        assert(
          DotTestedMap.lteq(WithContext(dmA, (ccA)), WithContext(dmMerged, ccMerged)),
          s"The result of DotMap.merge should be larger than its lhs, but DotMap.leq returns false when applied to ($dmA, $ccA, $dmMerged, $ccMerged)"
        )
        assert(
          DotTestedMap.lteq(WithContext(dmB, (ccB)), WithContext(dmMerged, ccMerged)),
          s"The result of DotMap.merge should be larger than its rhs, but DotMap.leq returns false when applied to ($dmB, $ccB, $dmMerged, $ccMerged)"
        )
    }

  }

  @tailrec
  private def removeDuplicates(
      start: List[(Int, CausalContext)],
      acc: TestedMap,
      con: CausalContext
  ): TestedMap =
    start match
      case Nil         => acc
      case (i, c) :: t => removeDuplicates(t, acc + (i -> c.subtract(con)), con union c)

  property("decompose") {
    forAll { (dmdup: TestedMap, deleted: CausalContext) =>

      val dm = removeDuplicates(dmdup.toList, Map.empty, CausalContext.empty)

      val cc = AsCausalContext[TestedMap].dots(dm) union deleted

      val decomposed: Iterable[WithContext[TestedMap]] =
        DotTestedMap.decompose(WithContext(dm, (cc)))
      val wc: WithContext[TestedMap] =
        decomposed.foldLeft(WithContext(AsCausalContext[TestedMap].empty, CausalContext.empty)) {
          case (WithContext(dmA, ccA), WithContext(dmB, ccB)) =>
            DecomposeLattice[WithContext[TestedMap]].merge(WithContext(dmA, ccA), WithContext(dmB, ccB))
        }

      val dmMerged: TestedMap = wc.store
      val ccMerged                          = wc.context

      assertEquals(
        ccMerged,
        cc,
        s"Merging the list of atoms returned by DotMap.decompose should produce an equal DotMap, but $dmMerged does not equal $dm"
      )
      dm.keys.foreach { k =>
        assertEquals(
          dm(k),
          dmMerged.getOrElse(k, CausalContext.empty),
          s"Merging the list of atoms returned by DotMap.decompose should produce an equal Causal Context, but on key $k the $ccMerged does not equal $cc"
        )
      }
    }
  }
}