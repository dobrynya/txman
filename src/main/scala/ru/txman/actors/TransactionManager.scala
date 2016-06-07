package ru.txman.actors

import akka.actor._
import ru.txman.model._
import TransactionManager._
import akka.persistence.PersistentActor

/**
  * Provides operations to transfer money between accounts.
  * Created by dmitry on 04.06.16.
  */
class TransactionManager(accountManager: ActorRef) extends PersistentActor with ActorLogging {
  var transfers = Map.empty[String, (TransferDetails, Option[ActorRef])]

  def persistenceId = "transaction-manager"

  def receiveRecover = {
    case TransferChanged(request, details) if List(TransferStatus.FAILED, TransferStatus.CLOSED) contains details.status =>
      transfers -= request
    case TransferChanged(request, details) =>
      transfers += request -> (details, None)
  }

  def receiveCommand = {
    case Transfer(request, source, destination, value) if !transfers.contains(request) =>
      val transfer = TransferDetails(request, source, destination, value, TransferStatus.INITIATED)
      persist(TransferChanged(request, transfer)) { changed =>
        log.debug("Initiated transfer between {} and {} for {} by request {}", source, destination, value, request)
        transfers += changed.details.id -> (changed.details, Some(sender))
        accountManager ! Accounts.DebitAccount(request, source, value)
        sender ! changed
      }

    case Accounts.AccountNotFound(request, accountId) =>
      transfers.get(request) match {
        case Some((transfer, requestor)) if transfer.status == TransferStatus.INITIATED =>
          val failed = transfer.copy(status = TransferStatus.FAILED,
            reason = Some(s"Source account ${transfer.source} is not found"))

          persist(TransferChanged(request, failed)) { changed =>
            transfers -= request
            requestor.foreach(_ ! changed)
            log.debug("Transaction {} failed due to account {} is not found!", transfer.id, transfer.source)
          }

        case Some((transfer, requestor)) if transfer.status == TransferStatus.DEBITED =>
          val failed = transfer.copy(status = TransferStatus.FAILED,
            reason = Some(s"Destination account ${transfer.destination} is not found"))

          persist(TransferChanged(request, failed)) { changed =>
            transfers -= request
            requestor.foreach(_ ! changed)
            // rollback debit operation
            accountManager ! Accounts.CreditAccount(request, transfer.source, transfer.value)
            log.debug("Transaction {} failed due to account {} is not found!", transfer.id, transfer.source)
          }
      }

    case Accounts.AccountDebited(request, accountDetails) =>
      transfers.get(request) match {
        case Some((transfer, requestor)) =>
          val debited = transfer.copy(status = TransferStatus.DEBITED)
          persist(TransferChanged(request, debited)) { changed =>
            transfers += request -> (debited, requestor)
            accountManager ! Accounts.CreditAccount(request, transfer.destination, transfer.value)
            log.debug("Transaction {} switched to debited status, requested to credit", transfer.id)
          }
        case None =>
          log.warning("Received unexpected debit on {} for request {}!", accountDetails.id, request)
      }

    case Accounts.AccountHasInsufficientFunds(request, accountDetails) if transfers contains request =>
      transfers.get(request) match {
        case Some((transfer, requestor)) =>
          val failed = transfer.copy(status = TransferStatus.FAILED,
            reason = Some(s"Account ${transfer.source} has insufficient funds"))

          persist(TransferChanged(request, failed)) { changed =>
            transfers -= request
            requestor.foreach(_ ! changed)
            log.debug("Transaction {} failed due to account {} has insufficient funds!", request, transfer.source)
          }
        case None =>
          log.warning("Received unexpected insufficient funds on {} for request {}!", accountDetails.id, request)
      }

    case Accounts.AccountCredited(request, accountDetails) =>
      transfers.get(request) match {
        case Some((transfer, requestor)) =>
          val closed = transfer.copy(status = TransferStatus.CLOSED)
          persist(TransferChanged(request, closed)) { changed =>
            transfers -= request
            requestor.foreach(_ ! changed)
            log.debug("Transaction {} completed successfully!", request, transfer.source)
          }
        case None =>
          log.warning("Received unexpected credit on {} for request {}!", accountDetails.id, request)
      }
  }
}

object TransactionManager {

  /**
    * Requests money transfer between accounts.
    * @param request specifies unique request
    * @param source specifies source account identifier
    * @param destination specifies destination account identifier
    * @param value specifies amount of money to be transferred
    */
  case class Transfer(request: String, source: String, destination: String, value: BigDecimal) {
    require(value > 0, "Transfer value should be positive!")
  }

  /**
    * Notifies of every change of a money transfer during its life cycle.
    * @param request specifies unique request
    * @param details contains information related to money transfer
    */
  case class TransferChanged(request: String, details: TransferDetails)
}
