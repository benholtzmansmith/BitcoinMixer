package BitcoinMixer

import java.util.UUID

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsResult, Reads, Format, Json}
import play.api.libs.json
import JsonToCaseClass.fromJson
import scala.util.Try
import scalaj.http.Http

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
object BitcoinMixerRun {
  def main(args:Array[String]):Unit = {

  }
}

object BitcoinMixer{
  def getAddresses(addresses:List[String]):String = {
    val validateAddresses = Try{addresses.map(JobcoinApi.getBalanceAndListOfTransactions)}
    UUID.randomUUID().toString
  }

//  def validateAddress(address:String):Try[] ={
//    Try{JobcoinApi.getBalanceAndListOfTransactions(address)}
//  }

  def transferBitCoins(address:String, amount:Double) = ???

}


object JobcoinApi {
  def post(fromAddress:String, toAddress:String, amount:String) = {
    val transaction = Json.toJson(Transaction(fromAddress, toAddress, amount)).toString()
    Http("http://jobcoin.projecticeland.net/tractarian/api/transactions").
      postData(transaction).
      header("Content-Type", "application/json").
      header("Charset", "UTF-8").
      asString.
      code
  }

  def getAllTransactions = {
    Http("http://jobcoin.projecticeland.net/tractarian/api/transactions").
      asString.
      body
  }

  def getBalanceAndListOfTransactions(address:String) = {
    val transactions =
      Http(s"http://jobcoin.projecticeland.net/tractarian/api/addresses/$address").
        asString.
        body
    fromJson[Account](transactions)
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
}

case class TransactionWithDateTime(
                                    timestamp: DateTime,
                                    fromAddress:String,
                                    toAddress:String,
                                    amount:String
                                    )
object TransactionWithDateTime{
  implicit val transactionWithDateTimeFormat:Format[TransactionWithDateTime] = Json.format[TransactionWithDateTime]
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  val jodaDateReads = Reads[DateTime](js =>
    js.validate[String].map[DateTime](dtString =>
      DateTime.parse(dtString, DateTimeFormat.forPattern(dateFormat))
    )
  )

}

case class Account(balance:String, transactions:List[TransactionWithDateTime])
object Account{
  implicit val accountFormat:Format[Account] = Json.format[Account]
}