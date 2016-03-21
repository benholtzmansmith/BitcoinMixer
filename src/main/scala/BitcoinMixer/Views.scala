package BitcoinMixer

import spray.http.MediaTypes._
import spray.http.{HttpEntity, HttpResponse}

/**
 * Created by benjaminsmith on 3/21/16.
 */
object Views {

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

              Use the below forms to operate the mixer.
              Any bad request will return a failure.
              <br></br>
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
