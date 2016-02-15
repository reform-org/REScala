package rescala

object Infiltrator {
  final def getLevel[S <: graph.Spores](reactive: graph.Reactive[S])(implicit maybe: engines.Ticket[S]) = maybe { reactive.bud.level(_) }
}
