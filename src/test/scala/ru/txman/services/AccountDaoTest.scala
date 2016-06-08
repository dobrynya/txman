package ru.txman.services

import org.scalatest._
import ru.txman.model._
import ru.txman.actors._
import scala.concurrent._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.Duration

/**
  * Specification on AccountDao.
  * Created by dmitry on 08.06.16.
  */
class AccountDaoTest extends FlatSpec with Matchers with AccountDao with BeforeAndAfterAll {
  behavior of "AccountDao"

  implicit def executionContext: ExecutionContext = new ExecutionContext {
    def reportFailure(cause: Throwable): Unit = throw cause

    def execute(runnable: Runnable): Unit = runnable.run()
  }

  val frodoAccount = AccountDetails(uuid, "Frodo Baggins", 5000)

  it should "successfully create a table for accounts" in {
    Await.result(createTable, Duration.Inf) should matchPattern {
      case () =>
    }
  }

  it should "find nothing in case of empty table" in {
    Await.result(findAll, Duration.Inf) should matchPattern {
      case Seq() =>
    }
  }

  it should "successfully create a new account" in {
    Await.result(createOrUpdateAccount(frodoAccount), Duration.Inf) should matchPattern {
      case Some(`frodoAccount`) =>
    }
  }

  it should "find all account" in {
    Await.result(findAll, Duration.Inf) should matchPattern {
      case Seq(`frodoAccount`) =>
    }
  }

  it should "not find non-matching accounts" in {
    Await.result(findMatching(Some("non-existing-account"), None), Duration.Inf) should matchPattern {
      case Seq() =>
    }

    Await.result(findMatching(Some(frodoAccount.id), Some("Wrong owner")), Duration.Inf) should matchPattern {
      case Seq() =>
    }

    Await.result(findMatching(Some("non-existing-account"), Some(frodoAccount.owner)), Duration.Inf) should matchPattern {
      case Seq() =>
    }
  }

  it should "find matching account" in {
    Await.result(findMatching(Some(frodoAccount.id), None), Duration.Inf) should matchPattern {
      case Seq(`frodoAccount`) =>
    }

    Await.result(findMatching(None, Some(frodoAccount.owner)), Duration.Inf) should matchPattern {
      case Seq(`frodoAccount`) =>
    }

    Await.result(findMatching(Some(frodoAccount.id), Some(frodoAccount.owner)), Duration.Inf) should matchPattern {
      case Seq(`frodoAccount`) =>
    }
  }

  it should "successfully update existing account" in {
    val updated = frodoAccount.copy(balance = 7000)
    Await.result(createOrUpdateAccount(updated), Duration.Inf) should matchPattern {
      case Some(`updated`) =>
    }
    Await.result(findAll, Duration.Inf) should matchPattern {
      case Seq(`updated`) =>
    }
  }

  val config = ConfigFactory.load()
  val databaseName = "txman-h2-database"
}
