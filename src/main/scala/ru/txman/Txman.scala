package ru.txman

import akka.actor._
import scala.io.StdIn
import ru.txman.actors._
import scala.concurrent.Future
import ru.txman.web.WebEndpoint

/**
  * Provides ability to configure application properly.
  * Created by dmitry on 08.06.16.
  */
object Txman extends App {

  implicit val actorSystem = ActorSystem("TxMan")
  implicit val ec = actorSystem.dispatcher

  val accountManager = actorSystem.actorOf(Props[AccountManager], "account-manager")

  val accountManagerViewer = actorSystem.actorOf(Props[AccountDaoService], "account-manager-viewer")

  val transactionManager = actorSystem.actorOf(Props(classOf[TransactionManager], accountManager), "transaction-manager")

  val endpoint = new WebEndpoint("localhost", 8080, accountManager, transactionManager)

  Future {
    println("\n\nPress enter to stop server...\n\n")
    StdIn.readLine()
    endpoint.stopEndpoint.foreach(_ => actorSystem.terminate().foreach(println))
  }
}
