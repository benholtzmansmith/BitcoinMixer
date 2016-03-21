package BitcoinMixer

import play.api.libs.json.JsResult
import com.mongodb.casbah._
import com.mongodb.DBObject
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Implicits._
/**
 * Created by benjaminsmith on 3/21/16.
 */
object MongoWrapper {
  import JsonToCaseClass.{fromJson, toJson}
  private lazy val mongoUri = MongoClientURI("mongodb://test:test@ds021299.mlab.com:21299/heroku_zmx64m97")

  private lazy val mongoClient = MongoClient(mongoUri)

  private lazy val internalAccountInfo = mongoClient("heroku_zmx64m97")("internalAccountInfo")

  def getInternalAccountInfo:List[JsResult[InternalAccountInfo]] =
    internalAccountInfo.find().map( dbo => fromJson[InternalAccountInfo](dbo.toString)).toList


  def findInternalAccountInfoByAddress(ad:String):Option[JsResult[InternalAccountInfo]] =
    internalAccountInfo.findOne(MongoDBObject("jobCoinAddress" -> ad )).map( dbo => fromJson[InternalAccountInfo](dbo.toString))

  def updateCanProcess(accountInfo: InternalAccountInfo) =
    internalAccountInfo.update(MongoDBObject("accountId" -> accountInfo.accountId), MongoDBObject("$set" -> MongoDBObject("canProcess" -> true)))

  def insertNewAccountInfo(address: InternalAccountInfo) =
    internalAccountInfo.insert(toJson[InternalAccountInfo](address))

  def updateAmount(accountInfo: InternalAccountInfo) =
    internalAccountInfo.update(MongoDBObject("accountId" -> accountInfo.accountId), MongoDBObject("$set" -> MongoDBObject("amount" -> accountInfo.amountInAccount)))
}
