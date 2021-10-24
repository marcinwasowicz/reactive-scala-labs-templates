package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = {
      context.system.scheduler.scheduleOnce(
          cartTimerDuration,
          {() => context.self ! ExpireCart}
      )( context.system.executionContext)
  }

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive {(ctx, msg) =>
      msg match {
          case AddItem(item) => nonEmpty(Cart.empty.addItem(item), scheduleTimer(ctx))
      }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive {(ctx, msg) =>
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
                  case 0 => empty
                  case cartSize => Behaviors.same
                  case _ => nonEmpty(newCart, scheduleTimer(ctx))
              }

          case StartCheckout =>
              timer.cancel()
              inCheckout(cart)
      }
  }

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive {(ctx, msg) =>
      msg match {
          case ConfirmCheckoutClosed =>
             empty
          case ConfirmCheckoutCancelled =>
              nonEmpty(cart, scheduleTimer(ctx))
      }
  }

}
