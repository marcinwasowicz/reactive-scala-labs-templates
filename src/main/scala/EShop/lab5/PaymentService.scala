package EShop.lab5

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.util.{Failure, Success}

object PaymentService {

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply(
      method: String,
      payment: ActorRef[Response]
  ): Behavior[HttpResponse] = Behaviors.setup { ctx =>
    implicit val system = ctx.system
    implicit val executionContext = ctx.system.executionContext

    val response = Http().singleRequest(HttpRequest(uri = getURI(method)))
    response.onComplete {
      case Success(httpResponse) => ctx.self ! httpResponse
      case Failure(reason)       => throw reason
    }
    Behaviors.receiveMessage {
      case HttpResponse(StatusCodes.OK, _, _, _) =>
        payment ! PaymentSucceeded
        Behaviors.stopped

      case HttpResponse(StatusCodes.RequestTimeout, _, _, _) =>
        throw PaymentServerError()

      case HttpResponse(StatusCodes.NotFound, _, _, _) =>
        throw PaymentClientError()
    }
  }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:9500"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }

  sealed trait Response

  case class PaymentClientError() extends Exception

  case class PaymentServerError() extends Exception

  case object PaymentSucceeded extends Response
}
