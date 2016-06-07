package ru.txman.actors

import akka.actor._
import ru.txman.model._
import ru.txman.actors.Accounts._
import akka.persistence.PersistentActor

/**
  * Provides account related operations such as debit, credit and so on.
  * Created by dmitry on 04.06.16.
  */
class Account(var details: AccountDetails) extends PersistentActor with ActorLogging {
  def persistenceId = "account-%s" format details.id

  def receiveRecover = {
    case newState: ChangeState =>
      log.debug("Account changed its state to {}", newState)
      details = newState.details
  }

  def receiveCommand = {
    case GetAccountInfo(request, _) =>
      log.debug("Account info is {}, request {}", details, request)
      sender ! AccountInfo(request, details)

    case DebitAccount(request, _, value) if details.balance >= value =>
      persist(AccountDebited(request, details.copy(balance =  details.balance - value))) { debited =>
        log.debug("Account {} has been debited for {}", details.id, value)
        details = debited.details
        sender ! debited
      }

    case DebitAccount(request, _, value) => // no need to persist any event
      log.debug("Insufficient funds for debiting account for {}", value)
      sender ! AccountHasInsufficientFunds(request, details)

    case CreditAccount(request, _, value) =>
      persist(AccountCredited(request, details.copy(balance = details.balance + value))) { credited =>
        log.debug("Account {} has been credited for {}", details.id, value)
        details = credited.details
        sender ! credited
      }
  }
}

/**
  * Provides operations with accounts.
  */
class AccountManager extends PersistentActor with ActorLogging {

  /**
    * Stores a finite set of accounts' identifiers to be able to start already persisted account state in case of
    * shutdown or failure.
    */
  var accounts = Set.empty[String]

  def persistenceId = "account-manager"

  /**
    * Finds an account to process requests or creates it if it does not exist.
    * Akka restores an account state when it is created.
    * @param id specifies account identifier
    * @return account actor
    */
  private[actors] def getOrCreateAccount(id: String) =
    context.child(id) match {
      case Some(account) =>
        log.debug("Found account {}", id)
        account
      case None =>
        log.debug("Creating account {}", id)
        context.actorOf(Accounts.newAccountProps(AccountDetails(id, null, 0)), id)
    }

  def receiveRecover = {
    case AccountCreated(request, details) =>
      log.debug("Account {} created by request {}", details, request)
      accounts += details.id
  }

  def receiveCommand = {
    case CreateAccount(request, owner, initialBalance) =>
      persist(AccountCreated(request, AccountDetails(uuid, owner, initialBalance))) { created =>
        log.debug("Created account {} by request {}", created.details, request)
        accounts += created.details.id
        context.actorOf(Accounts.newAccountProps(created.details), created.details.id)
        sender ! created
      }

    case operation @ GetAccountInfo(request, account) if accounts contains account =>
      getOrCreateAccount(account) forward operation

    case GetAccountInfo(request, account) =>
      log.warning("Requested information on non-existing account {}!", account)
      sender ! AccountNotFound(request, account)

    case operation @ CreditAccount(request, account, value) if accounts contains account =>
      log.debug("Crediting account {} for {} by request {}", request, value, request)
      getOrCreateAccount(account) forward  operation

    case CreditAccount(request, account, _) =>
      log.warning("Requested crediting non-existing account {}!", account)
      sender ! AccountNotFound(request, account)

    case operation @ DebitAccount(request, account, value) if accounts contains account =>
      log.debug("Debiting account {} with {}", account, value)
      getOrCreateAccount(account) forward operation

    case DebitAccount(request, account, _) =>
      log.warning("Requested debiting non-existing account {}!", request)
      sender ! AccountNotFound(request, account)
  }
}

/**
  * Contains messages being processed related to account mamagement.
  */
object Accounts {

  /**
    * Provides properties to create an account actor.
    * @param details account related information
    * @return props instance
    */
  def newAccountProps(details: AccountDetails) = Props(classOf[Account], details)

  /**
    * Requests creating an account.
    * @param request specifies unique request identifier
    * @param owner specifies owner of an account
    * @param initialBalance specifies an account initial balance
    */
  case class CreateAccount(request: String, owner: String, initialBalance: BigDecimal)

  /**
    * Contains a response on a create account request.
    * @param request specifies unique request identifier
    * @param details current account information such as balance, owner etc
    */
  case class AccountCreated(request: String, details: AccountDetails)

  /**
    * Requests debiting an account.
    * @param request specifies unique request identifier
    * @param account specifies account identifier
    * @param value specifies amount of money
    */
  case class DebitAccount(request: String, account: String, value: BigDecimal)

  /**
    * Contains a successful response on debiting account.
    * @param request specifies unique request identifier
    * @param details current account information such as balance, owner etc
    */
  case class AccountDebited(request: String, details: AccountDetails) extends ChangeState

  /**
    * Contains an unsuccessful response on debiting account.
    * @param request specifies unique request identifier
    * @param details current account information such as balance, owner etc
    */
  case class AccountHasInsufficientFunds(request: String, details: AccountDetails)

  /**
    * Requests crediting an account.
    * TODO: introduce operation id for matching with transfer id!
    * @param request specifies unique request identifier
    * @param account specifies account identifier
    * @param value specifies amount of money
    */
  case class CreditAccount(request: String, account: String, value: BigDecimal)

  /**
    * Contains a response on a CreditAccount request.
    * @param request specifies unique request identifier
    * @param details current account information such as balance, owner etc
    */
  case class AccountCredited(request: String, details: AccountDetails) extends ChangeState

  /**
    * Requests account related information. As a response an AccountDetails instance will be sent.
    * @param request specifies unique request identifier
    * @param account specifies account identifier
    */
  case class GetAccountInfo(request: String, account: String)

  /**
    * Contains a response on account info request.
    * @param request specifies request identifier
    * @param details contains current account state
    */
  case class AccountInfo(request: String, details: AccountDetails)

  /**
    * Contains a response on any operation to be applied to non-existing account.
    * @param request specifies request identifier
    * @param account specifies account identifier
    */
  case class AccountNotFound(request: String, account: String)

  /**
    * Represents events changing account state.
    */
  trait ChangeState {
    def details: AccountDetails
  }
}
