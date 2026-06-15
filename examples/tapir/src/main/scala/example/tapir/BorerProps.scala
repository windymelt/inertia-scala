package example.tapir

import dev.capslock.inertia.core.JsonObject
import io.bullet.borer.{Encoder, Json}
import io.bullet.borer.Dom
import io.bullet.borer.Dom.*

/** Props type backed by borer DOM elements.
  * Demonstrates that inertia-core's JsonObject typeclass works with any JSON backend.
  */
object BorerProps:

  type Props = Map[String, Element]

  given JsonObject[Props] with
    def empty: Props = Map.empty

    def merge(base: Props, overlay: Props): Props = base ++ overlay

    def filterKeys(p: Props, only: Set[String], except: Set[String]): Props =
      val afterOnly = if only.nonEmpty then p.filter((k, _) => only.contains(k)) else p
      afterOnly.filterNot((k, _) => except.contains(k))

    def errors(messages: Map[String, String], errorBag: Option[String]): Props =
      val value: Element =
        if messages.isEmpty then prop(Map.empty[String, String])
        else errorBag match
          case Some(bag) => prop(Map(bag -> messages))
          case None      => prop(messages)
      Map("errors" -> value)

    def toJsonObjectString(p: Props): String =
      if p.isEmpty then "{}"
      else
        val entries = p.map { (k, v) =>
          val keyJson = Json.encode(k).toUtf8String
          val valJson = Json.encode(v).toUtf8String
          s"$keyJson:$valJson"
        }
        entries.mkString("{", ",", "}")

  object Props:
    def of(entries: (String, Element)*): Props = Map(entries*)

  // Helpers for building prop values
  def str(s: String): Element              = StringElem(s)
  def int(i: Int): Element                 = IntElem(i)
  def long(l: Long): Element               = LongElem(l)
  def double(d: Double): Element            = DoubleElem(d)
  def bool(b: Boolean): Element             = BooleanElem(b)
  val nil: Element                          = NullElem

  /** Serialize any borer-encodable value to a DOM Element via JSON roundtrip. */
  def prop[A: Encoder](a: A): Element =
    val bytes = Json.encode(a).toByteArray
    Json.decode(bytes).to[Element].value
