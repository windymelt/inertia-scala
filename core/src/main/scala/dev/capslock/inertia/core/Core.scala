package dev.capslock.inertia.core

// ── JSON typeclass ───────────────────────────────────────────────────────────
// The core only knows this trait. Any JSON backend (ujson, jsoniter-scala, circe, etc.) can implement it.

trait JsonObject[P]:
  def empty: P
  def merge(base: P, overlay: P): P
  def filterKeys(p: P, only: Set[String], except: Set[String]): P
  def toJsonObjectString(p: P): String

  /** Build the `errors` property (an object shaped like `{errors: {...}}`).
    *
    * The Inertia protocol requires props to always include `errors`; when there are no errors, an empty object `{}` is
    * used. When `errorBag` is given, the errors are nested under that key (`{errors: {bag: {...}}}`).
    */
  def errors(messages: Map[String, String], errorBag: Option[String]): P

// ── HTTP abstraction ─────────────────────────────────────────────────────────

trait InertiaRequest:
  def isInertia: Boolean
  def clientVersion: Option[String]
  def url: String
  def method: String
  def partialComponent: Option[String]
  def partialOnly: Set[String]
  def partialExcept: Set[String]
  def errorBag: Option[String]

// ── Response types ───────────────────────────────────────────────────────────

sealed trait InertiaResult[+P]
object InertiaResult:
  case class InertiaJson[P](page: InertiaPage[P]) extends InertiaResult[P]
  case class InertiaHtml[P](page: InertiaPage[P]) extends InertiaResult[P]

  /** Asset-version mismatch. The response carries both X-Inertia-Location (redirectTo) and X-Inertia-Version (the
    * server's current version), as the protocol requires.
    */
  case class Conflict(redirectTo: String, version: String) extends InertiaResult[Nothing]
  case class Redirect(location: String, status: Int)       extends InertiaResult[Nothing]

// ── Redirect plan ────────────────────────────────────────────────────────────
// The shape of a redirect response. Framework integrations convert this into an actual response.

enum RedirectPlan:
  /** A normal redirect via the Location header (302/303). */
  case Location(location: String, status: Int)

  /** An Inertia redirect whose destination contains a fragment (#). 409 + X-Inertia-Redirect. */
  case Fragment(location: String)

case class InertiaPage[P](
  component: String,
  props: P,
  url: String,
  version: String,
)

// ── Core logic ───────────────────────────────────────────────────────────────

object InertiaCore:

  val HdrInertia       = "x-inertia"
  val HdrVersion       = "x-inertia-version"
  val HdrPartialCmp    = "x-inertia-partial-component"
  val HdrPartialOnly   = "x-inertia-partial-data"
  val HdrPartialExcept = "x-inertia-partial-except"
  val HdrErrorBag      = "x-inertia-error-bag"
  // Response-side headers
  val HdrLocation        = "X-Inertia-Location"
  val HdrRedirect        = "X-Inertia-Redirect"
  val HdrVersionResponse = "X-Inertia-Version"

  def render[P](
    req: InertiaRequest,
    component: String,
    props: P,
    sharedProps: Option[P] = None,
    version: String = "",
    errors: Map[String, String] = Map.empty,
    layoutFn: String => String = defaultLayout,
  )(using J: JsonObject[P]): InertiaResult[P] =
    // A 409 for asset version mismatch only applies to GET requests (matching the official adapter).
    // For non-GET requests (form submissions, etc.) an asset version mismatch does not trigger a redirect.
    if req.isInertia && req.method.toUpperCase == "GET"
      && version.nonEmpty && req.clientVersion.exists(_ != version)
    then InertiaResult.Conflict(req.url, version)
    else
      val effectiveSharedProps = sharedProps.getOrElse(J.empty)

      val merged = J.merge(effectiveSharedProps, props)

      val filtered =
        if req.isInertia && req.partialComponent.contains(component) then
          J.filterKeys(merged, req.partialOnly, req.partialExcept)
        else merged

      // errors is always included in props. It's merged in after filtering so that
      // it stays exempt from partial reload filtering.
      val finalProps = J.merge(filtered, J.errors(errors, req.errorBag))

      val page = InertiaPage(component, finalProps, req.url, version)

      if req.isInertia then InertiaResult.InertiaJson(page)
      else InertiaResult.InertiaHtml(page)

  def pageToJson[P: JsonObject](page: InertiaPage[P]): String =
    val J = summon[JsonObject[P]]
    // The core only concatenates JSON strings. Serialization is delegated to the typeclass.
    s"""{"component":${quoteStr(page.component)},"props":${J.toJsonObjectString(page.props)},"url":${quoteStr(
        page.url,
      )},"version":${quoteStr(page.version)}}"""

  /** Render the initial-load HTML fragment.
    *
    * Since Inertia v3, the page object is embedded as a JSON script element (`<script data-page="app"
    * type="application/json">`) next to the mount element, instead of the legacy `data-page` attribute. The client
    * locates it via `script[data-page="app"][type="application/json"]` and parses its text content with JSON.parse, so
    * the JSON must not be HTML-entity encoded.
    */
  def pageToHtml[P: JsonObject](
    page: InertiaPage[P],
    layoutFn: String => String = defaultLayout,
  ): String =
    val encoded = escapeJsonForScript(pageToJson(page))
    layoutFn(
      s"""<script data-page="app" type="application/json">$encoded</script>
         |<div id="app"></div>""".stripMargin,
    )

  /** Whether the redirect destination URL contains a fragment (#). */
  def redirectHasFragment(location: String): Boolean = location.contains('#')

  /** Determine the shape of a redirect response.
    *
    * When the request is an Inertia request and the destination contains a fragment, returns 409 + X-Inertia-Redirect
    * ([[RedirectPlan.Fragment]]); otherwise returns a normal Location redirect ([[RedirectPlan.Location]], with the
    * status normalized via [[normalizeRedirectStatus]]).
    */
  def planRedirect(
    method: String,
    location: String,
    status: Int,
    isInertia: Boolean,
  ): RedirectPlan =
    if isInertia && redirectHasFragment(location) then RedirectPlan.Fragment(location)
    else RedirectPlan.Location(location, normalizeRedirectStatus(method, status))

  def normalizeRedirectStatus(method: String, status: Int): Int =
    if Set("POST", "PUT", "PATCH", "DELETE").contains(method.toUpperCase)
      && (status == 301 || status == 302)
    then 303
    else status

  def defaultLayout(content: String): String =
    s"""|<!DOCTYPE html>
        |<html lang="en">
        |<head><meta charset="UTF-8"><title>App</title></head>
        |<body>
        |$content
        |</body>
        |</html>""".stripMargin

  private def quoteStr(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  /** Escape every unescaped forward slash as `\/` so that a `</script>` sequence inside prop data cannot close the
    * script element early, as the protocol requires. `\/` is a valid JSON string escape, so the parsed value is
    * unchanged. A slash already escaped (preceded by an odd number of backslashes) is left as is to avoid corrupting
    * the escape sequence.
    */
  private[core] def escapeJsonForScript(json: String): String =
    val (sb, _) = json.foldLeft((new StringBuilder(json.length + 16), 0)) { case ((sb, backslashes), c) =>
      c match
        case '\\' => (sb.append(c), backslashes + 1)
        case '/' if backslashes % 2 == 0 => (sb.append("\\/"), 0)
        case _ => (sb.append(c), 0)
    }
    sb.toString
