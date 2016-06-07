package ru.txman.actors

import akka.actor._
import akka.testkit._
import org.scalatest._
import scala.language.postfixOps
import ru.txman.actors.Accounts._
import scala.concurrent.duration._
import ru.txman.model.AccountDetails

/**
  * Specification on Account.
  * Created by dmitry on 04.06.16.
  */
class AccountSpec extends TestKit(ActorSystem("test-kit")) with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll {

  val req = "request"

  "Account" should "respond with current account information" in {
    val details = AccountDetails(uuid, "Frodo Baggins", 5)
    val account = system.actorOf(newAccountProps(details), "account-1")

    account ! GetAccountInfo(req, details.id)
    expectMsgPF(100 millis) {
      case AccountInfo(`req`, AccountDetails(accountId, owner, balance))
        if balance == details.balance && owner == details.owner && accountId == details.id =>
    }
  }

  it should "not allow debit in case of insufficient funds" in {
    val details = AccountDetails(uuid, "Aragorn", 5)
    val account = system.actorOf(newAccountProps(details), "account-2")

    account ! DebitAccount(req, "", 50)
    expectMsgPF(100 millis) {
      case AccountHasInsufficientFunds(`req`, AccountDetails(_, _, balance)) if balance < 50 && balance == details.balance =>
    }
  }

  it should "debit for amount of money in case of sufficient funds" in {
    val details = AccountDetails(uuid, "Gandalf", 50)
    val account = system.actorOf(newAccountProps(details), "account-3")

    account ! DebitAccount(req, "", 50)
    expectMsgPF(100 millis) {
      case AccountDebited(`req`, AccountDetails(details.id, _, balance))  if balance == BigDecimal(0) =>
    }
  }

  it should "credit for amount of money" in {
    val details = AccountDetails(uuid, "Gandalf", 100)
    val account = system.actorOf(newAccountProps(details), "account-4")

    account ! CreditAccount(req, "", 150)

    expectMsgPF(100 millis) {
      case AccountCredited(`req`, AccountDetails(details.id, _, balance)) if balance == 250 =>
    }
  }

  override protected def afterAll() = shutdown()
}
