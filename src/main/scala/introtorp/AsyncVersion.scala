package introtorp

import scala.collection.immutable._

object AsyncVersion extends App {

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

    println(s"parseHtmlToDOMAsync invoked with $dom")
    successHandler(dom("fakeDom").length + 10)
  }

  def countWordOccurrencesAsync(
      urls: List[String], 
      keyword: String,
      successHandler: (List[(String, Int)]) => Unit,
      errorHandler: (Throwable) => Unit): Unit = {

    // access to these shared variables would need 
    // to be synchronized, adding even more complexity
    var resultsAccumulator = List[(String, Int)]()
    var isUrlCompleted = urls
      .foldLeft(HashMap[String, Boolean]()) {
        (acc, url) =>
          acc.updated(url, false)
      }

    urls map { url =>
      fetchUrlAsync(
        url, 
        successHandler = { html =>
          parseHtmlToDOMAsync(
            html,
            successHandler = { dom =>
              countWordOccurrencesInDOMAsync(
                dom,
                keyword, 
                successHandler = { count =>
                  // add this result to the resultsAccumulator
                  resultsAccumulator = (url, count) :: resultsAccumulator

                  // update state to denote that this url is completed
                  isUrlCompleted = isUrlCompleted.updated(url, true)

                  // check if all urls have been completed
                  // if so, invoke the top success handler
                  val allDone = isUrlCompleted
                                  .map { case(key, value) => value }
                                  .reduce { (a, b) => a && b }
                  if (allDone) {
                    successHandler(resultsAccumulator)
                  }
                },
                errorHandler)
            },
            errorHandler)
        },
        errorHandler)
    }
  }

  println("Running async/callback version: ")
  countWordOccurrencesAsync(
    "http://foo.com" :: "http://barbar.com" :: "http://bazbazbaz.com" :: Nil,
    "foo", 
    successHandler = {
      result => println(s"result = $result") 
    },
    errorHandler = {
      error =>  println(s"something went wrong: $error")
    })
}