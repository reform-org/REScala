package replication.dtn

import com.github.plokhotnyuk.jsoniter_scala.core.{
  JsonReader, JsonValueCodec, JsonWriter, readFromArray, readFromByteBuffer, readFromString, readFromStringReentrant,
  writeToArray, writeToByteBuffer, writeToString
}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.charset.StandardCharsets
import java.util.{Base64, Objects}
import scala.concurrent.{Await, Future}
import kofre.base.{Bottom, DecomposeLattice, Lattice}
import kofre.base.Id
import kofre.syntax.*
import kofre.datatypes.PosNegCounter

import java.net.URI
import java.net.Authenticator
import java.net.http.{HttpClient, HttpRequest, WebSocket}
import java.net.http.HttpClient.{Redirect, Version}
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import de.rmgk.delay.*

import java.net.http.WebSocket.Listener
import java.nio.ByteBuffer
import java.util.Objects.isNull
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CompletableFuture, CompletionStage}

/** API base path used for http request */
def api(using scheme: String = "http"): String =
  val ip   = "127.0.0.1"
  val port = 3000
  s"$scheme://$ip:$port"

val client = HttpClient.newBuilder()
  .connectTimeout(Duration.ofSeconds(20))
  .build()

/** get uri body as string, throwing on any errors */
def sget(uri: java.net.URI): Async[Any, String] =
  client.sendAsync(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofString).toAsync.map(_.body())
def bget(uri: java.net.URI): Async[Any, Array[Byte]] =
  client.sendAsync(HttpRequest.newBuilder(uri).build(), BodyHandlers.ofByteArray()).toAsync.map(_.body())

class BinaryAsBase64(val payload: Array[Byte])

given JsonValueCodec[BinaryAsBase64] = new JsonValueCodec[BinaryAsBase64] {
  override def decodeValue(in: JsonReader, default: BinaryAsBase64): BinaryAsBase64 =
    BinaryAsBase64(in.readBase64AsBytes(null))
  override def encodeValue(x: BinaryAsBase64, out: JsonWriter): Unit =
    out.writeBase64Val(x.payload, true)
  override def nullValue: BinaryAsBase64 = null
}

// what follows are the data type and codec definitions for receiving and sending bundles
case class WsRecvData(bid: String, src: String, dst: String, data: BinaryAsBase64)
case class WsSendData(src: String, dst: String, data: BinaryAsBase64, delivery_notification: Boolean, lifetime: Long)
given JsonValueCodec[WsRecvData] = JsonCodecMaker.make
given JsonValueCodec[WsSendData] = JsonCodecMaker.make

import replication.JsoniterCodecs.given
given JsonValueCodec[PosNegCounter] = JsonCodecMaker.make

class Replica[S: Lattice: JsonValueCodec](val id: Id, dtnNodeId: String, val service: String, @volatile var data: S) {

  val connections: AtomicReference[List[WebSocket]] = {
    val ar = new AtomicReference[List[WebSocket]]
    ar.set(Nil)
    ar
  }

  val mut: ReplicaMutator[S] = new ReplicaMutator[S](this)

  def applyRemoteDelta(delta: S): Unit = synchronized {
    data = data merge delta
  }

  def applyLocalDelta(delta: S): Unit = synchronized {
    data = data merge delta
    val msg = message(delta)
    connections.get().foreach { ws =>
      ws.sendBinary(msg, true)
    }
  }

  def message(data: S): ByteBuffer = {
    val sendData = WsSendData(dtnNodeId, service, new BinaryAsBase64(writeToArray(data)), false, 60 * 1000)
    ByteBuffer.wrap(writeToArray(sendData))
  }

  def receive(data: ByteBuffer): Unit = {
    val receieved = readFromByteBuffer[WsRecvData](data)
    println(s"received: $receieved")
    val delta = readFromArray[S](receieved.data.payload)
    println(s"applying $delta")
    applyRemoteDelta(delta)
    println(s"value is now ${this.data}")
  }

  def connectOn(uri: URI): Async[Any, Unit] =
    Async {
      val listener      = ReplicaListener(this)
      val ws: WebSocket = client.newWebSocketBuilder().buildAsync(uri, listener).toAsync.bind
      println(s"starting ws handler")
      // select json communication
      ws.sendText("/json", true).toAsync.bind
      // ask to receive messages on the the given path
      ws.sendText(s"/subscribe ${service}", true).toAsync.bind
      ws.request(1) // start receiving

      listener.modeSwitched.async.bind

      // enable sending
      connections.updateAndGet(ws :: _)

      ws.sendBinary(message(data), true)
    }
}

class ReplicaListener[S: Lattice: JsonValueCodec](replica: Replica[S]) extends Listener {

  val modeSwitched: Promise[true] = Promise[true]

  override def onOpen(webSocket: WebSocket): Unit = ()
  override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[_] =
    if CharSequence.compare(data, "200 tx mode: JSON") == 0 then modeSwitched.succeed(true)
    println(data)
    super.onText(webSocket, data, last)
  override def onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage[_] = {
    replica.receive(data)

    super.onBinary(webSocket, data, last)
  }
}

class ReplicaMutator[S](val replica: Replica[S])

given fullPermission[L: DecomposeLattice: Bottom]: PermIdMutate[ReplicaMutator[L], L] = new {
  override def replicaId(c: ReplicaMutator[L]): Id = c.replica.id
  override def mutate(c: ReplicaMutator[L], delta: L): ReplicaMutator[L] =
    c.replica.applyLocalDelta(delta)
    c
  override def query(c: ReplicaMutator[L]): L = c.replica.data
}

def traverse[T](list: List[Async[Any, T]]): Async[Any, List[T]] = list match
  case Nil => Async { Nil }
  case h :: t => Async {
      val hr   = h.bind
      val rest = traverse(t).bind
      hr :: rest
    }

def run(): Unit =
  val service = "dtn://rdt/~test"

  val res = Async[Unit] {
    val nodeId = sget(URI.create(s"$api/status/nodeid")).bind
    sget(URI.create(s"$api/register?$service")).bind

    val replica = Replica(Id.gen(), nodeId, service, PosNegCounter.zero)

    val bundleString = sget(URI.create(s"$api/status/bundles")).bind
    val bundles = traverse(readFromString[List[String]](bundleString)(JsonCodecMaker.make).map { id =>
      bget(URI.create(s"$api/download?$id"))
    }).bind

    bundles.foreach(b => println(new String(b)))

    replica.connectOn(URI.create(s"${api(using "ws")}/ws")).bind

    Thread.sleep(1000)

    replica.mut.add(10)

  }.runToFuture(using ())

  Await.result(res, scala.concurrent.duration.Duration.Inf)
  Thread.sleep(1000)
