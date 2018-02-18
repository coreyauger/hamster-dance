package io.surfkit.derpyhoves.flows

import akka.actor.{ActorSystem, Cancellable}
import scala.concurrent.ExecutionContext
import play.api.libs.json._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
/**
  * Created by suroot.
  */
object IexTrading{

  sealed trait Iex

  case class IexQuote(
                   iexMarketPercent: Option[Double],
                   iexVolume: Option[Int],
                   iexRealtimePrice: Option[Double],
                   iexRealtimeSize: Option[Double],
                   iexBidPrice: Option[Double],
                   iexBidSize: Option[Double],
                   iexAskPrice: Option[Double],
                   iexAskSize: Option[Double],
                   iexLastUpdated: Option[Long]
                     ) extends Iex
  implicit val iexQuoteWrites = Json.writes[IexQuote]
  implicit val iexQuoteReads = Json.reads[IexQuote]

  case class LatestQuote(
                  latestPrice: Double,
                  latestSource: String,
                  latestTime: String,
                  latestUpdate: Long,
                          latestVolume: Int
                        ) extends Iex
  implicit val LatestQuoteWrites = Json.writes[LatestQuote]
  implicit val LatestQuoteReads = Json.reads[LatestQuote]

  case class QuoteExtra(
                  delayedPrice: Option[Double],
                  delayedPriceTime: Option[Long],
                  previousClose: Option[Double],
                  change: Option[Double],
                  changePercent: Option[Double],
                  avgTotalVolume: Option[Int],
                  marketCap: Option[Long],
                  peRatio: Option[Double],
                  week52High: Option[Double],
                  week52Low: Option[Double],
                  ytdChange: Option[Double]
                       ) extends Iex
  implicit val QuoteExtraWrites = Json.writes[QuoteExtra]
  implicit val QuoteExtraReads = Json.reads[QuoteExtra]

  case class Quote(
                  symbol: String,
                  companyName: Option[String],
                  primaryExchange: Option[String],
                  sector: Option[String],
                  calculationPrice: String,
                  open: Double,
                  openTime: Long,
                  close: Double,
                  closeTime: Long,
                  high: Double,
                  low: Double,
                  iex: IexQuote,
                  latest: LatestQuote,
                  extra: QuoteExtra
                  ) extends Iex
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  implicit val quoteReads: Reads[Quote] = (
      (JsPath \ "symbol").read[String] and
      (JsPath \ "companyName").readNullable[String] and
      (JsPath \ "primaryExchange").readNullable[String] and
      (JsPath \ "sector").readNullable[String] and
      (JsPath \ "calculationPrice").read[String] and
      (JsPath \ "open").read[Double] and
      (JsPath \ "openTime").read[Long] and
      (JsPath \ "close").read[Double] and
      (JsPath \ "closeTime").read[Long] and
      (JsPath \ "high").read[Double] and
      (JsPath \ "low").read[Double] and
      (JsPath).read[IexQuote] and
      (JsPath).read[LatestQuote] and
      (JsPath).read[QuoteExtra]
    )(Quote.apply _)
  implicit val quoteWrites = Json.writes[Quote]


  case class News(
                  datetime: String,
                  headline: String,
                  source:	String,
                  url:	String,
                  summary: 	String,
                  related: String
                 ) extends Iex
  implicit val newsWrites = Json.writes[News]
  implicit val newsReads = Json.reads[News]

  trait Ts extends Iex{
    def date: String
    def high: Double
    def low: Double
    def volume: Int
    def label: String
  }

  case class Ts1Min(
                  date: String,
                  minute: String,
                  label: String,
                  high: Double,
                  low: Double,
                  average: Double,
                  volume: Int,
                  notional: Option[Double],
                  numberOfTrades: Option[Int],
                  marketHigh: Option[Double],
                  marketLow: Option[Double],
                  marketAverage: Option[Double],
                  marketVolume: Option[Int],
                  marketNotional: Option[Double],
                  marketNumberOfTrades: Option[Int],
                  marketChangeOverTime: Option[Double],
                  changeOverTime: Option[Double]
                   ) extends Ts
  implicit val ts1MinWrites = Json.writes[Ts1Min]
  implicit val ts1MinReads = Json.reads[Ts1Min]

  case class Ts1Day(
                  date: String,
                  open: Double,
                  high: Double,
                  low: Double,
                  close: Double,
                  volume: Int,
                  unadjustedVolume: Int,
                  change: Double,
                  changePercent: Double,
                  vwap: Double,
                  label: String,
                  changeOverTime: Double
                                   ) extends Ts
  implicit val ts1DayWrites = Json.writes[Ts1Day]
  implicit val ts1DayReads = Json.reads[Ts1Day]

  case class Batch(
                  quote: Option[Quote],
                  chart: Option[Seq[Ts1Min]],
                  news: Option[Seq[News]]
                  ) extends Iex
  implicit val batchWrites = Json.writes[Batch]
  implicit val batchReads = Json.reads[Batch]

  case class BatchResponse(batch: Seq[(String, Batch)]) extends Iex
  implicit val tsFormat: Format[BatchResponse] =
    new Format[BatchResponse] {
      override def reads(json: JsValue): JsResult[BatchResponse] = json match {
        case j: JsObject =>
          JsSuccess(BatchResponse(j.fields.map {
            case (name, rest) =>
              rest.validate[Batch] match {
                case JsSuccess(validSize, _) => (name, validSize)
                case e: JsError => return e
              }
          }))
        case _ =>
          JsError("Invalid JSON type")
      }
      override def writes(o: BatchResponse): JsValue = Json.toJson(o.batch.toMap)
    }

  case class Last(
                  symbol: String,
                  price: Double,
                  size: Int,
                  time: Long
                 ) extends Iex
  implicit val lastWrites = Json.writes[Last]
  implicit val lastReads = Json.reads[Last]

  case class Top(
                symbol: String,
                marketPercent: Option[Double],
                bidSize: Int,
                bidPrice: Double,
                askSize: Int,
                askPrice: Double,
                volume: Int,
                lastSalePrice: Double,
                lastSaleSize: Int,
                lastSaleTime: Long,
                lastUpdated: Long,
                sector: Option[String],
                securityType: Option[String]
                ) extends Iex
  implicit val topWrites = Json.writes[Top]
  implicit val topReads = Json.reads[Top]

}

class IexTradingTicker[T <: IexTrading.Iex](endpoint: String, interval: FiniteDuration)(implicit system: ActorSystem, materializer: Materializer, um: Reads[T]) extends IexTradingPoller(url = endpoint, interval = interval) with PlayJsonSupport{
  def json(): Source[Future[T], Cancellable] = super.apply().map{
    case scala.util.Success(response) => Unmarshal(response.entity).to[T]
    case scala.util.Failure(ex) => Future.failed(ex)
  }
}

case class IexTradingQuoter(symbols: Seq[String], interval: FiniteDuration = 1 minute)(implicit system: ActorSystem, materializer: Materializer)
  extends IexTradingTicker[IexTrading.BatchResponse](s"stock/market/batch${symbols.mkString("?symbols=",",","")}&types=quote", interval)

case class IexTradingLast(symbols: Seq[String] = Seq.empty, interval: FiniteDuration = 10 seconds)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Seq[IexTrading.Last]]) extends IexTradingPoller(url = s"tops/last${if(symbols.isEmpty) "" else symbols.mkString("?symbols=",",","")}", interval = interval) with PlayJsonSupport {
  def json(): Source[Future[Seq[IexTrading.Last]], Cancellable] = super.apply().map {
    case scala.util.Success(response) => Unmarshal(response.entity).to[Seq[IexTrading.Last]]
    case scala.util.Failure(ex) => Future.failed(ex)
  }
}


class IexTradingApi()(implicit system: ActorSystem, materializer: Materializer, ex: ExecutionContext) extends PlayJsonSupport {

  object httpApi extends IexTradingHttp

  def unmarshal[T <: IexTrading.Iex](response: HttpResponse)(implicit um: Reads[T]):Future[T] = Unmarshal(response.entity).to[T]

  def news(symbol: String, last: Int = 10)(implicit um: Reads[Seq[IexTrading.News]]) =
    httpApi.get(s"stock/${symbol}/news/last/${last}").flatMap(x => Unmarshal(x.entity).to[Seq[IexTrading.News]] )

  def quote(symbol: String)(implicit um: Reads[IexTrading.Quote]) =
    httpApi.get(s"stock/${symbol}/quote").flatMap(x => unmarshal(x) )

  def chart(symbol: String)(implicit um: Reads[Seq[IexTrading.Ts1Min]]) =
    httpApi.get(s"stock/${symbol}/chart/1d").flatMap(x => Unmarshal(x.entity).to[Seq[IexTrading.Ts1Min]] )

  def batch(symbols: Seq[String], types: Set[String] = Set("quote","chart"))(implicit um: Reads[IexTrading.BatchResponse]) =
    httpApi.get(s"stock/market/batch${symbols.mkString("?symbols=",",","")}${types.mkString("&types=",",","")}&range=1d").flatMap(x => unmarshal(x) )
/*
  def bracket(account: String, post: Questrade.PostBracket)(implicit um: Reads[Questrade.OrderResponse],uw1: Writes[Questrade.BracketOrder]) =
    httpApi.post[Questrade.PostBracket](s"accounts/${account}/orders/bracket", post).flatMap(x => unmarshal(x))
*/

  def last(implicit um: Reads[IexTrading.Last]) =
    new IexTradingWebSocket[IexTrading.Last]("wss://ws-api.iextrading.com/1.0/last")

  def tops(implicit um: Reads[IexTrading.Top]) =
    new IexTradingWebSocket[IexTrading.Top]("wss://ws-api.iextrading.com/1.0/tops")

}