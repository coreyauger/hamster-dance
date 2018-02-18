package io.surfkit.derpyhoves.flows

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream._
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.typesafe.sslconfig.akka.AkkaSSLConfig

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.reflect._
import java.io.Serializable

import play.api.libs.json.{Json, Reads}

class IexTradingWebSocket[T <: IexTrading.Iex](endpoint: String)(implicit val system: ActorSystem, um: Reads[T]) extends Serializable {
  import system.dispatcher

  private[this] val decider: Supervision.Decider = {
    case _ => Supervision.Resume
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  var responsers = Map.empty[ String, List[T => Unit] ]
  var restart = true

  def callResponders(txt: String) = {
    if(!txt.contains("success")){
      val model = Json.parse(txt).as[T]
      val key = model.getClass.getName
      responsers.get(key).map{ res =>
        res.foreach(_(model))
      }
    }else{
      println(s"socket: ${txt}")
    }
  }

  val incoming: Sink[Message, Future[Done]] =
  Sink.foreach[Message] {
    case message: TextMessage.Strict =>
      callResponders(message.text)

    case TextMessage.Streamed(stream) =>
      stream
        .limit(100)                   // Max frames we are willing to wait for
        .completionTimeout(5 seconds) // Max time until last frame
        .runFold("")(_ + _)           // Merges the frames
        .flatMap{ msg =>
          callResponders(msg)
          Future.successful(msg)
        }

    case other: BinaryMessage =>
      println(s"Got other binary...")
      other.dataStream.runWith(Sink.ignore)
  }

  // send this as a message over the WebSocket
  def outgoing = Source(List(TextMessage( "" ))).concatMat(Source.maybe[Message])(Keep.right)

  val defaultSSLConfig = AkkaSSLConfig.get(system)

  def webSocketFlow(url: String) = Http().webSocketClientFlow(WebSocketRequest(url),connectionContext = Http().createClientHttpsContext(AkkaSSLConfig()))

  def connect:UniqueKillSwitch = {
    println(s"ws calling connect: ${endpoint}")
    val (killSwitch, closed) =
      outgoing
        .keepAlive(45 seconds, () => TextMessage(""))
        .viaMat(webSocketFlow(endpoint))(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(incoming)(Keep.both) // also keep the Future[Done]
        .run()
    closed.map{_ =>
      println("Socket close...")
      if(restart) {
        println("reconnecting ..")
        killSwitchFuture = connect
      }
    }
    killSwitch
  }

  println(s"ws call connect")
  var killSwitchFuture = connect

  def shutdown: Unit = {
    println("websocket SHUTDOWN.  should not restart")
    restart = false
    killSwitchFuture.shutdown()
  }

  def subscribe[T : ClassTag](handler: T => Unit ) = {
    val key = classTag[T].runtimeClass.getName
    responsers += (responsers.get(key) match{
      case Some(xs) => key -> (handler.asInstanceOf[IexTrading.Iex => Unit] :: xs)
      case None => key -> (handler.asInstanceOf[IexTrading.Iex => Unit] :: Nil)
    })
  }


  var onError = { t:Throwable => t.getStackTrace }

}