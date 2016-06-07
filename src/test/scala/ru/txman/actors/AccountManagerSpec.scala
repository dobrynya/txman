package ru.txman.actors

import akka.testkit._
import org.scalatest._
import ru.txman.actors.Accounts._
import scala.concurrent.duration._
import ru.txman.model.AccountDetails
import akka.actor.{ActorSystem, Props}

/**
  * Specification on account manager.
  * Created by dmitry on 04.06.16.
  */
class AccountManagerSpec extends TestKit(ActorSystem("test-kit")) with ImplicitSender with FlatSpecLike with Matchers with BeforeAndAfterAll {
  val manager = system.actorOf(Props[AccountManager], "account-manager")

  val req = "request"

  "Account manager" should "respond with AccountNotFound in case of requesting operation on non-existing account" in {
    val account = "non-existing-account"
    manager ! GetAccountInfo(req, account)
    expectMsg(100 millis, AccountNotFound(req, account))

    manager ! CreditAccount(req, account, 5)
    expectMsg(100 millis, AccountNotFound(req, account))

    manager ! DebitAccount(req, account, 5)
    expectMsg(100 millis, AccountNotFound(req, account))
  }

  it should "successfully create an account" in {
    manager ! CreateAccount(req, "Sauron", 0)
    expectMsgPF(100 millis, "Account should be created!") {
      case AccountCreated(`req`, AccountDetails(account, "Sauron", balance)) if account != null && balance == BigDecimal(0) =>
    }
  }

  it should "credit existing account" in {
    manager ! CreateAccount(req, "Sauron", 0)
    expectMsgPF(200 millis, "Account should be created!") {
      case AccountCreated(`req`, AccountDetails(account, _, oldBalance)) =>
        manager ! CreditAccount(req, account, 150)
        expectMsgPF(200 millis, "Crediting account should be successful!") {
          case AccountCredited(`req`, AccountDetails(`account`, _, newBalance)) if newBalance == BigDecimal(150) =>
        }
    }
  }

  it should "debit existing account with insufficient funds" in {
    manager ! CreateAccount(req, "Saruman", 0)
    expectMsgPF(200 millis, "Account should be created!") {
      case AccountCreated(`req`, AccountDetails(account, _, oldBalance)) =>
        manager ! DebitAccount(req, account, 150)
        expectMsgPF(200 millis, "Crediting account should be successful!") {
          case AccountHasInsufficientFunds(`req`, AccountDetails(`account`, _, balance)) if balance == oldBalance =>
        }
    }
  }

  it should "debit existing account with enough funds" in {
    manager ! CreateAccount(req, "Grima", 200)
    expectMsgPF(200 millis, "Account should be created!") {
      case AccountCreated(`req`, AccountDetails(account, _, oldBalance)) =>
        manager ! DebitAccount(req, account, 150)
        expectMsgPF(200 millis, "Crediting account should be successful!") {
          case AccountDebited(`req`, AccountDetails(`account`, _, balance)) if balance == BigDecimal(50) =>
        }
    }
  }

  override protected def afterAll() = shutdown()
}
