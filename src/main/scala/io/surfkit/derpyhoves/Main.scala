package io.surfkit.derpyhoves

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import io.surfkit.derpyhoves.flows._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

import scala.concurrent.Await

object Main extends App{

  override def main(args: Array[String]) {

    val decider: Supervision.Decider = {
      case _ => Supervision.Resume
    }
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    try {
      val api = new IexTradingApi()
      /*val news = Await.result(api.news("msft"), 5 seconds)
      println(s"MSFT news: ${news}")

      val quote = Await.result(api.quote("msft"), 5 seconds)
      println(s"MSFT quote: ${quote}")

      val chart = Await.result(api.chart("msft"), 5 seconds)
      println(s"MSFT chart: ${chart}")

      println("\nBATCH\n\n")

      val batch =  Await.result(api.batch(Seq("msft","snap","goog")), 5 seconds)
      println(batch)
*/
      /*val quoter = IexTradingLast()
      quoter.json.runForeach{ xs =>
         xs.foreach{ ys =>
           println(s"Got Values: ${ys}")
         }
      }*/

      api.last{ x: IexTrading.Last =>
        println(s"Last: ${x}")
      }

      api.tops{ x: IexTrading.Top =>
        println(s"Top: ${x}")

      }


     /* val tops = api.tops
      tops.subscribe[IexTrading.Last]{ x =>
        println(s"tops: ${x}")
      }*/

      Thread.currentThread.join()

      /*val json =
        """
          |{

          |}
        """.stripMargin

      val test = Json.parse(json).as[IexTrading.BatchResponse]*/
      //println(s"test: ${test}")
    }catch{
      case t:Throwable =>
        t.printStackTrace()
    }
  }

}
