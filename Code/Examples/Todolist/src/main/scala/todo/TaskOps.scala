package todo

import kofre.datatypes.ReplicatedList
import kofre.dotted.Dotted
import kofre.syntax.{DeltaBufferDotted, Named}
import rescala.default.*
import rescala.default.Fold

import java.util.concurrent.ThreadLocalRandom
import scala.annotation.nowarn

class TaskOps(@nowarn taskRefs: TaskReferences) {

  type State = DeltaBufferDotted[ReplicatedList[TaskRef]]

  def handleCreateTodo(createTodo: Event[String]): Fold.Branch[State] = createTodo.act { desc =>
    val taskid = s"Task(${ThreadLocalRandom.current().nextLong().toHexString})"
    TaskReferences.lookupOrCreateTaskRef(taskid, Some(TaskData(desc)))
    val taskref = TaskRef(taskid)
    current.clearDeltas().prepend(taskref)
  }

  def handleRemoveAll(removeAll: Event[Any]): Fold.Branch[State] = removeAll.act { _ =>
    current.clearDeltas().deleteBy { taskref =>
      val isDone = taskref.task.value.read.exists(_.done)
      // todo, move to observer, disconnect during transaction does not respect rollbacks
      if (isDone) taskref.task.disconnect()
      isDone
    }
  }

  def handleRemove(state: State)(id: String): State = {
    state.clearDeltas().deleteBy { taskref =>
      val delete = taskref.id == id
      // todo, move to observer, disconnect during transaction does not respect rollbacks
      if (delete) taskref.task.disconnect()
      delete
    }
  }

  def handleDelta(deltaEvent: Event[Named[Dotted[ReplicatedList[TaskRef]]]]): Fold.Branch[State] =
    deltaEvent.act { delta =>
      val deltaBuffered = current

      val newList = deltaBuffered.clearDeltas().applyDelta(delta.replicaId, delta.anon)

      val oldIDs = deltaBuffered.toList.toSet
      val newIDs = newList.toList.toSet

      val removed = oldIDs -- newIDs
      removed.foreach { _.task.disconnect() }

      newList
    }

}
