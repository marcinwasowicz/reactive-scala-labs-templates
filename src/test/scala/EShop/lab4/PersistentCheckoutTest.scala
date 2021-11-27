package EShop.lab4

import EShop.lab2.TypedCheckout
import EShop.lab3.Payment
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit.SerializationSettings
import akka.persistence.typed.PersistenceId
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.duration._
import scala.util.Random

class PersistentCheckoutTest
  extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  val deliveryMethod = "post"

  import EShop.lab2.TypedCheckout._
  val paymentMethod                       = "paypal"
  private val cartActorProbe              = testKit.createTestProbe[TypedCheckout.Event]()
  private val orderManagerToCheckoutProbe = testKit.createTestProbe[Event]
  private val orderManagerToPaymentProbe  = testKit.createTestProbe[Payment.Event]
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[Command, Event, State](
      system,
      new PersistentCheckout {
        override val timerDuration: FiniteDuration = 1.second
      }.apply(generatePersistenceId),
      SerializationSettings.disabled
    )

  override def afterAll: Unit = testKit.shutdownTestKit()

  def generatePersistenceId: PersistenceId = PersistenceId.ofUniqueId(Random.alphanumeric.take(256).mkString)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  it should "be in selectingDelivery state after checkout start" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true
  }

  it should "be in cancelled state after cancel message received in selectingDelivery State" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultCancelCheckout = eventSourcedTestKit.runCommand(CancelCheckout)

    resultCancelCheckout.event shouldBe CheckoutCancelled
    resultCancelCheckout.state shouldBe Cancelled
  }

  it should "be in cancelled state after expire checkout timeout in selectingDelivery state" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    Thread.sleep(2000)

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.hasNoEvents shouldBe true
    resultSelectDelivery.state shouldBe Cancelled
  }

  it should "be in selectingPayment state after delivery method selected" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true
  }

  it should "be in cancelled state after cancel message received in selectingPayment State" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    val resultCancelCheckout = eventSourcedTestKit.runCommand(CancelCheckout)

    resultCancelCheckout.event shouldBe CheckoutCancelled
    resultCancelCheckout.state shouldBe Cancelled
  }

  it should "be in cancelled state after expire checkout timeout in selectingPayment state" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    Thread.sleep(2000)

    val resultSelectPayment = eventSourcedTestKit.runCommand(
      SelectPayment(paymentMethod, orderManagerToCheckoutProbe.ref, orderManagerToPaymentProbe.ref)
    )

    resultSelectPayment.hasNoEvents shouldBe true
    resultSelectPayment.state shouldBe Cancelled
  }

  it should "be in processingPayment state after payment selected" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    val resultSelectPayment = eventSourcedTestKit.runCommand(
      SelectPayment(paymentMethod, orderManagerToCheckoutProbe.ref, orderManagerToPaymentProbe.ref)
    )

    resultSelectPayment.event.isInstanceOf[PaymentStarted] shouldBe true
    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true
  }

  it should "be in cancelled state after cancel message received in processingPayment State" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    val resultSelectPayment = eventSourcedTestKit.runCommand(
      SelectPayment(paymentMethod, orderManagerToCheckoutProbe.ref, orderManagerToPaymentProbe.ref)
    )

    resultSelectPayment.event.isInstanceOf[PaymentStarted] shouldBe true
    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    val resultCancelCheckout = eventSourcedTestKit.runCommand(CancelCheckout)

    resultCancelCheckout.event shouldBe CheckoutCancelled
    resultCancelCheckout.state shouldBe Cancelled
  }

  it should "be in cancelled state after expire checkout timeout in processingPayment state" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    val resultSelectPayment = eventSourcedTestKit.runCommand(
      SelectPayment(paymentMethod, orderManagerToCheckoutProbe.ref, orderManagerToPaymentProbe.ref)
    )

    resultSelectPayment.event.isInstanceOf[PaymentStarted] shouldBe true
    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    Thread.sleep(2000)

    val resultReceivePayment = eventSourcedTestKit.runCommand(ConfirmPaymentReceived)

    resultReceivePayment.hasNoEvents shouldBe true
    resultReceivePayment.state shouldBe Cancelled
  }

  it should "be in closed state after payment completed" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    val resultSelectPayment = eventSourcedTestKit.runCommand(
      SelectPayment(paymentMethod, orderManagerToCheckoutProbe.ref, orderManagerToPaymentProbe.ref)
    )

    resultSelectPayment.event.isInstanceOf[PaymentStarted] shouldBe true
    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    val resultReceivePayment = eventSourcedTestKit.runCommand(ConfirmPaymentReceived)

    resultReceivePayment.event shouldBe CheckOutClosed
    resultReceivePayment.state shouldBe Closed
  }

  it should "not change state after cancel msg in completed state" in {
    val resultStartCheckout = eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))

    resultStartCheckout.event shouldBe CheckoutStarted
    resultStartCheckout.state.isInstanceOf[SelectingDelivery] shouldBe true

    val resultSelectDelivery = eventSourcedTestKit.runCommand(SelectDeliveryMethod(deliveryMethod))

    resultSelectDelivery.event.isInstanceOf[DeliveryMethodSelected] shouldBe true
    resultSelectDelivery.state.isInstanceOf[SelectingPaymentMethod] shouldBe true

    val resultSelectPayment = eventSourcedTestKit.runCommand(
      SelectPayment(paymentMethod, orderManagerToCheckoutProbe.ref, orderManagerToPaymentProbe.ref)
    )

    resultSelectPayment.event.isInstanceOf[PaymentStarted] shouldBe true
    resultSelectPayment.state.isInstanceOf[ProcessingPayment] shouldBe true

    val resultReceivePayment = eventSourcedTestKit.runCommand(ConfirmPaymentReceived)

    resultReceivePayment.event shouldBe CheckOutClosed
    resultReceivePayment.state shouldBe Closed

    val resultCancelCheckout = eventSourcedTestKit.runCommand(CancelCheckout)

    resultCancelCheckout.hasNoEvents shouldBe true
    resultCancelCheckout.state shouldBe Closed
  }

  it should "correctly recover its state and timers after system being forcefully terminated" in {
    eventSourcedTestKit.runCommand(StartCheckout(cartActorProbe.ref))
    eventSourcedTestKit.restart()
    eventSourcedTestKit.getState().isInstanceOf[SelectingDelivery] shouldBe true
    Thread.sleep(2000)
    eventSourcedTestKit.getState() shouldBe Cancelled
  }
}