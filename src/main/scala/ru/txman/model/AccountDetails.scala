package ru.txman.model

/**
  * Contains account related information
  * Created by dmitry on 04.06.16.
  * @param id unique account identifier
  * @param owner owner description of an account
  * @param balance current account balance in imaginary currency
  */
case class AccountDetails(id: String, owner: String, balance: BigDecimal)

/**
  * Contains all information related to money transfer between two accounts.
  * @param id specifies unique transaction identifier
  * @param source source account identifier
  * @param destination destination account identifier
  * @param value value to be transferred
  */
case class TransferDetails(id: String, source: String, destination: String,
                           value: BigDecimal, status: TransferStatus.Value = TransferStatus.INITIATED,
                           reason: Option[String] = None)

/**
  * Contains all possible a transfer statuses.
  */
object TransferStatus extends Enumeration {
  val INITIATED = Value("Transfer has been initiated")
  val DEBITED = Value("Account has been debited")
  val FAILED = Value("Transfer has been failed")
  val CLOSED = Value("Transfer has been closed")
}