package BitcoinMixer

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.{JsResult, Json, Reads}
import spray.can.{Http => SprayHttp}
import spray.http.HttpMethods._
import spray.http._
import spray.httpx.unmarshalling.Unmarshaller

import scala.concurrent.duration._
import scala.util.{Failure, Success}


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

    println("Starting server!")

    scheduleEvery(10.seconds)(())

    IO(SprayHttp) ? SprayHttp.Bind(service, interface = "localhost", port = 8080)
  }
}


class MixerServiceActor extends Actor {
  import JobcoinApi._
  import Views._
  def actorRefFactory = context
  def receive = {
    case _: SprayHttp.Connected => sender ! SprayHttp.Register(self)
    case HttpRequest(GET, Uri.Path("/"), _, _, _) => sender ! index
    case HttpRequest(POST, Uri.Path("/input-addresses"), headers, entity: HttpEntity.NonEmpty, protocol) => {
      val possibleError = Unmarshaller.unmarshal[Addresses](entity).
        fold(_ => Addresses(Nil), identity(_)).
        addresses.
        map(getBalanceAndListOfTransactions).
        find(_.isError)
      possibleError match {
        case Some(err) => sender ! failure
        case None => {
          val newId = JobcoinApi.newId
          generateNewAddress(newId) match {
            case Success( _ ) => sender ! newAddress(newId)
            case Failure( _ ) => sender ! failure
          }
        }
      }
      sender ! index
    }
    case HttpRequest(POST, Uri.Path("/transfer-coins"), headers, entity: HttpEntity.NonEmpty, protocol) => {
      Unmarshaller.unmarshal[Transaction](entity) match {
        case Right(transaction) if transaction.fromAddress != "house" =>
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
  def fromJson[T: Reads](jsonString:String):JsResult[T] = {
    Json.fromJson[T](Json.parse(jsonString))
  }
}

object Scheduler {
  def scheduleEvery[T](frequency: FiniteDuration)(task: => T)(implicit system: ActorSystem) = {
    import system.dispatcher

    system.scheduler.schedule(0.second, frequency)(task)
  }
}