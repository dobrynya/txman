package ru.txman.actors

import akka.actor._
import ru.txman.actors.Accounts._
import ru.txman.services.AccountDao
import akka.persistence.PersistentView

/**
  * Provides functionality to persist data in a JDBC database.
  * Created by dmitry on 08.06.16.
  */
class AccountDaoService extends Actor with PersistentView with AccountDao {
  implicit def executionContext = context.dispatcher

  def viewId: String = "account-manager-jdbc-persister"

  def persistenceId: String = "account-manager"

  def receive = {
    case AccountCreated(request, account) =>
      createOrUpdateAccount(account).foreach {
        case Some(saved) =>
          log.debug("Account {} is saved", saved)
        case None =>
          log.warning("Account {} is not saved!", account)
      }
  }

  def config = context.system.settings.config

  def databaseName: String = "txman-database"
}
