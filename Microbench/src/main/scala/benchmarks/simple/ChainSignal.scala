package benchmarks.simple

import java.util.concurrent.TimeUnit

import benchmarks.{Workload, Size, EngineParam, Step}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.BenchmarkParams
import rescala.propagation.Turn
import rescala.engines.Engine
import rescala.reactives.Signal
import rescala.reactives.Var

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
class ChainSignal[S <: rescala.graph.Spores] {

  implicit var engine: Engine[S, Turn[S]] = _

  var source: Var[Int, S] = _
  var result: Signal[Int, S] = _

  @Setup
  def setup(params: BenchmarkParams, size: Size, step: Step, engineParam: EngineParam[S], work: Workload) = {
    engine = engineParam.engine
    source = Var(step.run())
    result = source
    for (_ <- Range(0, size.size)) {
      result = result.map{v => val r = v + 1; work.consume(); r}
    }
  }

  @Benchmark
  def run(step: Step): Unit = source.set(step.run())
}
