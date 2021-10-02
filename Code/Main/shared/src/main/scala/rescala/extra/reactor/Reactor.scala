package rescala.extra.reactor

import rescala.core.ReName
import rescala.interface.RescalaInterface
import rescala.macros.MacroAccess

class ReactorBundle[Api <: RescalaInterface](val api: Api) {
  import api._
  class Reactor[T](
      initState: State[ReactorState[T]],
      looping: Boolean = false,
  ) extends Derived with Interp[T] with MacroAccess[T, Interp[T]] {

    override type Value = ReactorState[T]

    override protected[rescala] def state: State[ReactorState[T]] = initState
    override protected[rescala] def name: ReName = "Custom Reactor"
    override def interpret(v: ReactorState[T]): T = v.currentValue
    override protected[rescala] def commit(base: Value): Value = base

    /** called if any of the dependencies changed in the current update turn,
      * after all (known) dependencies are updated
      */
    override protected[rescala] def reevaluate(input: ReIn): Rout = {

      input.trackDependencies(Set())

      var progressedNextAction = false

      def processActions[A](currentState: ReactorState[T]): ReactorState[T] = {
        def setAction(value: T, remainingActions: List[ReactorAction[T]]): ReactorState[T] = {
          processActions(currentState.copy(currentValue = value, currentStage = Stage(remainingActions)))
        }

        def nextAction[E](event: Event[E], handler: E => Stage[T]): ReactorState[T] = {
          if (progressedNextAction) {
            return currentState
          }

          val eventValue = input.depend(event)
          eventValue match {
            case None => currentState
            case Some(value) =>
              progressedNextAction = true
              val stages = handler(value)
              processActions(currentState.copy(currentStage = stages))
          }
        }

        def loopAction(loopStage: Stage[T], initialStage: Stage[T]): ReactorState[T] = {
          val resultState = processActions(currentState.copy(currentStage = loopStage))
          if (resultState.currentStage.actions.isEmpty) {
            return processActions(resultState.copy(currentStage = initialStage))
          }

          resultState.copy(currentStage = Stage(List(ReactorAction.LoopAction(resultState.currentStage, initialStage))))
        }

        def untilAction[E](event: Event[E], body: Stage[T], interrupt: E => Stage[T]): ReactorState[T] = {
          val eventValue = input.depend(event)
          eventValue match {
            case None =>
              val resultStage = processActions(currentState.copy(currentStage = body))
              resultStage.copy(currentStage =
                Stage(List(ReactorAction.UntilAction(event, resultStage.currentStage, interrupt)))
              )
            case Some(value) =>
              val stages = interrupt(value)
              processActions(currentState.copy(currentStage = stages))
          }
        }

        def modifyAction(modifier: T => T, currentValue: T, tail: List[ReactorAction[T]]): ReactorState[T] = {
          val modifiedValue = modifier(currentValue)
          setAction(modifiedValue, tail)
        }

        def readAction(builder: T => Stage[T], currentValue: T): ReactorState[T] = {
          val nextStage = builder(currentValue)
          processActions(currentState.copy(currentStage = nextStage))
        }

        currentState.currentStage.actions match {
          case Nil => currentState
          case ReactorAction.SetAction(v) :: tail =>
            setAction(v, tail)
          case ReactorAction.ModifyAction(modifier) :: tail =>
            modifyAction(modifier, currentState.currentValue, tail)
          case ReactorAction.NextAction(event, handler) :: _ =>
            nextAction(event, handler)
          case ReactorAction.ReadAction(builder) :: _ =>
            readAction(builder, currentState.currentValue)
          case ReactorAction.LoopAction(loopStage, initialStage) :: _ =>
            loopAction(loopStage, initialStage)
          case ReactorAction.UntilAction(event, body, interrupt) :: _ =>
            untilAction(event, body, interrupt)
        }
      }

      var resState = processActions(input.before)
      if (looping && resState.currentStage.actions.isEmpty) {
        resState = resState.copy(currentStage = resState.initialStage)
        resState = processActions(resState)
      }

      input.withValue(resState)
    }

    override def resource: Interp[T] = this

    def now(implicit scheduler: Scheduler): T = scheduler.singleReadValueOnce(this)
  }

  object Reactor {

    /** Creates a new Reactor, which steps through the reactor stages ones.
      *
      * @param initialValue The initial value of the Reactor.
      * @param initialStage The Stage defining the Reactors behaviour.
      * @tparam T The type of the Reactor value.
      * @return The created Reactor.
      */
    def once[T](
        initialValue: T,
    )(initialStage: Stage[T]): Reactor[T] = {
      CreationTicket.fromScheduler(scheduler)
        .create(
          Set(),
          new ReactorState[T](initialValue, initialStage),
          inite = true
        ) { createdState: State[ReactorState[T]] =>
          new Reactor[T](createdState)
        }
    }

    /** Creates a new Reactor, which starts from the beginning, once it's finished.
      *
      * @param initialValue The initial value of the Reactor.
      * @param initialStage The Stage defining the Reactors behaviour.
      * @tparam T The type of the Reactor value.
      * @return The created Reactor.
      */
    def loop[T](
        initialValue: T,
    )(initialStage: Stage[T]): Reactor[T] = {
      CreationTicket.fromScheduler(scheduler)
        .create(
          Set(),
          new ReactorState[T](initialValue, initialStage),
          inite = true
        ) { createdState: State[ReactorState[T]] =>
          new Reactor[T](createdState, true)
        }
    }
  }

  sealed trait ReactorAction[T]

  object ReactorAction {
    case class SetAction[T](res: T) extends ReactorAction[T]

    case class ModifyAction[T](modifier: T => T) extends ReactorAction[T]

    case class NextAction[T, E](event: Event[E], handler: E => Stage[T])
        extends ReactorAction[T]

    case class ReadAction[T](stageBuilder: T => Stage[T]) extends ReactorAction[T]

    case class LoopAction[T](currentStage: Stage[T], initialStage: Stage[T]) extends ReactorAction[T]

    case class UntilAction[T, E](event: Event[E], body: Stage[T], interrupt: E => Stage[T])
        extends ReactorAction[T]
  }

  case class ReactorState[T](currentValue: T, currentStage: Stage[T], initialStage: Stage[T]) {
    def this(currentValue: T, initialStage: Stage[T]) = this(currentValue, initialStage, initialStage)
  }

  case class Stage[T](actions: List[ReactorAction[T]] = Nil) {

    private def addAction(newValue: ReactorAction[T]): Stage[T] = {
      copy(actions = actions :+ newValue)
    }

    /** Sets the value of the Reactor.
      *
      * @param newValue The new value of the Reactor.
      * @return A StageBuilder describing the Reactor behaviour.
      */
    def set(newValue: T): Stage[T] = {
      addAction(ReactorAction.SetAction(newValue))
    }

    /** Modifies the value of the Reactor.
      *
      * @param modifier A function that has the old Reactor value as input and returns a new Reactor value.
      * @return A StageBuilder describing the Reactor behaviour.
      */
    def modify(modifier: T => T): Stage[T] = {
      addAction(ReactorAction.ModifyAction(modifier))
    }

    /** Waits until the event is triggered.
      *
      * When the event is triggered the given body is executed in the
      * same transaction.
      *
      * @param event the event to wait for.
      * @param body  the code to execute when the event is triggered.
      * @tparam E the event's type.
      */
    def next[E](event: Event[E])(body: E => Stage[T]): Stage[T] = {
      addAction(ReactorAction.NextAction(event, body))
    }

    /** Waits until the event is triggered.
      *
      * When the event is triggered the given body is executed in the
      * same transaction.
      *
      * @param event the event to wait for.
      * @param body  the code to execute when the event is triggered.
      */
    def next(event: Event[Unit])(body: => Stage[T]): Stage[T] = {
      addAction(ReactorAction.NextAction(event, (_: Unit) => body))
    }

    /** Reads the current reactor value.
      *
      * Executes the body with the current reactor value
      * and expects another [[Stage]] as result.
      *
      * A usage example could be returning different [[Stage]]s
      * depending on the event value.
      *
      * @param body The function building the resulting [[Stage]]
      */
    def read(body: T => Stage[T]): Stage[T] = {
      addAction(ReactorAction.ReadAction(body))
    }

    /** Executes the body in a loop.
      *
      * @param body The [[Stage]] to be executes repeatedly
      */
    def loop(body: => Stage[T]): Stage[T] = {
      addAction(ReactorAction.LoopAction(body, body))
    }

    /** Executes it's body until an event is fired.
      *
      * Until executes the body until the given event is fired.
      * When the event is fired, until executes the interruptHandler.
      *
      * @param event            The event indicating the interrupt.
      * @param body             The [[Stage]] to be executes by default.
      * @param interruptHandler A function taking the interrupt event's value
      *                         and returning a [[Stage]].
      *                         It is executed when the interrupt is fired.
      * @tparam E The type of the event value.
      */
    def until[E](event: Event[E], body: => Stage[T], interruptHandler: E => Stage[T]): Stage[T] = {
      addAction(ReactorAction.UntilAction(event, body, interruptHandler))
    }

    /** Executes it's body until an event is fired.
      *
      * Until executes the body until the given event is fired.
      * When the event is fired, until executes the interruptHandler.
      *
      * @param event            The event indicating the interrupt.
      * @param body             The [[Stage]] to be executes by default.
      * @param interruptHandler A function taking the interrupt event's value
      *                         and returning a [[Stage]].
      *                         It is executed when the interrupt is fired.
      */
    def until(event: Event[Unit], body: => Stage[T], interruptHandler: Stage[T]): Stage[T] = {
      val handler = { (_: Unit) => interruptHandler }
      addAction(ReactorAction.UntilAction(event, body, handler))
    }

    /** Executes it's body until an event is fired.
      *
      * Until executes the body until the given event is fired.
      *
      * @param event The event indicating the interrupt.
      * @param body  The [[Stage]] to be executes by default.
      * @tparam E The type of the event value.
      */
    def until[E](event: Event[E], body: => Stage[T]): Stage[T] = {
      val interrupHandler = { (_: E) => Stage[T]() }
      addAction(ReactorAction.UntilAction(event, body, interrupHandler))
    }
  }
}
