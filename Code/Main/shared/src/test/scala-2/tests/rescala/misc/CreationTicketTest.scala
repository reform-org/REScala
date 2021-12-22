package tests.rescala.misc

import tests.rescala.testtools.RETests

class CreationTicketTest extends RETests {
  multiEngined { engine =>
    import engine._

    if (engine != rescala.Schedulers.simple) {
      /* this test uses some shady planned()(identity) to get the turn object out of the transaction
       * you should not do this. */
      def getTurn(implicit engine: Scheduler): Transaction =
        engine.forceNewTransaction()(_.tx)

      test("none Dynamic No Implicit") {
        assert(implicitly[CreationTicket].scope.self === Right(engine.scheduler))
      }

      test("some Dynamic No Implicit") {
        engine.transaction() { (dynamicTurn: AdmissionTicket) =>
          assert(implicitly[CreationTicket].scope.self === Right(engine.scheduler))
          assert(implicitly[CreationTicket].scope.dynamicTransaction(identity) === dynamicTurn.tx)
        }
      }

      test("none Dynamic Some Implicit") {
        implicit val implicitTurn: Transaction = getTurn
        assert(implicitly[CreationTicket].scope.self === Left(implicitTurn))
        assert(implicitly[CreationTicket].scope.dynamicTransaction(identity) === implicitTurn)
      }

      test("some Dynamic Some Implicit") {
        engine.transaction() { (dynamicTurn: AdmissionTicket) =>
          implicit val implicitTurn: Transaction = getTurn
          assert(implicitly[CreationTicket].scope.self === Left(implicitTurn))
          assert(implicitly[CreationTicket].scope.dynamicTransaction(identity) === implicitTurn)
        }
      }

      test("implicit In Closures") {
        val closureDefinition: Transaction = getTurn(engine.scheduler)
        val closure = {
          implicit def it: Transaction = closureDefinition
          () => implicitly[CreationTicket]
        }
        engine.transaction() { dynamic =>
          assert(closure().scope.self === Left(closureDefinition))
          assert(closure().scope.dynamicTransaction(identity) === closureDefinition)
        }
      }

      test("dynamic In Closures") {
        val closure: () => engine.CreationTicket = {
          engine.transaction() { t => () => implicitly[CreationTicket] }
        }
        engine.transaction() { dynamic =>
          assert(closure().scope.self === Right(engine.scheduler))
          assert(closure().scope.dynamicTransaction(identity) === dynamic.tx.initializer)
        }
      }

    }
  }
}