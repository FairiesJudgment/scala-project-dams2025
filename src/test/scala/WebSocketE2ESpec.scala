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

  def httpPost(path: String, body: String): Task[(Int, String)] = {
    val client = HttpClient.newHttpClient()
    val req    = HttpRequest
      .newBuilder()
      .uri(URI.create(s"http://127.0.0.1:8080$path"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()

    def attemptSend(retries: Int): Task[(Int, String)] =
      ZIO
        .attemptBlocking {
          val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
          (resp.statusCode(), resp.body())
        }
        .catchAll { _ =>
          if (retries <= 0) ZIO.fail(new RuntimeException("httpPost failed after retries"))
          else ZIO.attemptBlocking(Thread.sleep(200)) *> attemptSend(retries - 1)
        }

    attemptSend(200)
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

  private def waitForUp(retries: Int = 100): Task[Unit] =
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
          _           <- waitForUp() *> ZIO.attemptBlocking(Thread.sleep(150))
          client       = HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build()
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
          ws          <- {
            def tryUris = List("ws://localhost:8080/stream", "ws://127.0.0.1:8080/stream", "ws://[::1]:8080/stream")

            def connectTo(uri: String, retries: Int): Task[WebSocket] =
              ZIO
                .fromCompletableFuture(
                  client.newWebSocketBuilder().buildAsync(URI.create(uri), listener)
                )
                .catchAll { err =>
                  if (retries <= 0) ZIO.fail(err)
                  else
                    ZIO.attemptBlocking(println(s"WS connect to $uri failed, retrying... ($retries) -> $err")) *>
                      ZIO.attemptBlocking(Thread.sleep(200)) *> connectTo(uri, retries - 1)
                }

            def connect(retriesPerUri: Int): Task[WebSocket] = {
              def loop(uris: List[String]): Task[WebSocket] = uris match {
                case Nil    => ZIO.fail(new RuntimeException("all WS connect attempts failed"))
                case h :: t =>
                  connectTo(h, retriesPerUri).either.flatMap {
                    case Right(ws) => ZIO.succeed(ws)
                    case Left(err) => ZIO.attemptBlocking(println(s"connect to $h failed final: $err")) *> loop(t)
                  }
              }

              loop(tryUris)
            }

            connect(200)
          }
          _           <- httpPost("/wish", "{\"title\":\"E2E\",\"details\":\"test\",\"priority\":1}")
          msg         <- ZIO.attemptBlocking(cf.get(20, java.util.concurrent.TimeUnit.SECONDS))
          _           <- ZIO.attempt(ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye"))
          _           <- serverFiber.interrupt
        } yield assert(msg)(containsString("E2E"))
      }
    )
}
