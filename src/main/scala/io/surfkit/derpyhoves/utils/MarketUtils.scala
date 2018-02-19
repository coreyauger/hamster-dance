package io.surfkit.derpyhoves.utils

import org.joda.time.{DateTime, DateTimeZone}

object MarketUtils {
  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) + "ns")
    result
  }

  def dateToMarketOpenDateTime(dt: DateTime) =
    new DateTime(dt.getYear, dt.getMonthOfYear, dt.getDayOfMonth, 9, 30 ,DateTimeZone.forID("America/New_York")).toDateTimeISO

  def dateToMarketCloseDateTime(dt: DateTime) =
    new DateTime(dt.getYear, dt.getMonthOfYear, dt.getDayOfMonth, 16, 0 ,DateTimeZone.forID("America/New_York")).toDateTimeISO

  val minuteInOpenNasdaqMarket = 391

}
