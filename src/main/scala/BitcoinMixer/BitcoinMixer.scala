package BitcoinMixer

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json._
import spray.can.{Http => SprayHttp}
import spray.http.HttpMethods._
import spray.http._
import spray.httpx.unmarshalling.Unmarshaller
import com.mongodb.util.{ JSON => MongoJson}

import scala.concurrent.duration._
import scala.sys.process
import scala.util.{Properties, Failure, Success}
import com.mongodb.casbah._
import com.mongodb.DBObject
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Implicits._
import MongoWrapper._

/**
 * Created by benjaminsmith on 3/18/16.
 *
 * Project requirements: Build a bitcoin service that does the following
 * 1. You provide to the mixer a list of new, unused addresses that you own.
 * 2. The mixer provides you with a new deposit address that it owns.
 * 3. You transfer your bitcoins to that address.
 * 4. The mixer will detect your transfer by watching or polling the P2P Bitcoin network.
 * 5. The mixer will transfer your bitcoins from the deposit address into a big “house account” along with all the other bitcoins currently being mixed.
 * 6. Then, over some time the mixer will use the house account to dole out your bitcoins in smaller increments to the withdrawal addresses that you provided, possibly after deducting a fee.
 *
 */


object BitcoinMixer {
  import Scheduler.scheduleEvery
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()

    val service = system.actorOf(Props[MixerServiceActor], name = "mixer")

    implicit val timeout = Timeout(20.seconds)

    val myPort = Properties.envOrElse("PORT", "8080").toInt

    println("Starting server!")

    scheduleEvery(2.minutes)(PollJobcoin.moveToHouseAccount)

    scheduleEvery(2.minutes)(PollJobcoin.distributeToWithdrawalAccounts)
    
    IO(SprayHttp) ? SprayHttp.Bind(service, interface = "localhost", port = myPort)
  }
}


object PollJobcoin {
  import JobcoinApi._

  def moveToHouseAccount:Unit = {
    getInternalAccountInfo.
      collect{ case JsSuccess(a, _) => a}.
      foreach( accountInfo => {
        for {
          newBalance <- getBalanceAndListOfTransactions(accountInfo.jobCoinAddress).asOpt.map(_.balance.toInt)
          hasChangeOccured = !(newBalance == accountInfo.amountInAccount)
          if hasChangeOccured
        } yield {
          postTransaction(Transaction(accountInfo.jobCoinAddress, houseAddress, newBalance.toString))
          updateAmount(accountInfo.copy(amountInAccount = newBalance.toInt))
          updateCanProcess(accountInfo.copy(canProcess = true))
        }
    })
  }
  
  def distributeToWithdrawalAccounts:Unit = {
    getInternalAccountInfo.
      collect{ case JsSuccess(a, _) => a}.
      foreach{ accountInfo => {
      if (accountInfo.canProcess){
        val someEvenAmount = accountInfo.amountInAccount.toDouble / accountInfo.userOwnedAddresses.length

        accountInfo.userOwnedAddresses.map(a => postTransaction(Transaction(houseAddress, a, someEvenAmount.toString)))
      }
    }}
  }
}

class MixerServiceActor extends Actor {
  import JobcoinApi._
  import MongoWrapper._
  import Views._
  def actorRefFactory = context
  def receive = {
    case _: SprayHttp.Connected => sender ! SprayHttp.Register(self)
    case HttpRequest(GET, Uri.Path("/"), _, _, _) => sender ! index
    case HttpRequest(POST, Uri.Path("/input-addresses"), headers, entity: HttpEntity.NonEmpty, protocol) => {
      val ad = Unmarshaller.unmarshal[Addresses](entity).
        fold(_ => Addresses(Nil), identity(_)).
        addresses

      val possibleError = ad.
        map(getBalanceAndListOfTransactions).
        find(_.isError)
      possibleError match {
        case Some(err) => sender ! failure
        case None => {
          val newId = JobcoinApi.newId
          generateNewAddress(newId) match {
            case Success( _ ) => {
              insertNewAccountInfo(InternalAccountInfo(newId, newId, ad, 0, false))
              sender ! newAddress(newId)
            }
            case Failure( _ ) => sender ! failure
          }
        }
      }
      sender ! index
    }
    case HttpRequest(POST, Uri.Path("/transfer-coins"), headers, entity: HttpEntity.NonEmpty, protocol) => {
      Unmarshaller.unmarshal[Transaction](entity) match {
        case Right(transaction) if transaction.fromAddress != houseAddress =>
          postTransaction(transaction) match {
            case Success( _ ) => sender ! success
            case Failure ( _ ) => sender ! failure
          }
        case Left( _ ) => sender ! failure
      }
    }
    case HttpRequest(POST, Uri.Path("/make-new-addresses"), headers, entity: HttpEntity.NonEmpty, protocol) => {
      Unmarshaller.unmarshal[Addresses](entity).
        fold(_ => Addresses(Nil), identity(_)).
        addresses.
        map(generateNewAddress).find( _.isFailure) match {
          case Some( _ ) => sender ! failure
          case None => sender ! success
        }
    }
  }
}

object JsonToCaseClass{
  def fromJson[T: Reads](jsonString:String):JsResult[T] =
    Json.fromJson[T](Json.parse(jsonString))

  def toJson[T : Writes](t: T): DBObject =
    MongoJson.parse(Json.stringify(Json.toJson(t))).asInstanceOf[DBObject]

}

object Scheduler {
  def scheduleEvery[T](frequency: FiniteDuration)(task: => T)(implicit system: ActorSystem) = {
    import system.dispatcher

    system.scheduler.schedule(0.second, frequency)(task)
  }
}