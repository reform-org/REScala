package rescala.stm

import rescala.core._
import rescala.twoversion.TwoVersionSchedulerImpl

import scala.concurrent.stm.atomic

object STMScheduler {
  implicit val stm: Scheduler[STMTurn] = new TwoVersionSchedulerImpl[STMTurn, STMTurn]("STM", _ => new STMTurn()) {
    override def executeTurn[R](initialWrites: Set[ReSource[STMTurn]], admissionPhase: AdmissionTicket[STMTurn] => R): R =
      atomic { tx => super.executeTurn(initialWrites, admissionPhase) }
  }
}