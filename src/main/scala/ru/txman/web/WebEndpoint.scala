package ru.txman.web

import akka.pattern._
import ru.txman.actors._
import akka.util.Timeout
import akka.http.scaladsl.Http
import scala.concurrent.Future
import ru.txman.actors.Accounts._
import java.util.concurrent.TimeUnit
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol._
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import scala.concurrent.duration.FiniteDuration
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import ru.txman.actors.TransactionManager.{Transfer, TransferChanged}
import ru.txman.model.{AccountDetails, TransferDetails, TransferStatus}

/**
  * Starts up web infrastructure and provides integration points for accessing the application's functionality.
  * Created by dmitry on 08.06.16.
  */
class WebEndpoint(intf: String, port: Int, accountManager: ActorRef, transactionManager: ActorRef)(implicit actorSystem: ActorSystem) {
  implicit val timeout = Timeout(FiniteDuration(2, TimeUnit.SECONDS))

  implicit val materializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher

  case class DecodedTransferDetails(requestId: String, id: String, source: String, destination: String,
                                    value: BigDecimal, status: String, reason: Option[String])

  val status2str = Map(
    TransferStatus.INITIATED -> "INITIATED",
    TransferStatus.DEBITED -> "DEBITED",
    TransferStatus.FAILED -> "FAILED",
    TransferStatus.CLOSED -> "CLOSED"
  )

  def decode(transferChanged: TransferChanged) = {
    val t = transferChanged.details
    DecodedTransferDetails(transferChanged.request, t.id, t.source, t.destination, t.value, status2str(t.status), t.reason)
  }

  // data transformers
  implicit val getAccountInfoJson = jsonFormat2(GetAccountInfo)
  implicit val accountDetailsJson = jsonFormat3(AccountDetails)
  implicit val createAccountJson = jsonFormat3(CreateAccount)
  implicit val transferJson = jsonFormat4(Transfer)
  implicit val decodedTransferDetailsJson = jsonFormat7(DecodedTransferDetails)

  val accountManagement =
    pathPrefix("account" / Segment) { accountId =>
      get {
        val reqId = uuid
        onSuccess(accountManager ? GetAccountInfo(reqId, accountId)) {
          case AccountInfo(`reqId`, accountDetails) => complete(accountDetails)
          case AccountNotFound(`reqId`, `accountId`) => complete(StatusCodes.NotFound)
        }
      }
    } ~
    path("account") {
      put {
        entity(as[CreateAccount]) { ca =>
          onSuccess(accountManager ? ca) {
            case AccountCreated(ca.request, accountDetails) => complete(accountDetails)
          }
        }
      }
    }

  val transactionManagement = path("transaction") {
    put {
      entity(as[Transfer]) { it =>
        onSuccess(transactionManager ? it) {
          case tr: TransferChanged => complete(decode(tr))
        }
      }
    }
  }

  private val binding: Future[ServerBinding] =
    Http().bindAndHandle(accountManagement ~ transactionManagement, intf, port)

  def stopEndpoint: Future[Unit] = binding.flatMap(_.unbind())
}