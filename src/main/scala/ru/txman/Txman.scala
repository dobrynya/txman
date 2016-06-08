package ru.txman

import akka.actor._
import ru.txman.actors.Accounts.CreateAccount
import ru.txman.actors.{AccountDaoService, AccountManager, TransactionManager}

/**
  * Provides ability to configure application properly.
  * Created by dmitry on 08.06.16.
  */
object Txman extends App {

  val actorSystem = ActorSystem("TxMan")

  val accountManager = actorSystem.actorOf(Props[AccountManager], "account-manager")

  val accountManagerViewer = actorSystem.actorOf(Props[AccountDaoService], "account-manager-viewer")

  val transactionManager = actorSystem.actorOf(Props(classOf[TransactionManager], accountManager), "transaction-manager")

  accountManager ! CreateAccount("newRequest", "Sauron", 1000000)
}
