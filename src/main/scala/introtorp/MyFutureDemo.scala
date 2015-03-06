package introtorp

import scala.concurrent.{Future, Promise}
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global

  
object MyFutureDemo extends App {
  def myFlatMap[T, S](t: Future[T], func: T => Future[S]): Future[S] = {
    val p = Promise[S]()

    // onComplete method registers a callback that is executed when
    // this Future is completed
    t onComplete {
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

  val x = Future { 1 }
  
  val f = (x:Int) => { Future { (x+1).toString } }
  
  val y = myFlatMap(x, f)
  
  y onComplete { r =>
    println(r)
  }
  
}