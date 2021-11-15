package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    )
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) => Effect.persist(ItemAdded(item))
          case GetItems(sender) =>
            sender ! Cart.empty
            Effect.none
          case _ => Effect.unhandled
        }

      case NonEmpty(cart, _) =>
        command match {
          case ExpireCart    => Effect.persist(CartExpired)
          case AddItem(item) => Effect.persist(ItemAdded(item))
          case RemoveItem(item) =>
            val cartSize = cart.size
            val newCart  = cart.removeItem(item)
            newCart.size match {
              case 0          => Effect.persist(CartEmptied)
              case `cartSize` => Effect.none
              case _          => Effect.persist(ItemRemoved(item))
            }
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case StartCheckout(_) =>
            val checkout = context.spawnAnonymous(new TypedCheckout().start)
            Effect.persist(CheckoutStarted(checkout))
          case _ => Effect.unhandled
        }

      case InCheckout(_) =>
        command match {
          case ConfirmCheckoutClosed    => Effect.persist(CheckoutClosed)
          case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled)
          case _                        => Effect.unhandled
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    if (state.timerOpt.nonEmpty) {
      state.timerOpt.get.cancel()
    }

    event match {
      case CheckoutStarted(_)        => InCheckout(state.cart)
      case ItemAdded(item)           => NonEmpty(state.cart.addItem(item), scheduleTimer(context))
      case ItemRemoved(item)         => NonEmpty(state.cart.removeItem(item), scheduleTimer(context))
      case CartEmptied | CartExpired => Empty
      case CheckoutClosed            => Empty
      case CheckoutCancelled         => NonEmpty(state.cart, scheduleTimer(context))
    }
  }

  private def scheduleTimer(context: ActorContext[Command]): Cancellable = {
    context.system.scheduler.scheduleOnce(
      cartTimerDuration,
      () => context.self ! ExpireCart
    )(context.system.executionContext)
  }

}
