package rescala.extra.lattices.delta

case class TimedVal[A](value: A, replicaID: String, nanoTime: Long, timestamp: Long) {
  def laterThan(other: TimedVal[A]): Boolean =
    this.timestamp > other.timestamp ||
      this.timestamp == other.timestamp &&
      (
        this.replicaID > other.replicaID ||
          this.replicaID == other.replicaID && this.nanoTime > other.nanoTime
      )
}

object TimedVal {
  def apply[A](value: A, replicaID: String): TimedVal[A] =
    TimedVal(value, replicaID, System.nanoTime(), System.currentTimeMillis())

  implicit def TimedValAsUIJDLattice[A]: UIJDLattice[TimedVal[A]] = new UIJDLattice[TimedVal[A]] {
    override def leq(left: TimedVal[A], right: TimedVal[A]): Boolean = left.timestamp <= right.timestamp

    /** Decomposes a lattice state into its unique irredundant join decomposition of join-irreducible states */
    override def decompose(state: TimedVal[A]): Iterable[TimedVal[A]] = List(state)

    override def bottom: TimedVal[A] = throw new UnsupportedOperationException("TimedVal does not have a bottom value")

    /** By assumption: associative, commutative, idempotent. */
    override def merge(left: TimedVal[A], right: TimedVal[A]): TimedVal[A] =
      if (left.laterThan(right)) left else right
  }
}