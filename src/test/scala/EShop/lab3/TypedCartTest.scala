package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
    extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val cart = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Cart]()

    cart ! AddItem("sample_item")
    cart ! GetItems(probe.ref)

    probe.expectMessage(Cart(Seq("sample_item")))
  }

  it should "be empty after adding and removing the same item" in {
    val cart = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Cart]()

    cart ! AddItem("sample_item")
    cart ! RemoveItem("sample_item")
    cart ! GetItems(probe.ref)

    probe.expectMessage(Cart.empty)
  }

  it should "start checkout" in {
    val cart = testKit.spawn(new TypedCartActor().start)
    val probe = testKit.createTestProbe[Event]()

    cart ! AddItem("sample_item")
    cart ! StartCheckout(probe.ref)

    probe.expectMessageType[CheckoutStarted]
  }
}
