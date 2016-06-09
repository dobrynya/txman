package ru.txman.actors

import akka.actor._
import akka.testkit._
import org.scalatest._
import ru.txman.model._
import TransactionManager._
import scala.language.postfixOps
import ru.txman.actors.Accounts._
import scala.concurrent.duration._

/**
  * Specification on TransactionManager.
  * Created by dmitry on 05.06.16.
  */
class TransactionManagerSpec extends TestKit(ActorSystem("test-kit")) with ImplicitSender
  with FlatSpecLike with Matchers {

  val accountManager = TestProbe()
  val transactionManager = system.actorOf(Props(classOf[TransactionManager], accountManager.ref), "transaction-manager")

  "Transaction manager" should "fail transaction in case of negative amount of money" in {
    intercept[Exception] {
      transactionManager ! Transfer("request", "acc-1", "acc-2", -5)
    }
  }

  it should "initiate a transfer and fail it in case invalid debit account" in {
    transactionManager ! Transfer("request", "non-existing-account", "nevermind", 100)
    expectMsgPF(100 millis, "Should initiate a transfer!") {
      case TransferChanged("request", TransferDetails(_, "non-existing-account", "nevermind", tr, TransferStatus.INITIATED, None))
        if tr == 100 =>

        accountManager.expectMsgPF() {
          case DebitAccount("request", "non-existing-account", balance) if balance == 100 =>
            accountManager.reply(AccountNotFound("request", "non-existing-account"))

            expectMsgPF(100 millis, "Should be failed transfer!") {
              case TransferChanged("request", TransferDetails(id, "non-existing-account", _, money, TransferStatus.FAILED, Some(reason)))
                if reason contains "Source account non-existing-account is not found" =>
            }
        }
    }
  }

  it should "credit back debited account and fail a transfer in case invalid account being credited" in {
    val quater = BigDecimal(250)
    transactionManager ! Transfer("request", "debited", "non-existing-account", quater)
    expectMsgPF(100 millis, "Should initiate a transfer!") {
      case TransferChanged("request", TransferDetails(_, "debited", "non-existing-account", `quater`, TransferStatus.INITIATED, None)) =>

        accountManager.expectMsgPF(100 millis, "Should debit!") {
          case DebitAccount("request", "debited", `quater`) =>
            accountManager.reply(AccountDebited("request", AccountDetails("debited", "", 500)))

            accountManager.expectMsgPF(100 millis, "Should not find account to be credited!") {
              case CreditAccount("request", "non-existing-account", `quater`) =>
               accountManager.reply(AccountNotFound("request", "non-existing-account"))

                // check whether first debit is rolled back
                accountManager.expectMsgPF(100 millis, "Roll back transfer!") {
                  case CreditAccount("request", "debited", `quater`) =>

                    expectMsgPF(100 millis, "Should be failed transfer!") {
                      case TransferChanged("request", TransferDetails(_, "debited", "non-existing-account", _,
                      TransferStatus.FAILED, Some(reason))) if reason contains "Destination account non-existing-account is not found" =>
                    }
                }
            }
        }
    }
  }

  it should "successfully complete a transfer" in {
    val quater = BigDecimal(250)
    transactionManager ! Transfer("request", "debited", "credited", quater)
    expectMsgPF(100 millis, "Should initiate a transfer!") {
      case TransferChanged("request", TransferDetails(_, "debited", "credited", `quater`, TransferStatus.INITIATED, None)) =>

        accountManager.expectMsgPF(100 millis, "Should debit!") {
          case DebitAccount("request", "debited", `quater`) =>
            accountManager.reply(AccountDebited("request", AccountDetails("debited", "", 500)))

            accountManager.expectMsgPF(100 millis, "Should complete a transfer!") {
              case CreditAccount("request", "credited", `quater`) =>
                accountManager.reply(AccountCredited("request", AccountDetails("credited", "", 1000)))

                expectMsgPF(100 millis, "Should be closed transfer!") {
                  case TransferChanged("request", TransferDetails(_, "debited", "credited", _, TransferStatus.CLOSED, None)) =>
                }
            }
        }
    }
  }
}