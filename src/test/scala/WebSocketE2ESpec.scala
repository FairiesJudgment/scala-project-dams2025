import zio._
import zio.test._
import zio.test.Assertion._
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, WebSocket}
import java.util.concurrent.CompletableFuture
import scala.jdk.FutureConverters._
import zio.http._

object WebSocketE2ESpec extends ZIOSpecDefault {
  import Main._

  def httpPost(path: String, body: String): Task[(Int, String)] = ZIO.attempt {
    val client = HttpClient.newHttpClient()
    val req    = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://127.0.0.1:8080$path"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    val resp   = client.send(req, HttpResponse.BodyHandlers.ofString())
    (resp.statusCode(), resp.body())
  }

  def httpGet(path: String): Task[(Int, String)] = ZIO.attempt {
    val client = HttpClient.newHttpClient()
    val req    = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://127.0.0.1:8080$path"))
      .GET()
      .build()
    val resp   = client.send(req, HttpResponse.BodyHandlers.ofString())
    (resp.statusCode(), resp.body())
  }

  private def waitForUp(retries: Int = 50): Task[Unit] =
    if (retries <= 0) ZIO.fail(new RuntimeException("server did not start"))
    else
      httpGet("/ping").either.flatMap {
        case Right((200, _)) => ZIO.unit
        case _               => ZIO.attemptBlocking(Thread.sleep(200)) *> waitForUp(retries - 1)
      }

  override def spec =
    suite("WebSocket E2E")(
      test("POST a wish and WS client receives it") {
        for {
          db          <- Ref.make(Vector.empty[Wish])
          hub         <- Hub.unbounded[Wish]
          app          = httpApp(db, hub)
          serverFiber <- Server.serve(app).provide(Server.default).fork
          _           <- waitForUp()
          client       = HttpClient.newHttpClient()
          cf           = new CompletableFuture[String]()
          listener     = new WebSocket.Listener {
                           override def onText(
                               ws: WebSocket,
                               data: CharSequence,
                               last: Boolean
                           ): java.util.concurrent.CompletionStage[_] = {
                             cf.complete(data.toString)
                             java.util.concurrent.CompletableFuture.completedFuture(null)
                           }
                         }
          ws          <- ZIO.fromCompletableFuture(
                           client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:8080/stream"), listener)
                         )
          _           <- httpPost("/wish", "{\"title\":\"E2E\",\"details\":\"test\",\"priority\":1}")
          msg         <- ZIO.attemptBlocking(cf.get(5, java.util.concurrent.TimeUnit.SECONDS))
          _           <- ZIO.attempt(ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye"))
          _           <- serverFiber.interrupt
        } yield assert(msg)(containsString("E2E"))
      }
    )
}
