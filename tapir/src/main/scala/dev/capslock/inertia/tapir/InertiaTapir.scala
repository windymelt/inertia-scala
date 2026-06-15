package dev.capslock.inertia.tapir

import dev.capslock.inertia.core.*
import sttp.model.{Header, StatusCode}
import sttp.tapir.*

// ── Inertia headers extracted from a Tapir request ──────────────────────────

case class InertiaHeaders(
    isInertia: Boolean,
    clientVersion: Option[String],
    partialComponent: Option[String],
    partialOnly: Set[String],
    partialExcept: Set[String],
    errorBag: Option[String] = None
)

// ── InertiaRequest implementation backed by extracted headers ────────────────

class TapirInertiaRequest(
    headers: InertiaHeaders,
    val url: String,
    val method: String
) extends InertiaRequest:
  val isInertia: Boolean              = headers.isInertia
  val clientVersion: Option[String]   = headers.clientVersion
  val partialComponent: Option[String] = headers.partialComponent
  val partialOnly: Set[String]        = headers.partialOnly
  val partialExcept: Set[String]      = headers.partialExcept
  val errorBag: Option[String]        = headers.errorBag

// ── Tapir-friendly response ─────────────────────────────────────────────────

/** Framework-agnostic Inertia response that can be returned from Tapir server logic. */
case class InertiaResponse(
    statusCode: StatusCode,
    body: String,
    headers: List[Header]
)

// ── Main binding ────────────────────────────────────────────────────────────

object InertiaTapir:

  // ── Header input extractor ──────────────────────────────────────────────

  /** Tapir EndpointInput that extracts all Inertia-related headers. */
  val inertiaHeadersInput: EndpointInput[InertiaHeaders] =
    header[Option[String]](InertiaCore.HdrInertia)
      .and(header[Option[String]](InertiaCore.HdrVersion))
      .and(header[Option[String]](InertiaCore.HdrPartialCmp))
      .and(header[Option[String]](InertiaCore.HdrPartialOnly))
      .and(header[Option[String]](InertiaCore.HdrPartialExcept))
      .and(header[Option[String]](InertiaCore.HdrErrorBag))
      .map(tupleToHeaders)(headersToTuple)

  private def parseCommaSeparated(s: Option[String]): Set[String] =
    s.map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet).getOrElse(Set.empty)

  private type HeaderTuple =
    (Option[String], Option[String], Option[String], Option[String], Option[String], Option[String])

  private def tupleToHeaders(t: HeaderTuple): InertiaHeaders =
    InertiaHeaders(
      isInertia = t._1.isDefined,
      clientVersion = t._2,
      partialComponent = t._3,
      partialOnly = parseCommaSeparated(t._4),
      partialExcept = parseCommaSeparated(t._5),
      errorBag = t._6
    )

  private def headersToTuple(h: InertiaHeaders): HeaderTuple =
    (
      if h.isInertia then Some("true") else None,
      h.clientVersion,
      h.partialComponent,
      if h.partialOnly.nonEmpty then Some(h.partialOnly.mkString(", ")) else None,
      if h.partialExcept.nonEmpty then Some(h.partialExcept.mkString(", ")) else None,
      h.errorBag
    )

  // ── Output definition ───────────────────────────────────────────────────

  /** Tapir EndpointOutput that carries an InertiaResponse (status code, body, headers).
    * Declares TextHtml format so browsers' `Accept: text/html` matches.
    * The actual Content-Type is set explicitly in InertiaResponse.headers
    * (text/html for initial loads, application/json for Inertia XHR).
    */
  val inertiaOutput: EndpointOutput[InertiaResponse] =
    statusCode
      .and(stringBodyUtf8AnyFormat(Codec.string.format(CodecFormat.TextHtml())))
      .and(sttp.tapir.headers)
      .map(fromTuple)(toTuple)

  private def fromTuple(t: (StatusCode, String, List[Header])): InertiaResponse =
    InertiaResponse(t._1, t._2, t._3)

  private def toTuple(r: InertiaResponse): (StatusCode, String, List[Header]) =
    (r.statusCode, r.body, r.headers)

  // ── Render helper ───────────────────────────────────────────────────────

  /** Call InertiaCore.render and convert the result to an InertiaResponse. */
  def render[P](
      headers: InertiaHeaders,
      url: String,
      method: String,
      component: String,
      props: P,
      sharedProps: Option[P] = None,
      version: String = "",
      errors: Map[String, String] = Map.empty,
      layoutFn: String => String = InertiaCore.defaultLayout
  )(using J: JsonObject[P]): InertiaResponse =
    val req    = TapirInertiaRequest(headers, url, method)
    val result = InertiaCore.render(req, component, props, sharedProps, version, errors)
    resultToResponse(result, layoutFn)

  private def resultToResponse[P: JsonObject](
      result: InertiaResult[P],
      layoutFn: String => String
  ): InertiaResponse = result match
    case r: InertiaResult.InertiaJson[P @unchecked] =>
      InertiaResponse(
        statusCode = StatusCode.Ok,
        body = InertiaCore.pageToJson(r.page),
        headers = List(
          Header("Content-Type", "application/json; charset=utf-8"),
          Header("X-Inertia", "true"),
          Header("Vary", "X-Inertia")
        )
      )
    case r: InertiaResult.InertiaHtml[P @unchecked] =>
      InertiaResponse(
        statusCode = StatusCode.Ok,
        body = InertiaCore.pageToHtml(r.page, layoutFn),
        headers = List(
          Header("Content-Type", "text/html; charset=utf-8")
        )
      )
    case InertiaResult.Conflict(location) =>
      InertiaResponse(
        statusCode = StatusCode.Conflict,
        body = "",
        headers = List(Header("X-Inertia-Location", location))
      )
    case InertiaResult.Redirect(location, status) =>
      InertiaResponse(
        statusCode = StatusCode(status),
        body = "",
        headers = List(Header("Location", location))
      )

  // ── Redirect helper ─────────────────────────────────────────────────────

  /** リダイレクトレスポンスを生成する。
    *
    * `isInertia` が true かつ遷移先にフラグメント(#)が含まれる場合は
    * 409 + X-Inertia-Redirect を、それ以外は Location リダイレクト（302/303）を返す。
    */
  def redirect(
      method: String,
      location: String,
      status: Int = 302,
      isInertia: Boolean = false
  ): InertiaResponse =
    InertiaCore.planRedirect(method, location, status, isInertia) match
      case RedirectPlan.Fragment(loc) =>
        InertiaResponse(
          statusCode = StatusCode.Conflict,
          body = "",
          headers = List(Header(InertiaCore.HdrRedirect, loc))
        )
      case RedirectPlan.Location(loc, st) =>
        InertiaResponse(
          statusCode = StatusCode(st),
          body = "",
          headers = List(Header("Location", loc))
        )
