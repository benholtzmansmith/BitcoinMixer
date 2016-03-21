package BitcoinMixer

import play.api.libs.json.{Json, Format}
import spray.http.{MediaTypes, HttpEntity}
import spray.httpx.unmarshalling._
import UrlEncoded.urlEncoded

/**
 * Created by benjaminsmith on 3/21/16.
 */

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

case class InternalAccountInfo(accountId:String, jobCoinAddress:String, userOwnedAddresses:List[String], amountInAccount:Int, canProcess:Boolean)
object InternalAccountInfo{
  implicit val addressFormat:Format[InternalAccountInfo] = Json.format[InternalAccountInfo]
}

object UrlEncoded {
  val urlEncoded = MediaTypes.`application/x-www-form-urlencoded`
}
