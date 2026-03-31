package dev.capslock.inertia.core

// ── JSON typeclass ───────────────────────────────────────────────────────────
// The core only knows this trait. Any JSON backend (ujson, jsoniter-scala, circe, etc.) can implement it.

trait JsonObject[P]:
  def empty: P
  def merge(base: P, overlay: P): P
  def filterKeys(p: P, only: Set[String], except: Set[String]): P
  def toJsonObjectString(p: P): String

// ── HTTP abstraction ─────────────────────────────────────────────────────────

trait InertiaRequest:
  def isInertia: Boolean
  def clientVersion: Option[String]
  def url: String
  def method: String
  def partialComponent: Option[String]
  def partialOnly: Set[String]
  def partialExcept: Set[String]

// ── Response types ───────────────────────────────────────────────────────────

sealed trait InertiaResult[+P]
object InertiaResult:
  case class InertiaJson[P](page: InertiaPage[P]) extends InertiaResult[P]
  case class InertiaHtml[P](page: InertiaPage[P]) extends InertiaResult[P]
  case class Conflict(redirectTo: String)          extends InertiaResult[Nothing]
  case class Redirect(location: String, status: Int) extends InertiaResult[Nothing]

case class InertiaPage[P](
  component: String,
  props: P,
  url: String,
  version: String
)

// ── Core logic ───────────────────────────────────────────────────────────────

object InertiaCore:

  val HdrInertia       = "x-inertia"
  val HdrVersion       = "x-inertia-version"
  val HdrPartialCmp    = "x-inertia-partial-component"
  val HdrPartialOnly   = "x-inertia-partial-data"
  val HdrPartialExcept = "x-inertia-partial-except"

  def render[P](
    req: InertiaRequest,
    component: String,
    props: P,
    sharedProps: Option[P] = None,
    version: String = "",
    layoutFn: String => String = defaultLayout
  )(using J: JsonObject[P]): InertiaResult[P] =
    val effectiveSharedProps = sharedProps.getOrElse(J.empty)

    if req.isInertia && version.nonEmpty && req.clientVersion.exists(_ != version) then
      return InertiaResult.Conflict(req.url)

    val merged = J.merge(effectiveSharedProps, props)

    val finalProps =
      if req.isInertia && req.partialComponent.contains(component) then
        J.filterKeys(merged, req.partialOnly, req.partialExcept)
      else
        merged

    val page = InertiaPage(component, finalProps, req.url, version)

    if req.isInertia then InertiaResult.InertiaJson(page)
    else                   InertiaResult.InertiaHtml(page)

  def pageToJson[P: JsonObject](page: InertiaPage[P]): String =
    val J = summon[JsonObject[P]]
    // The core only concatenates JSON strings. Serialization is delegated to the typeclass.
    s"""{"component":${quoteStr(page.component)},"props":${J.toJsonObjectString(page.props)},"url":${quoteStr(page.url)},"version":${quoteStr(page.version)}}"""

  def pageToHtml[P: JsonObject](
    page: InertiaPage[P],
    layoutFn: String => String = defaultLayout
  ): String =
    val encoded = escapeAttr(pageToJson(page))
    layoutFn(s"""<div id="app" data-page="$encoded"></div>""")

  def normalizeRedirectStatus(method: String, status: Int): Int =
    if Set("POST","PUT","PATCH","DELETE").contains(method.toUpperCase)
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

  private def escapeAttr(s: String): String =
    s.replace("&", "&amp;").replace("\"", "&quot;")
