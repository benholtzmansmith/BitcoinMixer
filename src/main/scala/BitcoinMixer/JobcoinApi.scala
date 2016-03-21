package BitcoinMixer

import java.util.UUID

import play.api.libs.json.{JsResult, Json}

import scala.util.{Failure, Success, Try}
import scalaj.http.Http

import JsonToCaseClass.fromJson

/**
 * Created by benjaminsmith on 3/21/16.
 */
object JobcoinApi {

  val houseAddress:String = "house"

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

