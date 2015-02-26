package introtorp

import scala.collection.immutable._
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.async.Async.{async, await}

object AsyncAwaitVersion extends App {

  type DOM = HashMap[String, String]

  def fetchUrlAsync(
      url: String,
      successHandler: String => Unit,
      errorHandler: Throwable => Unit): Unit = {
    println(s"fetchUrlAsync invoked with $url")
    successHandler(s"<html><body>$url</body></html>")
  }

  def parseHtmlToDOMAsync(
      html: String,
      successHandler: DOM => Unit,
      errorHandler: Throwable => Unit): Unit = {
    println(s"parseHtmlToDOMAsync invoked with $html")
    successHandler(HashMap[String, String]("fakeDom" -> html))
  }

  def countWordOccurrencesInDOMAsync(
      dom: DOM, 
      keyword: String,
      successHandler: Int => Unit,
      errorHandler: Throwable => Unit): Unit = {

    println(s"countWordOccurrencesInDOMAsync invoked with $dom")
    successHandler(dom("fakeDom").length + 10)
  }

  def fetchUrl(url: String) : Future[String] = {
    val p = Promise[String]()
    fetchUrlAsync(url,
       successHandler = { html => p.success(html) },
       errorHandler = { error => p.failure(error) })
    p.future
  }

  def parseHtmlToDOM(html: String): Future[DOM] = {
    val p = Promise[DOM]()
    parseHtmlToDOMAsync(html,
       successHandler = { dom => p.success(dom) },
       errorHandler = { error => p.failure(error) })
    p.future
  }

  def countWordOccurrencesInDOM(dom: DOM, keyword: String): Future[Int] = {
    val p = Promise[Int]()
    countWordOccurrencesInDOMAsync(
       dom,
       keyword,
       successHandler = { count => p.success(count) },
       errorHandler = { error => p.failure(error) })
    p.future
  }

  def countWordOccurrences(urls: List[String], keyword: String): Future[List[(String, Int)]] = {
    // listOfFutures evaluates to type of List[Future[(String, Int)]
    val listOfFutures = urls
      .map { url => async {
        // html, dom and numberOfOccurences values are of type String, 
        // DOM, and Int respectively
        val html = await { fetchUrl(url) }
        val dom = await { parseHtmlToDOM(html) }
        val numberOfOccurrences = await { countWordOccurrencesInDOM(dom, keyword) }
        (url, numberOfOccurrences) 
      }
    }

    // transform List[Future[(String, Int)] to Future[List[(String, Int)]]
    Future.sequence(listOfFutures)
  }

  println("Running async/callback version: ")
  val future = countWordOccurrences(
    "http://foo.com" :: "http://barbar.com" :: "http://bazbazbaz.com" :: Nil,
    "foo")
  future onComplete { list =>
    println(s"result = $list")
  }
}