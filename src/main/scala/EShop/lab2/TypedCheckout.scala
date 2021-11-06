package EShop.lab2

import EShop.lab3.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCheckout {

  sealed trait Data

  sealed trait Command

  sealed trait Event

  case class SelectingDeliveryStarted(timer: Cancellable) extends Data

  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  case class SelectDeliveryMethod(method: String) extends Command

  case class SelectPayment(payment: String) extends Command

  case class StartCheckout(
    cartActorToCheckoutRef: ActorRef[Event],
    orderManagerToCheckoutRef: ActorRef[Event],
    orderManagerToPaymentRef: ActorRef[Payment.Event]
  ) extends Command

  case class PaymentStarted(payment: ActorRef[Payment.Command]) extends Event

  case object Uninitialized extends Data

  case object CancelCheckout extends Command

  case object ExpireCheckout extends Command

  case object ExpirePayment extends Command

  case object ConfirmPaymentReceived extends Command

  case object CheckOutClosed extends Event
}

class TypedCheckout {

  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case StartCheckout(cartActorToCheckoutRef, orderManagerToCheckoutRef, orderManagerToPaymentRef) =>
        selectingDelivery(
          ctx.system.scheduler.scheduleOnce(
            checkoutTimerDuration,
            () => ctx.self ! ExpireCheckout
          )(ctx.system.executionContext),
          cartActorToCheckoutRef,
          orderManagerToCheckoutRef,
          orderManagerToPaymentRef
        )

    }
  }

  def selectingDelivery(
    timer: Cancellable,
    cartActorToCheckoutRef: ActorRef[Event],
    orderManagerToCheckoutRef: ActorRef[Event],
    orderManagerToPaymentRef: ActorRef[Payment.Event]
  ): Behavior[TypedCheckout.Command] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ExpireCheckout =>
        timer.cancel()
        cancelled
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case SelectDeliveryMethod(_) =>
        timer.cancel()
        selectingPaymentMethod(
          ctx.system.scheduler.scheduleOnce(
            checkoutTimerDuration,
            () => ctx.self ! ExpireCheckout
          )(ctx.system.executionContext),
          cartActorToCheckoutRef,
          orderManagerToCheckoutRef,
          orderManagerToPaymentRef
        )
    }
  }

  def selectingPaymentMethod(
    timer: Cancellable,
    cartActorToCheckoutRef: ActorRef[Event],
    orderManagerToCheckoutRef: ActorRef[Event],
    orderManagerToPaymentRef: ActorRef[Payment.Event]
  ): Behavior[TypedCheckout.Command] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ExpireCheckout =>
        timer.cancel()
        cancelled
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case SelectPayment(method) =>
        timer.cancel()
        val checkoutToPaymentRef: ActorRef[Payment.Event] = ctx.messageAdapter { case Payment.PaymentReceived =>
          TypedCheckout.ConfirmPaymentReceived
        }
        val payment = ctx.spawn(new Payment(method, orderManagerToPaymentRef, checkoutToPaymentRef).start, "payment")
        orderManagerToCheckoutRef ! PaymentStarted(payment)
        processingPayment(
          ctx.system.scheduler.scheduleOnce(
            paymentTimerDuration,
            () => ctx.self ! ExpirePayment
          )(ctx.system.executionContext),
          cartActorToCheckoutRef
        )
    }
  }

  def processingPayment(
    timer: Cancellable,
    cartActorToCheckoutRef: ActorRef[Event]
  ): Behavior[TypedCheckout.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case ExpirePayment =>
        timer.cancel()
        cancelled
      case CancelCheckout =>
        timer.cancel()
        cancelled
      case ConfirmPaymentReceived =>
        timer.cancel()
        cartActorToCheckoutRef ! CheckOutClosed
        closed
    }
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case _ => Behaviors.same
    }
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive { (_, msg) =>
    msg match {
      case _ => Behaviors.same
    }
  }

}
