---
layout: post
title: "An introduction to reactive programming"
issue: "two"
categories: issues two
date: 2015-03-23 09:00:00
author: Nemanja Stanarevic
---

There has been a significant shift in recent years towards server-side and
network programming using event-driven asynchronous runtime environments and
frameworks such as Node.js, Twisted, and Netty/NIO. Asynchronous code allows
independent IO operations to run concurrently, resulting in efficient code.
However, this improved efficiency comes at a cost &mdash; straightforward synchronous
code may become a mess of nested callbacks.

Can we do better? Can we combine the simplicity of synchronous
code with the efficiency of the asynchronous approach? It turns out
we can. Futures are an abstraction that allow us to express the effect of latency in
asynchronous computations, encapsulate event-handling code, and use higher-order
functions such as map, reduce, and filter, to compose clean and readable
asynchronous code.

We will explore this by looking at a web-scraping word count example. First,
we'll write simple synchronous code and consider how this code may look rewritten
with a callback-based asynchronous framework such as Node.js or Netty. Then, we'll
use promises to turn callback-based building blocks into functions returning
futures, allowing us to compose code using functional programming constructs.

While the examples below are in Scala, the abstractions and patterns described are
applicable to most modern languages. Basic familiarity with functional patterns
is assumed, but you can catch up by reading [Mary's awesome introduction to functional
programming](https://codewords.hackerschool.com/issues/one/an-introduction-to-functional-programming).


##To callback hell and back

Consider the following example of single-threaded synchronous code:
the `countWordOccurrences` function takes a `List` of URLs and a keyword, fetches
the HTML behind each URL, parses the HTML into a DOM representation, and counts the number
of times the keyword occurs within each page. It returns a `List` of pairs of
URL and count:

```scala

  def countWordOccurrences(urls: List[String], keyword: String): List[(String, Int)] = {
    urls map { url =>
      val html = fetchUrl(url)
      val dom = parseHtmlToDOM(html)
      val count = countWordOccurrencesInDOM(dom, keyword)
      (url, count)
    }
  }

```

This code is easy to reason about &mdash; operations are performed one after another,
in the specified order. However, this is not very efficient&mdash;each `fetchUrl`
call is independent from one another and thus easily parallelized, yet this code
executes them serially. Furthermore, each `fetchUrl` operation involves network
IO and will block the execution thread while waiting for IO to complete.

With a callback-based asynchronous approach, the code may look something like this:

```scala

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
                  // add this result to the accumulator
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

```

Here, each `...Async` function returns immediately and execution continues while
the network IO or other computation is underway. Callback functions are passed as
arguments to handle cases when the operation is successful and when an error occurs.
The callbacks are executed once the operation is completed.

This approach allows for more efficient use of system resources, but still has
a number of drawbacks:

 - We need to have shared state variables (`resultsAccumulator` and `isUrlCompleted`)
   and synchronize access to them
 - Each asynchronous processing step requires a nested callback, quickly leading
   to "callback hell"
 - The code is much harder to read and understand


##Futures and promises


What is particularly nice about the synchronous code in the first example is that
a function like `fetchUrl` returns a `String` value that other functions in
turn use for their computations. This leads to easily readable and composable
code.

Futures allow for a similar pattern using asynchronous rather than synchronous code. A future is an
object that expresses a result of asynchronous computation&mdash;a value that is not
available yet but may be available in the future[^1]. This allows the asynchronous version of `fetchUrl` to return a value of type `Future[String]` which is then used in synchronous-looking code, without worrying about whether a `String` value is actually available.

Promise objects are utility objects that make it easier to construct futures, like so:

```scala

  def fetchUrl(url: String): Future[String] = {
    val p = Promise[String]()
    fetchUrlAsync(url,
       successHandler = { html => p.success(html) },
       errorHandler = { error => p.failure(error) })
    p.future
  }

```

In this example, `fetchUrl` calls the callback-based counterpart `fetchUrlAsync`
with a success handler that completes `Promise p` with success and a failure
handler that completes `p` with failure. The function then extracts a `Future`
out of `Promise p` and returns it to the caller. This "wrapping" of
callback-based code using promise objects to return a future object is a
very common pattern in reactive programming.

The `parseHtmlToDOM` and `countWordOccurrencesInDOM` functions would be
similarly refactored to return `Future[DOM]` and `Future[Int]` respectively.


##Composing futures and a way to map/filter/reduce hell


The `Future` trait defines standard higher-order functions such as `map`, `flatMap`,
`filter`, `fold`, and `reduce`. These functions are not exactly the same as ones
defined on `List`, but are similar. For example, `flatMap` defined
on `Future[T]` applies a function that takes `T` as an argument and returns
`Future[S]` and flattens the resulting `Future[Future[S]]` into a single
`Future[S]`[^2].  This is analogous to how `flatMap` on `List` flattens a `List`
of `List` into a single `List`.

The higher-order functions make it possible to compose functions returning
futures using familiar functional programming patterns:

```scala
  import scala.concurrent.{ Future, Promise }
  import scala.concurrent.ExecutionContext.Implicits.global

  def countWordOccurrences(urls: List[String], keyword: String): Future[List[(String, Int)]] = {
    // partially apply countWordOccurrencesInDOM
    val countKeywordOccurrencesInDOM = countWordOccurrencesInDOM(_: DOM, keyword)

    // expression evaluates to List[Future[(String, Int)]
    val listOfFutures = urls
      .map { url =>
             fetchUrl(url)
               .flatMap(parseHtmlToDOM)
               .flatMap(countKeywordOccurrencesInDOM)
               .map { count => (url, count) } }

    // transform List[Future[(String, Int)] to Future[List[(String, Int)]]
    Future.sequence(listOfFutures)
  }

```

The new version of `countWordOccurrences` simply performs `flatMap` on the result of
`fetchUrl` over `parseHtmlToDOM` and `countKeywordOccurrencesInDOM` and 
maps the result to a pair of URL and count, resulting in `Future[(String, Int)]`.

The call to `Future.sequence` is necessary to transform the `List` of `Future[(String, Int)]`
into a `Future` of `List[(String, Int)]`.

This code appears much cleaner than the callback example. However, it is
important to note that each function in the above example is defined to take
exactly output of the preceding function. We may not be so lucky with real-world
APIs and we may need to process output before passing it to the next function.
So, it is easy to imagine that real-world code could become much more complicated
by additional processing steps. It may even feel like we are simply trading 
callback hell for map/filter/reduce hell.

Finally, reactive code composed with higher-order functions is often not purely
functional—it typically involves side effects through network or file system
IO[^3].

So, is there an even better way to write asynchronous code?


##Macros to the rescue: async/await


The [Scala Async](https://github.com/scala/async) library provides `async` and `await`
macros inspired by the similar constructs originally introduced by C#. The macros make it possible to write efficient asynchronous code in a direct style, very similar to how the synchronous code was written in the first example.

The basic approach is to wrap each block of asynchronous code within an async block
and each computation resulting in a future within an await block. The
computation within an await block will be "suspended" until the corresponding
future is completed, but in a non-blocking fashion and without any performance
penalties.

```scala
  import scala.concurrent.{ Future, Promise }
  import scala.async.Async.{ async, await }
  import scala.concurrent.ExecutionContext.Implicits.global

  def countWordOccurrences(urls: List[String], keyword: String): Future[List[(String, Int)]] = {
    // listOfFutures evaluates to type List[Future[(String, Int)]
    val listOfFutures = urls
      .map { url => async {
        // html, dom and numberOfOccurrences values are of type String,
        // DOM, and Int respectively
        val html = await { fetchUrl(url) }
        val dom = await { parseHtmlToDOM(html) }
        val count = await { countWordOccurrencesInDOM(dom, keyword) }
        (url, count)
      }
    }

    // transform List[Future[(String, Int)] to Future[List[(String, Int)]]
    Future.sequence(listOfFutures)
  }

```

The code above looks nearly identical to the synchronous code from the
first example, reflects our intent more directly, and feels more natural.


##What’s next?


We barely scratched the surface of reactive programming using `Future`, `Promise`
and `async`/`await` constructs. The upcoming Coursera course [Principles of
Reactive Programming](https://www.coursera.org/course/reactive) digs deeper into
the details, incorporates interesting programming exercises, and covers two more
reactive patterns that are particularly useful: observables and actors.

Observables provide a powerful abstraction over event streams, similarly to
how futures provide an abstraction over discrete events.  The most notable
implementation of observables is probably Microsoft's open-source [Reactive
Extensions library](https://github.com/Reactive-Extensions).  This library was
used extensively at Netflix to [redefine how they approach both back-end and
front-end development](http://techblog.netflix.com/2013/01/reactive-programming-at-netflix.html). Netflix also put together [a great interactive tutorial that covers using Reactive Extensions with JavaScript](http://jhusain.github.com/learnrx/index.html).

Actors provide the building blocks for distributed, fault-tolerant
message-passing applications, such as messaging servers, trading systems, and
telecom appliances. The most notable implementations of actors include
[Erlang OTP](http://www.erlang.org/doc/) and [TypeSafe Akka](http://akka.io/).


-----------------------------

[^1]: Since the computation may succeed or throw an error, the future will either
complete with a value (success) or with an error (failure).

[^2]: You don't need to know this to follow the rest of this article, but if you
are curious, here is a sketch of how `flatMap` may be implemented on `Future[T]`:

```scala

  def flatMap[S](func: T => Future[S]): Future[S] = {
    val p = Promise[S]()

    // onComplete method registers a callback that is executed when
    // this Future is completed
    this onComplete {
      // if this Future fails, complete the promise with failure, i.e.
      // propagate failure of "this" Future to the resulting Future[S]
      case Failure(e) => p.failure(e)

      // if "this" Future is successful, evaluate provided function
      // with its result and complete the promise appropriately
      case Success(value) =>
        // apply provided function "func" that returns Future[S]
        val resultingFuture = func(value)

        // register onComplete callback on the resulting Future[S]
        resultingFuture onComplete {
          // if computation fails, complete the promise with failure
          case Failure(e) => p.failure(e)

          // if computation returns a value, complete the promise with
          // the resulting value
          case Success(resultingValue) => p.success(resultingValue)
        }
    }

    p.future
  }

```

[^3]: This is why the popular term "Reactive Functional Programming" feels like a bit
of misnomer.
