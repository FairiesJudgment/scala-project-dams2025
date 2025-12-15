import zio._
import zio.http._
import zio.http.ChannelEvent.Read
import zio.http.Middleware
import zio.json._
import zio.stream._
import java.time.Instant

// Les modèles de données pour la Wish-list
case class CreateWish(title: String, details: String, priority: Int = 1)
object CreateWish {
  implicit val codec: JsonCodec[CreateWish] = DeriveJsonCodec.gen
}

case class Wish(title: String, details: String, priority: Int, timestamp: Long)
object Wish {
  implicit val codec: JsonCodec[Wish] = DeriveJsonCodec.gen
}

//L'appli principale
object Main extends ZIOAppDefault {

  private def jsonError(msg: String, status: Status = Status.BadRequest): Response =
    Response.json(s"{\"error\":\"${msg.replaceAll("\"", "\\\"")}\"}")

  def run =
    for {
      // Ensure http.port is set from system property or application.conf resource
      _   <- ZIO.attempt {
               val prop = sys.props.get("http.port")
               if (prop.isEmpty) {
                 val is = Option(getClass.getResourceAsStream("/application.conf"))
                 is.foreach { stream =>
                   val s     = scala.io.Source.fromInputStream(stream).mkString
                   stream.close()
                   val regex = """port\s*=\s*(\d+)""".r
                   regex.findFirstMatchIn(s).foreach(m => java.lang.System.setProperty("http.port", m.group(1)))
                 }
               }
             }
      db  <- Ref.make(Vector(Wish("Premiere idée", "Un exemple de souhait", 1, Instant.now.getEpochSecond)))
      hub <- Hub.unbounded[Wish]
      app  = httpApp(db, hub)

      _ <- Server.serve(app).provide(Server.default)
    } yield ()

  def routes(db: Ref[Vector[Wish]], hub: Hub[Wish]) = Routes(
    // ping-pong (check que le serveur tourne)
    Method.GET / "ping" -> handler(Response.text("pong")),

    // obtenir un wish aléatoire de la bdd
    Method.GET / "random" -> handler {
      for {
        items <- db.get
        res   <- if (items.isEmpty) ZIO.succeed(jsonError("no wishes available", Status.NotFound))
                 else Random.nextIntBounded(items.size).map(items(_)).map(w => Response.json(w.toJson))
      } yield res
    },

    // creer un nouveau secret et le mettre dans la bdd
    Method.POST / "wish" -> handler { (req: Request) =>
      req.body.asString.flatMap { body =>
        body.fromJson[CreateWish] match {
          case Left(err)    => ZIO.succeed(jsonError(err, Status.BadRequest))
          case Right(input) =>
            val newWish = Wish(input.title, input.details, input.priority, Instant.now.getEpochSecond)
            for {
              _ <- db.update(_ :+ newWish)
              _ <- hub.publish(newWish)
            } yield Response.status(Status.Created)
        }
      }
    },

    // Lire les noms
    Method.GET / "wishes" -> handler {
      db.get.map(ws => Response.json(ws.toJson))
    },

    // lire un nom par index
    Method.GET / "wish" / int("index") -> handler { (index: Int, _: Request) =>
      db.get.map { ws =>
        if (index < 0 || index >= ws.size)
          jsonError(s"index $index not found", Status.NotFound)
        else
          Response.json(ws(index).toJson)
      }
    },

    // changer un nom par index
    Method.PUT / "wish" / int("index") -> handler { (index: Int, req: Request) =>
      req.body.asString.flatMap { body =>
        body.fromJson[CreateWish] match {
          case Left(err)    =>
            ZIO.succeed(jsonError(err, Status.BadRequest))
          case Right(input) =>
            db.modify { ws =>
              if (index < 0 || index >= ws.size) (jsonError(s"index $index not found", Status.NotFound), ws)
              else {
                val updated = ws.updated(
                  index,
                  ws(index).copy(title = input.title, details = input.details, priority = input.priority)
                )
                (Response.text("Wish updated"), updated)
              }
            }
        }
      }
    },

    // enlever un nom par index
    Method.DELETE / "wish" / int("index") -> handler { (index: Int, _: Request) =>
      db.modify { ws =>
        if (index < 0 || index >= ws.size)
          (jsonError(s"index $index not found", Status.NotFound), ws)
        else
          (Response.text("Wish deleted"), ws.patch(index, Nil, 1))
      }
    },

    // la WBS pour streamer les messages (nouveaux wishes)
    Method.GET / "stream" -> handler {
      val socket = Handler.webSocket { channel =>
        ZStream
          .fromHub(hub)
          .map(w => WebSocketFrame.text(w.toJson))
          .map(frame => Read(frame))
          .foreach(channel.send)
      }
      socket.toResponse
    }
  )

  // Expose an HttpApp for tests and alternate server configs
  def httpApp(db: Ref[Vector[Wish]], hub: Hub[Wish]): HttpApp[Any] =
    (routes(db, hub) @@ Middleware.cors)
      .handleError(e => Response.internalServerError(s"Erreur: ${e.getMessage}"))
      .toHttpApp
}
