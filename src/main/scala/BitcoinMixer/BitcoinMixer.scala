package BitcoinMixer

import java.util.UUID
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsResult, Reads, Format, Json}
import play.api.libs.json
import JsonToCaseClass.fromJson
import spray.http._
import spray.util._
import spray.httpx.unmarshalling.{FormDataUnmarshallers, Unmarshaller}
import spray.routing.HttpService
import scala.util.{Success, Failure, Try}
import scala.util.matching.Regex
import scalaj.http.Http
import akka.actor.{Actor, ActorSystem, Props}
import akka.io.IO
import spray.can.{ Http => SprayHttp}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import MediaTypes._
import HttpMethods._
import UrlEncoded.urlEncoded
import Scheduler.scheduleEvery


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

  def newAddress(newAddress:String) = HttpResponse( entity =
    HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Successful processing</h1>
          <br></br>
          Here is your new address:{newAddress}
          <a href="/"> Go back</a>
        </body>s
      </html>.toString()
    )
  )

  lazy val success = HttpResponse( entity =
    HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Successful processing</h1>
          <a href="/"> Go back</a>
        </body>
      </html>.toString()
    )
  )

  lazy val failure = HttpResponse( entity =
    HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Failed processing</h1>
          <a href="/"> Go back</a>
        </body>
      </html>.toString()
    )
  )

  lazy val index =
    HttpResponse( entity =
      HttpEntity(`text/html`,
        <html>
          <body>
            <h1>Bitcoin Mixer</h1>
            <div>

              Use the below forms to operate the mixer. Any bad request will return a failure.

              <form action="/make-new-addresses" method="post">
                Submit space separated new addresses to make that you will now own: <input type="text" name="addresses"></input>
                <input type="submit"></input>
              </form>

              <form action ="/input-addresses" method="post">
                Submit your new addresses separated by a space:  <input type="text" name="addresses"></input>
                <input type="submit"></input>
              </form>

              <form action="/transfer-coins" method="post">
                Transfer coins from an address you own to another one. To complete mixing, transer to the house address.

                From Address: <input type="text" name="fromAddress"></input>
                To Address: <input type="text" name="toAddress"></input>
                Amount: <input type="text" name="amount"></input>
                <input type="submit"></input>
              </form>

            </div>
          </body>
        </html>.toString()
      )
    )
}


object JobcoinApi {
  def postTransaction(transaction: Transaction):Try[Unit] = {
    val result = Http("http://jobcoin.projecticeland.net/tractarian/api/transactions").
      postData(Json.toJson(transaction).toString()).
      header("Content-Type", "application/json").
      header("Charset", "UTF-8").
      asString.
      code
    if (result == 200) Success(())
    else Failure(new Throwable("non 200 return code"))
  }

  def getAllTransactions:String = {
    Http("http://jobcoin.projecticeland.net/tractarian/api/transactions").
      asString.
      body
  }

  def getBalanceAndListOfTransactions(address:String):JsResult[Account] = {
    val transactions =
      Http(s"http://jobcoin.projecticeland.net/tractarian/api/addresses/$address").
        asString.
        body
    fromJson[Account](transactions)
  }

  def newId:String = UUID.randomUUID().toString

  def generateNewAddress(addressString: String):Try[Unit] = {
    //This is the only way to make a new account through the API that I could think of.
    postTransaction(Transaction("house", addressString, "1"))
    postTransaction(Transaction(addressString, "house", "1"))
  }
}

object JsonToCaseClass{
  def fromJson[T: Reads](jsonString:String):JsResult[T] = {
    Json.fromJson[T](Json.parse(jsonString))
  }
}

case class Transaction(fromAddress:String, toAddress:String, amount:String)
object Transaction{
  implicit val transactionFormat:Format[Transaction] = Json.format[Transaction]
  
  implicit val transactionUnmarshaller:Unmarshaller[Transaction] = Unmarshaller[Transaction](urlEncoded){
    case HttpEntity.NonEmpty(contentType, data) =>

      val List(fromAddress, toAddress, amount) = data.asString.split("&").toList.map(_.split("=")(1))

      Transaction(fromAddress = fromAddress, toAddress = toAddress, amount = amount)
  }

}

case class TransactionWithDateTime(
                                    timestamp: String,
                                    fromAddress:String,
                                    toAddress:String,
                                    amount:String
                                    )
object TransactionWithDateTime{
  implicit val transactionWithDateTimeFormat:Format[TransactionWithDateTime] = Json.format[TransactionWithDateTime]
}

case class Account(balance:String, transactions:List[TransactionWithDateTime])
object Account{
  implicit val accountFormat:Format[Account] = Json.format[Account]
}

case class Addresses(addresses:List[String])
object Addresses{
  implicit val addressUnmarshaller:Unmarshaller[Addresses] = Unmarshaller[Addresses](urlEncoded){
    case HttpEntity.NonEmpty(contentType, data) =>
      Addresses(data.asString.replaceAll("addresses=", "").split("\\+").toList)
    case HttpEntity.Empty => Addresses(Nil)
  }
}

object UrlEncoded {
  val urlEncoded = MediaTypes.`application/x-www-form-urlencoded`
}

object Scheduler {
  def scheduleEvery[T](frequency: FiniteDuration)(task: => T)(implicit system: ActorSystem) = {
    import system.dispatcher

    system.scheduler.schedule(0.second, frequency)(task)
  }
}