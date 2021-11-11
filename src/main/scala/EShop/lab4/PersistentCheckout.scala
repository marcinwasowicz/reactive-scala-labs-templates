package EShop.lab4

import EShop.lab2.TypedCheckout
import EShop.lab3.Payment
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration                               = 1.seconds
  private var cartActorRef: Option[ActorRef[TypedCheckout.Event]] = None

  def apply(persistenceId: PersistenceId): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart =>
        command match {
          case StartCheckout(cartActorToCheckoutRef) =>
            cartActorRef = Some(cartActorToCheckoutRef)
            Effect.persist(CheckoutStarted)
          case _ => Effect.unhandled
        }

      case SelectingDelivery(_) =>
        command match {
          case ExpireCheckout               => Effect.persist(CheckoutCancelled)
          case CancelCheckout               => Effect.persist(CheckoutCancelled)
          case SelectDeliveryMethod(method) => Effect.persist(DeliveryMethodSelected(method))
          case _                            => Effect.unhandled
        }

      case SelectingPaymentMethod(_) =>
        command match {
          case ExpireCheckout => Effect.persist(CheckoutCancelled)
          case CancelCheckout => Effect.persist(CheckoutCancelled)
          case SelectPayment(method, _, orderManagerToPaymentRef) =>
            val checkoutToPaymentRef: ActorRef[Payment.Event] = context.messageAdapter { case Payment.PaymentReceived =>
              TypedCheckout.ConfirmPaymentReceived
            }
            val payment =
              context.spawnAnonymous(new Payment(method, orderManagerToPaymentRef, checkoutToPaymentRef).start)
            Effect.persist(PaymentStarted(payment))
          case _ => Effect.unhandled
        }

      case ProcessingPayment(_) =>
        command match {
          case ExpireCheckout => Effect.persist(CheckoutCancelled)
          case CancelCheckout => Effect.persist(CheckoutCancelled)
          case ConfirmPaymentReceived =>
            cartActorRef.get ! CheckOutClosed
            Effect.persist(CheckOutClosed)
          case _ => Effect.unhandled
        }

      case Cancelled =>
        command match {
          case _ => Effect.unhandled
        }

      case Closed =>
        command match {
          case _ => Effect.unhandled
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    if (state.timerOpt.nonEmpty) {
      state.timerOpt.get.cancel()
    }
    event match {
      case CheckoutStarted           => SelectingDelivery(schedule(context))
      case DeliveryMethodSelected(_) => SelectingPaymentMethod(schedule(context))
      case PaymentStarted(_)         => ProcessingPayment(schedule(context))
      case CheckOutClosed            => Closed
      case CheckoutCancelled         => Cancelled
    }
  }

  def schedule(context: ActorContext[Command]): Cancellable = {
    context.system.scheduler.scheduleOnce(
      timerDuration,
      () => context.self ! ExpireCheckout
    )(context.system.executionContext)
  }
}
