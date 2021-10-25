package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable}
import akka.event.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

    sealed trait Data

    sealed trait Command

    sealed trait Event

    case class SelectingDeliveryStarted(timer: Cancellable) extends Data

    case class ProcessingPaymentStarted(timer: Cancellable) extends Data

    case class SelectDeliveryMethod(method: String) extends Command

    case class SelectPayment(payment: String) extends Command

    case class PaymentStarted(payment: ActorRef) extends Event

    case object Uninitialized extends Data

    case object StartCheckout extends Command

    case object CancelCheckout extends Command

    case object ExpireCheckout extends Command

    case object ExpirePayment extends Command

    case object ConfirmPaymentReceived extends Command

    case object CheckOutClosed extends Event

}

class Checkout extends Actor {

    val checkoutTimerDuration: FiniteDuration = 1 seconds
    val paymentTimerDuration: FiniteDuration = 1 seconds
    private val scheduler = context.system.scheduler
    private val log = Logging(context.system, this)

    def receive: Receive = {
        case StartCheckout =>
            context.become(selectingDelivery(
                scheduler.scheduleOnce(
                    checkoutTimerDuration,
                    self,
                    ExpireCheckout
                )(context.system.dispatcher)))
    }

    def selectingDelivery(timer: Cancellable): Receive = {
        case ExpireCheckout =>
            timer.cancel()
            context.become(cancelled)
        case CancelCheckout =>
            context.become(cancelled)
        case SelectDeliveryMethod(_) =>
            timer.cancel()
            context.become(selectingPaymentMethod(
                scheduler.scheduleOnce(
                    checkoutTimerDuration,
                    self,
                    ExpireCheckout
                )(context.system.dispatcher)))
    }

    def selectingPaymentMethod(timer: Cancellable): Receive = {
        case ExpireCheckout =>
            timer.cancel()
            context.become(cancelled)
        case CancelCheckout =>
            context.become(cancelled)
        case SelectPayment(_) =>
            timer.cancel()
            context.become(processingPayment(scheduler.scheduleOnce(
                paymentTimerDuration,
                self,
                ExpirePayment,
            )(context.system.dispatcher)))
    }

    def processingPayment(timer: Cancellable): Receive = {
        case ExpirePayment =>
            timer.cancel()
            context.become(cancelled)
        case CancelCheckout =>
            context.become(cancelled)
        case ConfirmPaymentReceived =>
            timer.cancel()
            context.become(closed)
    }

    def closed: Receive = {
        case _ => context.system.stop(self)
    }

    def cancelled: Receive = {
        case _ => context.system.stop(self)
    }

}
