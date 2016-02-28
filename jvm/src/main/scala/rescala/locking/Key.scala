package rescala.locking

import java.util.concurrent.{ConcurrentHashMap, Semaphore}

import rescala.graph.Globals

import scala.annotation.tailrec

final class Key[InterTurn](val turn: InterTurn) {

  val id = Globals.nextID()
  override def toString: String = s"Key($id)"

  @volatile var keychain: Keychain[InterTurn] = new Keychain(this)

  private[this] val semaphore = new Semaphore(0)

  def continue(): Unit = semaphore.release()
  def await(): Unit = semaphore.acquire()


  def lockKeychain[R](f: => R): R = {
    @tailrec def loop(): R = {
      val oldChain = keychain
      keychain.synchronized {
        if (oldChain eq keychain) Some(f)
        else None
      } match {
        case None => loop()
        case Some(r) => r
      }
    }
    loop()
  }

  /** contains a list of all locks owned by us. */
  private[this] val heldLocks: ConcurrentHashMap[TurnLock[InterTurn], Boolean] = new ConcurrentHashMap[TurnLock[InterTurn], Boolean]()

  def addLock(lock: TurnLock[InterTurn]): Unit = heldLocks.put(lock, true)

  def grabLocks() = heldLocks

  /** release all locks we hold or transfer them to a waiting transaction if there is one
    * holds the master lock for request */
  def releaseAll(): Unit = lockKeychain {keychain.release(this)}

}