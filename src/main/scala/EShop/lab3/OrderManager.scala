package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command

  sealed trait Ack

  case class AddItem(id: String, sender: ActorRef[Ack]) extends Command

  case class RemoveItem(id: String, sender: ActorRef[Ack]) extends Command

  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command

  case class Buy(sender: ActorRef[Ack]) extends Command

  case class Pay(sender: ActorRef[Ack]) extends Command

  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Command

  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command]) extends Command

  case object ConfirmPaymentReceived extends Command

  case object Done extends Ack
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] = Behaviors.setup { ctx =>
    val cartActor = ctx.spawn(new TypedCartActor().start, "cartActor")
    open(cartActor)
  }

  def open(
    cartActor: ActorRef[TypedCartActor.Command]
  ): Behavior[OrderManager.Command] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case AddItem(id, sender) =>
        cartActor ! TypedCartActor.AddItem(id)
        sender ! Done
        open(cartActor)
      case RemoveItem(id, sender) =>
        cartActor ! TypedCartActor.RemoveItem(id)
        sender ! Done
        open(cartActor)
      case Buy(sender) =>
        val orderManagerToCartRef: ActorRef[TypedCartActor.Event] = ctx.messageAdapter {
          case TypedCartActor.CheckoutStarted(checkoutRef) =>
            ConfirmCheckoutStarted(checkoutRef)
        }
        val orderManagerToCheckoutRef: ActorRef[TypedCheckout.Event] = ctx.messageAdapter {
          case TypedCheckout.PaymentStarted(payment) =>
            ConfirmPaymentStarted(payment)
        }
        val orderManagerToPaymentRef: ActorRef[Payment.Event] = ctx.messageAdapter { case Payment.PaymentReceived =>
          ConfirmPaymentReceived
        }
        cartActor ! TypedCartActor.StartCheckout(
          orderManagerToCartRef,
          orderManagerToCheckoutRef,
          orderManagerToPaymentRef
        )
        inCheckout(cartActor, sender)
    }
  }

  def inCheckout(
    @annotation.unused cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case ConfirmCheckoutStarted(checkoutRef) =>
        senderRef ! Done
        inCheckout(checkoutRef)
    }
  }

  def inCheckout(
    checkoutActorRef: ActorRef[TypedCheckout.Command]
  ): Behavior[OrderManager.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
        checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
        checkoutActorRef ! TypedCheckout.SelectPayment(payment)
        inPayment(sender)
    }
  }

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case ConfirmPaymentStarted(paymentRef) =>
        senderRef ! Done
        inPayment(paymentRef, senderRef)
    }
  }

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case Pay(sender) =>
        paymentActorRef ! Payment.DoPayment
        inPayment(paymentActorRef, sender)
      case ConfirmPaymentReceived =>
        senderRef ! Done
        finished
    }
  }

  def finished: Behavior[OrderManager.Command] = Behaviors.stopped
}
