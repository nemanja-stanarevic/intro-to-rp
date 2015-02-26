package introtorp

import scala.collection.immutable._

object SyncVersion extends App {

  type DOM = HashMap[String, String]
  
  def fetchUrl(url: String): String = {
    println(s"fetchUrl invoked with $url")
    s"<html><body>$url</body></html>"
  }

  def parseHtmlToDOM(html: String): DOM = {
    println(s"parseHtmlToDOM invoked with $html")
    HashMap[String, String]("fakeDom" -> html)
  }

  def countWordOccurrencesInDOM(dom: DOM, keyword: String): Int = {
    println(s"countWordOccurrencesInDOM invoked with $dom")
    dom("fakeDom").length + 10
  }

  def countWordOccurrences(urls: List[String], keyword: String): List[(String, Int)] = {
    urls map { url =>
      val html = fetchUrl(url)
      val dom = parseHtmlToDOM(html)
      val numberOfOccurrences = countWordOccurrencesInDOM(dom, keyword)
      (url, numberOfOccurrences)
    }
  }

  println("Running sync version: ")
  val result = countWordOccurrences(
      "http://foo.com" :: "http://barbar.com" :: "http://bazbazbaz.com" :: Nil,
      "foo")
  println(s"result = $result")
}