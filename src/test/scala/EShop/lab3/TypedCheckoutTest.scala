package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val trivialAckProbe = testKit.createTestProbe[String]()
    val cart            = testKit.spawn(TypedCheckoutTest.createCartWithResponseOnStateChange(trivialAckProbe.ref))
    val orderManagerToCartMapper     = testKit.createTestProbe[TypedCartActor.Event]()
    val orderManagerToCheckoutMapper = testKit.createTestProbe[TypedCheckout.Event]()
    val orderManagerToPaymentMapper  = testKit.createTestProbe[Payment.Event]()

    cart ! TypedCartActor.AddItem("sample_item")
    cart ! TypedCartActor.StartCheckout(orderManagerToCartMapper.ref)
    val checkoutRef = orderManagerToCartMapper.expectMessageType[TypedCartActor.CheckoutStarted].checkoutRef

    checkoutRef ! SelectDeliveryMethod("sample_deliver")
    checkoutRef ! SelectPayment("sample_payment", orderManagerToCheckoutMapper.ref, orderManagerToPaymentMapper.ref)

    trivialAckProbe.expectMessage(TypedCheckoutTest.ack)
  }
}

object TypedCheckoutTest {
  val ack = "Ack"
  def createCartWithResponseOnStateChange(trivialAckProbeRef: ActorRef[String]): Behavior[TypedCartActor.Command] = {
    new TypedCartActor() {
      override def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = {
        val result = super.inCheckout(cart)
        trivialAckProbeRef ! ack
        result
      }
    }.start
  }
}
