package ru.txman.services

import com.typesafe.config.Config
import slick.driver.H2Driver.api._
import ru.txman.model.AccountDetails
import scala.concurrent.{ExecutionContext, Future}

/**
  * Usual database access object. Provides operations to store and retrieve data.
  * Created by dmitry on 07.06.16.
  */
trait Dao[T] {

  /**
    * Provides DAO with DB configuration.
    * @return
    */
  def config: Config

  /**
    * Provides database configuration element in the supplied configuration.
    * @return database configuration element
    */
  def databaseName: String

  /**
    * Database to use for running requests.
    */
  lazy val dataBase = Database.forConfig(databaseName, config)
}


trait AccountDao extends Dao[AccountDetails] {
  implicit def executionContext: ExecutionContext

  class AccountsTable(tag: Tag) extends Table[AccountDetails](tag, "ACCOUNTS") {
    def id = column[String]("ID", O.PrimaryKey)
    def owner = column[String]("OWNER")
    def initialBalance = column[BigDecimal]("balance")
    def * = (id, owner, initialBalance) <> (AccountDetails.tupled, AccountDetails.unapply)
  }

  val accountDetails = TableQuery[AccountsTable]

  def createTable: Future[Unit] = dataBase.run(accountDetails.schema.create)

  /**
    * Creates an account or updates its if it exists in a database.
    * @param details account to be created or updated
    * @return successfully persisted account or none if persistence is failed
    */
  def createOrUpdateAccount(details: AccountDetails): Future[Option[AccountDetails]] =
    dataBase.run(accountDetails.insertOrUpdate(details)).map {
      case 1 => Some(details)
      case _ => None
    }

  /**
    * Finds all accounts.
    * @return persisted accounts.
    */
  def findAll: Future[Seq[AccountDetails]] = findMatching(None, None)

  /**
    * Finds matching accounts by identifier or owner
    * @param id specifies account identifier
    * @param owner specifies account owner
    * @return found accounts
    */
  def findMatching(id: Option[String], owner: Option[String]) =
    dataBase.run(accountDetails.filter { account =>
      List(
        id.map(account.id === _),
        owner.map(account.owner === _)
      ).collect({case Some(criteria)  => criteria}).reduceLeftOption(_ && _).getOrElse(true: Rep[Boolean])
    }.result)
}