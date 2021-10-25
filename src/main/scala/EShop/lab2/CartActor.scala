package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

    def props = Props(new CartActor())

    sealed trait Command

    sealed trait Event

    case class AddItem(item: Any) extends Command

    case class RemoveItem(item: Any) extends Command

    case class CheckoutStarted(checkoutRef: ActorRef) extends Event

    case object ExpireCart extends Command

    case object StartCheckout extends Command

    case object ConfirmCheckoutCancelled extends Command

    case object ConfirmCheckoutClosed extends Command
}

class CartActor extends Actor {

    import CartActor._

    val cartTimerDuration: FiniteDuration = 5 seconds
    private val log = Logging(context.system, this)

    def receive: Receive = empty

    def empty: Receive = {
        case AddItem(item) => context.become(nonEmpty(Cart.empty.addItem(item), scheduleTimer))
    }

    def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
        case ExpireCart =>
            timer.cancel()
            context.become(empty)

        case AddItem(item) =>
            timer.cancel()
            context.become(nonEmpty(cart.addItem(item), scheduleTimer))

        case RemoveItem(item) =>
            timer.cancel()
            val newCart = cart.removeItem(item)
            if (cart.contains(item)) {
                newCart.size match {
                    case 0 => context.become(empty)
                    case _ => context.become(nonEmpty(newCart, scheduleTimer))
                }
            }

        case StartCheckout =>
            timer.cancel()
            context.become(inCheckout(cart))
    }

    def inCheckout(cart: Cart): Receive = {
        case ConfirmCheckoutClosed =>
            context.become(empty)
        case ConfirmCheckoutCancelled =>
            context.become(nonEmpty(cart, scheduleTimer))
    }

    private def scheduleTimer: Cancellable = {
        context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)(context.system.dispatcher)
    }

}
