package benchmarks.lattices.delta

import org.openjdk.jmh.annotations._
import kofre.time.{Dots, Dot}
import kofre.base.Id.asId
import java.util.concurrent.TimeUnit
import kofre.base.Id.asId

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Thread)
class DietMapCContextBench {

  @Param(Array("1", "1000"))
  var size: Long = _

  var cca: Dots       = _
  var ccb: Dots       = _
  var cca1: Dots      = _
  var ccaSingle: Dots = _

  private def makeCContext(replicaID: String, mul: Long, off: Long, len: Long): Dots = {
    val ranges = Range.Long(0L, size, 1).map(i => Range.Long(i * mul + off, i * mul + len + off, 1))
    val dots   = ranges.flatten.map(Dot(replicaID.asId, _)).toSet
    Dots.from(dots)
  }

  @Setup
  def setup(): Unit = {
    cca = makeCContext("a", 10, 0, 7)
    ccb = makeCContext("b", 10, 5, 7)
    cca1 = cca.union(Dots.from(Set(Dot("b".asId, 5))))
    ccaSingle = Dots.from(Set(Dot("a".asId, size + 10)))
  }

  @Benchmark
  def merge = (cca union ccb)

  @Benchmark
  def mergeSelf = (cca union cca)

  @Benchmark
  def mergeSelfPlusOne = (cca union cca1)
}
