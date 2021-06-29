package de.ckuessner
package encrdt.experiments

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

class TwoPhaseSetCrdt[T](val replicaId: Int) extends SetCrdt[T] {

  private var _state = TwoPhaseSetState[T]()

  def state: TwoPhaseSetState[T] = _state

  def merge(remoteState: TwoPhaseSetState[T]): Unit = {
    _state = SemiLattice[TwoPhaseSetState[T]].merged(state, remoteState)
  }

  def add(element: T): Unit = {
    _state = _state.added(element)
  }

  // When removing an element that is not currently present in the Set, the element can't be added later on.
  def remove(element: T): Unit = {
    _state = _state.removed(element)
  }

  def values: Set[T] = state.values
}

case class TwoPhaseSetState[T](added: Set[T] = Set[T](), removed: Set[T] = Set[T]()) {
  def values: Set[T] = added -- removed

  def added(element: T): TwoPhaseSetState[T] = copy(added = added + element)

  def removed(element: T): TwoPhaseSetState[T] = {
    copy(removed = removed + element)
  }
}

object TwoPhaseSetState {
  implicit def TwoPhaseSetSemiLattice[T]: SemiLattice[TwoPhaseSetState[T]] =
    (left: TwoPhaseSetState[T], right: TwoPhaseSetState[T]) =>
      TwoPhaseSetState(left.added ++ right.added, left.removed ++ right.removed)

  implicit def codec[T](implicit jsonValueCodec: JsonValueCodec[T]): JsonValueCodec[TwoPhaseSetState[T]] =
    JsonCodecMaker.make[TwoPhaseSetState[T]]
}
