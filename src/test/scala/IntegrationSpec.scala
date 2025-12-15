import zio._
import zio.test._
import zio.test.Assertion._
import zio.http._
import java.net.{HttpURLConnection, URL}

object IntegrationSpec extends ZIOSpecDefault {
  import Main._

  def httpGet(path: String): Task[(Int, String)] = ZIO.attempt {
    val u    = new URL(s"http://localhost:8080$path")
    val conn = u.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")
    conn.setConnectTimeout(2000)
    conn.setReadTimeout(2000)
    conn.connect()
    val code = conn.getResponseCode
    val is   = if (code >= 400) conn.getErrorStream else conn.getInputStream
    val body = if (is != null) scala.io.Source.fromInputStream(is).mkString else ""
    conn.disconnect()
    (code, body)
  }

  private def waitForUp(retries: Int = 20): Task[Unit] =
    if (retries <= 0) ZIO.fail(new RuntimeException("server did not start"))
    else
      httpGet("/ping").either.flatMap {
        case Right((200, _)) => ZIO.unit
        case _               => ZIO.attemptBlocking(Thread.sleep(200)) *> waitForUp(retries - 1)
      }

  override def spec =
    suite("Integration tests")(
      test("server responds to /ping and /wishes") {
        for {
          db          <- Ref.make(Vector.empty[Wish])
          hub         <- Hub.unbounded[Wish]
          app          = httpApp(db, hub)
          serverFiber <- Server.serve(app).provide(Server.default).fork
          _           <- waitForUp()
          ping        <- httpGet("/ping")
          wishes      <- httpGet("/wishes")
          _           <- serverFiber.interrupt
        } yield assert(ping._1)(equalTo(200)) && assert(ping._2)(containsString("pong")) && assert(wishes._1)(
          equalTo(200)
        )
      }
    )
}
