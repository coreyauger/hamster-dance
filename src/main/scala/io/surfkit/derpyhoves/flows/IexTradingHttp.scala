package io.surfkit.derpyhoves.flows

import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import org.joda.time.DateTimeZone
import org.joda.time.DateTime
import play.api.libs.json.{Json, Writes}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * Created by suroot.
  */
class IexTradingPoller(url: String, interval: FiniteDuration, fuzz: Double = 5.0)(implicit system: ActorSystem, materializer: Materializer) {
  import scala.concurrent.duration._
  val baseUrl = "https://api.iextrading.com/1.0/"
  val request: HttpRequest = RequestBuilding.Get(Uri(baseUrl+url))
  val initialDelay = (60.0-DateTime.now.getSecondOfMinute.toDouble) + (Math.random() * fuzz)    // set to the end of the minute plus some fuzzy
  val source: Source[HttpRequest, Cancellable] = Source.tick(initialDelay.seconds, interval, request).filter{ _ =>
    val now = DateTime.now()
    //println(s"tick: ${baseUrl+url}")
    now.isAfter(dateToMarketOpenDateTime(now)) && now.isBefore(dateToMarketCloseDateTime(now)) && now.getDayOfWeek() >= org.joda.time.DateTimeConstants.MONDAY && now.getDayOfWeek() <= org.joda.time.DateTimeConstants.FRIDAY
  }
  val sourceWithDest: Source[Try[HttpResponse], Cancellable] = source.map(req ⇒ (req, NotUsed)).via(Http().superPool[NotUsed]()).map(_._1)

  def apply(): Source[Try[HttpResponse], Cancellable] = sourceWithDest

  def shutdown = {
    Http().shutdownAllConnectionPools()
  }

  def dateToMarketOpenDateTime(dt: DateTime) =
    new DateTime(dt.getYear, dt.getMonthOfYear, dt.getDayOfMonth, 9, 30 ,DateTimeZone.forID("America/New_York")).toDateTimeISO

  def dateToMarketCloseDateTime(dt: DateTime) =
    new DateTime(dt.getYear, dt.getMonthOfYear, dt.getDayOfMonth, 16, 0 ,DateTimeZone.forID("America/New_York")).toDateTimeISO
}



class IexTradingHttp(implicit system: ActorSystem, materializer: Materializer) {
  import system.dispatcher
  val baseUrl = "https://api.iextrading.com/1.0/"

  def get(path: String) = {
      println(s"curl -XGET '${baseUrl}${path}'")
      Http().singleRequest(HttpRequest(uri = s"${baseUrl}${path}"))
  }

  def post[T <: IexTrading.Iex](path: String, post: T)(implicit uw: Writes[T]) = {
    val json = Json.stringify(uw.writes(post))
    val jsonEntity = HttpEntity(ContentTypes.`application/json`, json)
    println(s"curl -XPOST '${baseUrl}${path}' -d '${json}'")
    Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"${baseUrl}${path}", entity = jsonEntity))
  }

}
