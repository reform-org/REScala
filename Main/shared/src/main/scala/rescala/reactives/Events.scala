package rescala.reactives

import rescala.core.Pulse.{Exceptional, NoChange, Value}
import rescala.core._
import rescala.reactives.Events.Estate
import rescala.reactives.Signals.Diff

import scala.language.existentials

/** Functions to construct events, you probably want to use the operators on [[Event]] for a nicer API. */
object Events {
  type Estate[S <: Struct, T]  = S#State[Pulse[T], S, Pulse[T]]


  /** the basic method to create static events */
  def staticNamed[T, S <: Struct](name: String, dependencies: ReSource[S]*)(calculate: StaticTicket[S] => Pulse[T])(implicit ticket: CreationTicket[S]): Event[T, S] = ticket { initTurn =>
    initTurn.create[Pulse[T], StaticEvent[T, S], Pulse[T]](dependencies.toSet, Initializer.Event) {
      state => new StaticEvent[T, S](state, calculate, name) with DisconnectableImpl[S]
    }
  }

  /** Creates static events */
  def static[T, S <: Struct](dependencies: ReSource[S]*)(calculate: StaticTicket[S] => Option[T])(implicit ticket: CreationTicket[S]): Event[T, S] =
    staticNamed(ticket.rename.name, dependencies: _*)(st => Pulse.fromOption(calculate(st)))

  /** Creates dynamic events */
  def dynamic[T, S <: Struct](dependencies: ReSource[S]*)(expr: DynamicTicket[S] => Option[T])(implicit ticket: CreationTicket[S]): Event[T, S] = {
    ticket { initialTurn =>
      initialTurn.create[Pulse[T], DynamicEvent[T, S], Pulse[T]](dependencies.toSet, Initializer.DynamicEvent) {
        state => new DynamicEvent[T, S](state, expr.andThen(Pulse.fromOption), ticket.rename) with DisconnectableImpl[S]
      }
    }
  }

  /** Creates change events */
  def change[A, S <: Struct](signal: Signal[A, S])(implicit ticket: CreationTicket[S]): Event[Diff[A], S] = ticket { initTurn =>
    val internal = initTurn.create[Pulse[Diff[A]], ChangeEvent[A, S], Pulse[Diff[A]]](Set(signal), Initializer.ChangeEvent) {
      state => new ChangeEvent[A, S](state, signal, ticket.rename) with DisconnectableImpl[S]
    }
    Events.static(internal)(st => st.dependStatic(internal))(initTurn)
  }

  /** Folds when any one of a list of events occurs, if multiple events occur, every fold is executed in order. */
  final def fold[A: ReSerializable, S <: Struct](init: A)(accthingy: (=> A) => Seq[(Event[T, S], T => A) forSome {type T}])(implicit ticket: CreationTicket[S]): Signal[A, S] = {
    ticket { initialTurn =>
      var acc = () => init
      val ops = accthingy(acc())
      val dependencies = ops.map(_._1)
      Signals.staticFold[A, S](dependencies.toSet, Pulse.tryCatch(Pulse.Value(init))) { (st, currentValue) =>
        acc = currentValue
        for ((ev, f) <- ops) {
          val value = st.dependStatic(ev)
          value.foreach { v =>
            val res = f(v)
            acc = () => res
          }
        }
        acc()

      }(initialTurn)(ticket.rename)
    }
  }

  final def Match[S <: Struct, A](ops : (Event[T, S], T => A) forSome {type T}*): Seq[((Event[T, S], T => A)) forSome {type T}] = ops

  class EOps[T, S <: Struct](val e: Event[T, S]) {
    /** Constructs a pair similar to ->, however this one is compatible with type inference for [[fold]] */
    final def >> [A](fun: T => A): (Event[T, S], T => A) = (e, fun)
  }
}




private abstract class StaticEvent[T, S <: Struct](_bud: Estate[S, T], expr: StaticTicket[S] => Pulse[T], name: REName)
  extends Base[Pulse[T], S, Pulse[T]](_bud, name) with Event[T, S] {
  override protected[rescala] def reevaluate(rein: ReIn): Rout =
    Result.fromPulse(rein, Pulse.tryCatch(expr(rein), onEmpty = NoChange))
}


private abstract class ChangeEvent[T, S <: Struct](_bud: Estate[S, Diff[T]], signal: Signal[T, S], name: REName)
  extends Base[Pulse[Diff[T]], S, Pulse[Diff[T]]](_bud, name) with Event[Diff[T], S] {

  override protected[rescala] def reevaluate(rein: ReIn): Rout = {
    val to: Pulse[T] = rein.collectStatic(signal)
    if (to == Pulse.empty) return rein
    rein.before match {
      case Value(u) =>
        val from = u.to
        if (from == to) rein
        else {
          Result.fromPulse(rein, Pulse.Value(Diff(from, to)))
          rein.withNotification(Pulse.Value(Diff(from, to)))
        }
      case NoChange =>
        val res = Diff(Pulse.empty, rein.collectStatic(signal))
        rein.withValue(Pulse.Value(res)).withPropagate(false)
      case x@Exceptional(_) =>
        Result.fromPulse(rein, x) //should not happen, change does not actually access other pulses
    }
  }
}

private abstract class DynamicEvent[T, S <: Struct](_bud: Estate[S, T], expr: DynamicTicket[S] => Pulse[T], name: REName)
  extends Base[Pulse[T], S, Pulse[T]](_bud, name) with Event[T, S] {

  override protected[rescala] def reevaluate(rein: ReIn): Rout = {
    rein.trackDependencies()
    Result.fromPulse(rein, Pulse.tryCatch(expr(rein), onEmpty = NoChange))
  }
}
