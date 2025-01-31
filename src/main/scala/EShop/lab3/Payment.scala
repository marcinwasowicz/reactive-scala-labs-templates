package EShop.lab3

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  def apply(method: String,
            orderManager: ActorRef[Event],
            checkout: ActorRef[Event]): Behavior[Command] =
    Behaviors.setup(_ => new Payment(method, orderManager, checkout).start)

  sealed trait Command

  sealed trait Event

  case object DoPayment extends Command

  case object PaymentReceived extends Event
}

class Payment(
    method: String,
    orderManager: ActorRef[Payment.Event],
    checkout: ActorRef[Payment.Event]
) {

  import Payment._

  def start: Behavior[Payment.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case DoPayment =>
        orderManager ! PaymentReceived
        checkout ! PaymentReceived
        Behaviors.stopped
    }
  }

}
