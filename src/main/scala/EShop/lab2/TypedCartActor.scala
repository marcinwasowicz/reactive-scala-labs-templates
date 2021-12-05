package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCartActor {

  sealed trait Command

  sealed trait Event

  sealed abstract class State(val timerOpt: Option[Cancellable]) {
    def cart: Cart
  }

  case class AddItem(item: Any) extends Command

  case class RemoveItem(item: Any) extends Command

  case class GetItems(sender: ActorRef[Cart]) extends Command

  case class StartCheckout(orderManagerToCartRef: ActorRef[Event])
      extends Command

  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])
      extends Event

  case class ItemAdded(item: Any) extends Event

  case class ItemRemoved(item: Any) extends Event

  case class NonEmpty(cart: Cart, timer: Cancellable) extends State(Some(timer))

  case class InCheckout(cart: Cart) extends State(None)

  case object ExpireCart extends Command

  case object ConfirmCheckoutCancelled extends Command

  case object ConfirmCheckoutClosed extends Command

  case object CartEmptied extends Event

  case object CartExpired extends Event

  case object CheckoutClosed extends Event

  case object CheckoutCancelled extends Event

  case object Empty extends State(None) {
    def cart: Cart = Cart.empty
  }
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive {
    (ctx, msg) =>
      msg match {
        case AddItem(item) =>
          nonEmpty(Cart.empty.addItem(item), scheduleTimer(ctx))

        case GetItems(sender) =>
          sender ! Cart.empty
          empty
      }
  }

  def nonEmpty(cart: Cart,
               timer: Cancellable): Behavior[TypedCartActor.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case ExpireCart =>
          timer.cancel()
          empty

        case AddItem(item) =>
          timer.cancel()
          nonEmpty(cart.addItem(item), scheduleTimer(ctx))

        case RemoveItem(item) =>
          timer.cancel()
          val cartSize = cart.size
          val newCart = cart.removeItem(item)
          newCart.size match {
            case 0          => empty
            case `cartSize` => Behaviors.same
            case _          => nonEmpty(newCart, scheduleTimer(ctx))
          }

        case GetItems(sender) =>
          sender ! cart
          nonEmpty(cart, timer)

        case StartCheckout(orderManagerToCartRef) =>
          timer.cancel()
          val checkout = ctx.spawn(new TypedCheckout().start, "checkout")
          val cartToCheckoutRef: ActorRef[TypedCheckout.Event] =
            ctx.messageAdapter {
              case TypedCheckout.CheckOutClosed =>
                ConfirmCheckoutClosed
            }
          checkout ! TypedCheckout.StartCheckout(cartToCheckoutRef)
          orderManagerToCartRef ! CheckoutStarted(checkout)
          inCheckout(cart)
      }
    }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case ConfirmCheckoutClosed =>
          empty
        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(ctx))
      }
    }

  private def scheduleTimer(
      context: ActorContext[TypedCartActor.Command]): Cancellable = {
    context.system.scheduler.scheduleOnce(
      cartTimerDuration,
      () => context.self ! ExpireCart
    )(context.system.executionContext)
  }

}
