package dev.capslock.inertia.core

import com.github.plokhotnyuk.jsoniter_scala.core.*

// ── Props type ───────────────────────────────────────────────────────────────
//
// Each prop field value is stored as a pre-serialized JSON byte array.
// This means:
//   - The core can include any type as props (as long as it has a JsonValueCodec)
//   - Merge is just Map's ++
//   - Final output simply concatenates each field's byte array (no re-serialization)

opaque type RawJson = Array[Byte]

object RawJson:
  /** Serialize any type A as a JSON field value */
  def of[A: JsonValueCodec](a: A): RawJson = writeToArray(a)

  /** Use an existing JSON string directly as a field value */
  def raw(json: String): RawJson = json.getBytes("UTF-8")

// Props = field name -> pre-serialized JSON value
type Props = Map[String, RawJson]

object Props:
  val empty: Props = Map.empty

  /** Varargs constructor for listing fields */
  def of(fields: (String, RawJson)*): Props = fields.toMap

// ── JsonObject[Props] typeclass instance ─────────────────────────────────────

given JsonObject[Props] with
  def empty: Props = Props.empty

  // overlay overwrites base (right side wins)
  def merge(base: Props, overlay: Props): Props = base ++ overlay

  def filterKeys(p: Props, only: Set[String], except: Set[String]): Props =
    if only.nonEmpty        then p.view.filterKeys(only.contains).toMap
    else if except.nonEmpty then p.view.filterKeys(!except.contains(_)).toMap
    else p

  // Map[String, Array[Byte]] -> {"key1":...,"key2":...} JSON string
  // Each value is already serialized, so no re-encoding needed
  def toJsonObjectString(p: Props): String =
    if p.isEmpty then "{}"
    else
      val sb = new java.lang.StringBuilder("{")
      var first = true
      p.foreach { (k, v) =>
        if !first then sb.append(',')
        sb.append('"')
        // Escape keys (borrowing jsoniter's writeToString)
        val escaped = writeToString(k)(using stringCodec)
          .drop(1).dropRight(1)  // Strip surrounding quotes to get inner content
        sb.append(escaped)
        sb.append('"').append(':')
        sb.append(new String(v, "UTF-8"))
        first = false
      }
      sb.append('}').toString

// ── String codec (not provided by jsoniter-scala out of the box) ─────────────

given stringCodec: JsonValueCodec[String] =
  new JsonValueCodec[String]:
    def decodeValue(in: JsonReader, default: String): String = in.readString(default)
    def encodeValue(x: String, out: JsonWriter): Unit = out.writeVal(x)
    def nullValue: String = null

// ── Convenience helpers ──────────────────────────────────────────────────────
//
// Usage:
//   import inertia.core.JsoniterProps.*
//
//   Props.of(
//     "users"  -> prop(userList),    // List[User] must have a JsonValueCodec
//     "count"  -> prop(42),
//     "active" -> prop(true)
//   )

object JsoniterProps:
  /** Convert any type to a props field value */
  def prop[A: JsonValueCodec](a: A): RawJson = RawJson.of(a)

  /** String -> props field value (shorthand that doesn't require a String codec in scope) */
  def str(s: String): RawJson = RawJson.of(s)(using stringCodec)
