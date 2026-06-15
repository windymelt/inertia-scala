package dev.capslock.inertia.core

// ── JSON typeclass ───────────────────────────────────────────────────────────
// The core only knows this trait. Any JSON backend (ujson, jsoniter-scala, circe, etc.) can implement it.

trait JsonObject[P]:
  def empty: P
  def merge(base: P, overlay: P): P
  def filterKeys(p: P, only: Set[String], except: Set[String]): P
  def toJsonObjectString(p: P): String

  /** `errors` プロパティ（`{errors: {...}}` 形のオブジェクト）を構築する。
    *
    * Inertia プロトコルでは props に必ず `errors` を含める必要があり、エラーが
    * 無い場合は空オブジェクト `{}` を入れる。`errorBag` が指定された場合は
    * エラーをそのキー配下にネストする（`{errors: {bag: {...}}}`）。
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
  case class Conflict(redirectTo: String)          extends InertiaResult[Nothing]
  case class Redirect(location: String, status: Int) extends InertiaResult[Nothing]

// ── Redirect plan ────────────────────────────────────────────────────────────
// リダイレクトのレスポンス形。フレームワーク側がこれをレスポンスへ変換する。

enum RedirectPlan:
  /** Location ヘッダーによる通常のリダイレクト（302/303）。 */
  case Location(location: String, status: Int)
  /** 遷移先にフラグメント(#)を含む Inertia リダイレクト。409 + X-Inertia-Redirect。 */
  case Fragment(location: String)

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
  val HdrErrorBag      = "x-inertia-error-bag"
  // レスポンス側ヘッダー
  val HdrLocation      = "X-Inertia-Location"
  val HdrRedirect      = "X-Inertia-Redirect"

  def render[P](
    req: InertiaRequest,
    component: String,
    props: P,
    sharedProps: Option[P] = None,
    version: String = "",
    errors: Map[String, String] = Map.empty,
    layoutFn: String => String = defaultLayout
  )(using J: JsonObject[P]): InertiaResult[P] =
    val effectiveSharedProps = sharedProps.getOrElse(J.empty)

    // 資産バージョン不一致による 409 は GET リクエストのみが対象（公式アダプター準拠）。
    // 非 GET（フォーム送信など）では資産バージョンの差異でリダイレクトを発生させない。
    if req.isInertia && req.method.toUpperCase == "GET"
       && version.nonEmpty && req.clientVersion.exists(_ != version) then
      return InertiaResult.Conflict(req.url)

    val merged = J.merge(effectiveSharedProps, props)

    val filtered =
      if req.isInertia && req.partialComponent.contains(component) then
        J.filterKeys(merged, req.partialOnly, req.partialExcept)
      else
        merged

    // errors は常に props に含める。partial reload でもフィルタ対象外とするため
    // フィルタ適用後にマージする。
    val finalProps = J.merge(filtered, J.errors(errors, req.errorBag))

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

  /** リダイレクト先 URL がフラグメント(#)を含むか。 */
  def redirectHasFragment(location: String): Boolean = location.contains('#')

  /** リダイレクトのレスポンス形を決定する。
    *
    * Inertia リクエストかつ遷移先にフラグメントが含まれる場合は 409 +
    * X-Inertia-Redirect（[[RedirectPlan.Fragment]]）を、それ以外は通常の
    * Location リダイレクト（[[RedirectPlan.Location]]、ステータスは
    * [[normalizeRedirectStatus]] で正規化）を返す。
    */
  def planRedirect(
    method: String,
    location: String,
    status: Int,
    isInertia: Boolean
  ): RedirectPlan =
    if isInertia && redirectHasFragment(location) then
      RedirectPlan.Fragment(location)
    else
      RedirectPlan.Location(location, normalizeRedirectStatus(method, status))

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
