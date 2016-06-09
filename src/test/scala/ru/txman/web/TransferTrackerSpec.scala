package ru.txman.web

import akka.actor._
import akka.testkit._
import org.scalatest._
import scala.language.postfixOps
import scala.concurrent.duration._
import ru.txman.actors.TransactionManager._
import ru.txman.model.{TransferDetails, TransferStatus}

/**
  * Specification on TransferTracker.
  * Created by dmitry on 09.06.16.
  */
class TransferTrackerSpec extends TestKit(ActorSystem("test-kit")) with ImplicitSender with FlatSpecLike with Matchers {
  behavior of "TransferTracker"

  val transactionManager = TestProbe()

  val tracker = system.actorOf(Props(new TransferTracker(transactionManager.ref)), "transfer-tracker")

  it should "send a transfer to the transaction manager when requested" in {
    tracker ! Transfer("request", "123", "321", 50)
    transactionManager.expectMsgPF(100 millis, "Should initiate a transfer!") {
      case Transfer("request", "123", "321", amount) if amount == BigDecimal(50) =>
    }
  }

  it should "receive only closed or failed status of a transfer" in {
    val transfer = Transfer("request2", "123", "321", 50)
    tracker ! transfer
    transactionManager.expectMsg(100 millis, transfer)
    transactionManager.reply(TransferChanged("request2", TransferDetails("id", "123", "321", 50, TransferStatus.INITIATED)))
    transactionManager.reply(TransferChanged("request2", TransferDetails("id", "123", "321", 50, TransferStatus.DEBITED)))
    transactionManager.reply(TransferChanged("request2", TransferDetails("id", "123", "321", 50, TransferStatus.CLOSED)))
    expectMsgPF(100 millis, "Transfer should be closed!") {
      case DecodedTransferDetails("request2", "id", "123", "321", _, "CLOSED", None) =>
    }

    tracker ! transfer
    transactionManager.expectMsg(100 millis, transfer)
    transactionManager.reply(TransferChanged("request2", TransferDetails("id", "123", "321", 50, TransferStatus.INITIATED)))
    transactionManager.reply(TransferChanged("request2", TransferDetails("id", "123", "321", 50, TransferStatus.FAILED,
      Some("Account 123 is not found"))))

    expectMsgPF(100 millis, "Transfer should be failed!") {
      case DecodedTransferDetails("request2", "id", "123", "321", _, "FAILED", Some("Account 123 is not found")) =>
    }
  }
}
