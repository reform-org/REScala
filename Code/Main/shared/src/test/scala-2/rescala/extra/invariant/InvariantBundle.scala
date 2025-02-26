package rescala.extra.invariant

import org.scalacheck.Prop.forAll
import org.scalacheck.Test.PropException
import org.scalacheck.{Gen, Prop, Test}
import rescala.interface.RescalaInterface
import rescala.operator.Pulse
import rescala.scheduler.TopoBundle

import scala.collection.mutable.ListBuffer

object InvariantApi extends InvariantBundle with RescalaInterface {
  def scheduler: InvariantScheduler.type = InvariantScheduler

  override def makeDerivedStructStateBundle[V](ip: V): InvariantApi.InvariantState[V] = new InvariantState(ip)
}

trait InvariantBundle extends TopoBundle {
  selfType: RescalaInterface =>

  override type State[V] = InvariantState[V]

  sealed trait InvariantException extends RuntimeException

  case class InvariantViolationException(
      t: Throwable,
      reactive: ReSource,
      causalErrorChains: Seq[Seq[ReSource]]
  ) extends InvariantException {

    override val getMessage: String = {
      val chainErrorMessage =
        if (causalErrorChains.nonEmpty)
          "The error was caused by these update chains:\n\n" ++ causalErrorChains.map(_.map(r =>
            s"${r.name.str} with value: ${r.state.value}"
          ).mkString("\n↓\n")).mkString("\n---\n")
        else "The error was not triggered by a change."

      s"${t.getMessage} in reactive ${reactive.name.str}\n$chainErrorMessage\n"
    }

    override def fillInStackTrace(): InvariantViolationException = this
  }

  case class NoGeneratorException(message: String) extends InvariantException {
    override val getMessage: String = message
  }

  class InvariantState[V](value: V) extends TopoState[V](value) {
    var invariants: Seq[Invariant[V]] = Seq.empty
    var gen: Gen[_]                   = _
  }

  class InvariantInitializer(afterCommitObservers: ListBuffer[Observation])
      extends TopoInitializer(afterCommitObservers) {
    override protected[this] def makeDerivedStructState[V](ip: V): InvariantState[V] = new InvariantState[V](ip)
  }

  object InvariantScheduler extends TopoSchedulerInterface {

    override def schedulerName: String = "SimpleWithInvariantSupport"

    override def beforeCleanupHook(all: Seq[ReSource], initialWrites: Set[ReSource]): Unit =
      InvariantUtil.evaluateInvariants(all, initialWrites)

    implicit class SignalWithInvariants[T](val signal: Signal[T]) {

      def specify(inv: Invariant[T]*): Unit = {
        signal.state.invariants =
          inv.map(inv => new Invariant[signal.Value](inv.description, (invp: Pulse[T]) => inv.inv(invp.get: T)))
      }

      def setValueGenerator(gen: Gen[T]): Unit = {
        this.signal.state.gen = gen
      }

      def test(): Unit = {
        val result = Test.check(
          Test.Parameters.default,
          customForAll(
            findGenerators(),
            changes => {
              forceValues(changes.map(pair => (pair._1, Pulse.Value(pair._2))): _*)
              true
            }
          )
        )
        if (!result.passed) {
          result.status match {
            case PropException(_, e, _) => throw e
            case _                      => throw new RuntimeException("Test failed!")
          }
        }
      }

      private def customForAll[P](
          signalGeneratorPairs: List[(ReSource, Gen[A] forSome { type A })],
          f: List[(ReSource, Any)] => Boolean,
          generated: List[(ReSource, Any)] = List.empty
      ): Prop =
        signalGeneratorPairs match {
          case Nil => Prop(f(generated))
          case (sig, gen) :: tail =>
            forAll(gen)(t => customForAll(tail, f, generated :+ ((sig, t))))
        }

      private def findGenerators(): List[(ReSource, Gen[A] forSome { type A })] = {
        def findGeneratorsRecursive(resource: ReSource): List[(ReSource, Gen[A] forSome { type A })] = {
          if (resource.state.gen != null) {
            List((resource, resource.state.gen))
          } else if (resource.state.incoming == Set.empty) {
            List()
          } else {
            resource.state.incoming
              .flatMap { incoming => findGeneratorsRecursive(incoming) }
              .toList
          }
        }

        val gens = findGeneratorsRecursive(this.signal)
        if (gens.isEmpty) {
          throw NoGeneratorException(s"No generators found in incoming nodes for signal ${this.signal.name}")
        }
        gens
      }

      private def forceValues(changes: (ReSource, A) forSome { type A }*): Set[ReSource] = {
        val asReSource = changes.foldLeft(Set.empty[ReSource]) {
          case (acc, (source, _)) => acc + source
        }

        forceNewTransaction(
          asReSource,
          {
            admissionTicket =>
              changes.foreach {
                change =>
                  val initialChange: InitialChange = new InitialChange {
                    override val source: ReSource = change._1

                    override def writeValue(b: source.Value, v: source.Value => Unit): Boolean = {
                      val casted = change._2.asInstanceOf[source.Value]
                      if (casted != b) {
                        v(casted)
                        return true
                      }
                      false
                    }
                  }
                  admissionTicket.recordChange(initialChange)
              }
          }
        )

        asReSource
      }
    }

  }

  object InvariantUtil {

    def evaluateInvariants(reactives: Seq[ReSource], initialWrites: Set[ReSource]): Unit = {
      for {
        reactive <- reactives
        inv      <- reactive.state.invariants
        if !inv.validate(reactive.state.value)
      } {
        throw new InvariantViolationException(
          new IllegalArgumentException(s"${reactive.state.value} violates invariant ${inv.description}"),
          reactive,
          InvariantUtil.getCausalErrorChains(reactive, initialWrites)
        )
      }
    }

    def getCausalErrorChains(
        errorNode: ReSource,
        initialWrites: Set[ReSource]
    ): Seq[Seq[ReSource]] = {
      import scala.collection.mutable.ListBuffer

      val initialNames = initialWrites.map(_.name)

      def traverse(
          node: ReSource,
          path: Seq[ReSource]
      ): Seq[Seq[ReSource]] = {
        val paths = new ListBuffer[Seq[ReSource]]()
        for (incoming <- node.state.incoming) {
          val incName = incoming.name
          if (initialNames.contains(incName)) {
            paths += path :+ incoming
          } else {
            paths ++= traverse(incoming, path :+ incoming)
          }
        }
        paths.toList
      }

      traverse(errorNode, Seq(errorNode))
    }
  }

}
