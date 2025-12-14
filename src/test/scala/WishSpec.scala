import zio._
import zio.test._
import zio.test.Assertion._
import zio.stream._
import java.time.Instant

object WishSpec extends ZIOSpecDefault {
  def spec = suite("Wish tests")(
    test("JSON roundtrip for CreateWish and Wish") {
      import zio.json._
      val cw = CreateWish("Cadeau", "Un bel objet", 2)
      val json = cw.toJson
      assertTrue(json.contains("Cadeau"))
    },

    test("Ref update and read") {
      for {
        db <- Ref.make(Vector.empty[Wish])
        _  <- db.update(_ :+ Wish("T1","d",1, Instant.now.getEpochSecond))
        v  <- db.get
      } yield assert(v.size)(equalTo(1))
    },

    test("Hub publishes and stream receives (WebSocket-like)") {
      for {
        hub <- Hub.unbounded[Wish]
        w   = Wish("W","details",1, Instant.now.getEpochSecond)
        _   <- hub.publish(w)
        out <- ZStream.fromHub(hub).take(1).runCollect
      } yield assert(out.head.title)(equalTo("W"))
    }
  )
}
