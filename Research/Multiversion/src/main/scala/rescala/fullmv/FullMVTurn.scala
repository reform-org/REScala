package rescala.fullmv

import rescala.engine.{EngineImpl, InitializationImpl, ValuePersistency}
import rescala.graph.{Pulsing, Reactive, Struct}

trait FullMVStruct extends Struct {
  override type State[P, S <: Struct] = NodeVersionHistory[P]
}

class FullMVEngine extends EngineImpl[FullMVStruct, FullMVTurn] {
  def sgt: SerializationGraphTracking = ???
  override protected def makeTurn(initialWrites: Traversable[Reactive], priorTurn: Option[FullMVTurn]): FullMVTurn = new FullMVTurn(sgt)
  override protected def executeInternal[R](turn: FullMVTurn, initialWrites: Traversable[Reactive], admissionPhase: (FullMVTurn) => R): R = ???
}

class FullMVTurn(val sgt: SerializationGraphTracking) extends InitializationImpl[FullMVStruct] {
  def incrementFrame(node: Reactive[FullMVStruct]): Unit = {
    // TODO
    //val branching = node.state.incrementFrame(this)
  }
  def incrementSupersedeFrame(node: Reactive[FullMVStruct], superseded: FullMVTurn): Unit = {
    // TODO
    //val branching = node.state.incrementSupersedeFrame(this, superseded)
  }

  def notify(node: Reactive[FullMVStruct], changed: Boolean, maybeFollowFrame: Option[FullMVTurn]): Unit = {
    // TODO
    //val notificationResultAction = node.state.notify(this, changed, maybeFollowFrame)
  }

  override protected def makeStructState[P](valuePersistency: ValuePersistency[P]): NodeVersionHistory[P] = new NodeVersionHistory(sgt, this, valuePersistency.initialValue)
  override protected def ignite(reactive: Reactive[FullMVStruct], incoming: Set[Reactive[FullMVStruct]], valuePersistency: ValuePersistency[_]): Unit = ???

  override private[rescala] def dynamicDependencyInteraction(reactive: Reactive[FullMVStruct]) = reactive.state.ensureReadVersion(this)
  override private[rescala] def before[P](pulsing: Pulsing[P, FullMVStruct]) = pulsing.state.before(this)
  override private[rescala] def after[P](pulsing: Pulsing[P, FullMVStruct]) = pulsing.state.after(this)



  override def observe(f: () => Unit): Unit = f()
}