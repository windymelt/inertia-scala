package dev.capslock.inertia.cask

import dev.capslock.inertia.core.*

class CaskInertiaRequest(req: cask.Request) extends InertiaRequest:

  private def header(name: String): Option[String] =
    req.headers.get(name).flatMap(_.headOption)

  private def headerList(name: String): Set[String] =
    header(name)
      .map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet)
      .getOrElse(Set.empty)

  val isInertia: Boolean              = req.headers.contains(InertiaCore.HdrInertia)
  val clientVersion: Option[String]   = header(InertiaCore.HdrVersion)
  val partialComponent: Option[String] = header(InertiaCore.HdrPartialCmp)
  val partialOnly: Set[String]        = headerList(InertiaCore.HdrPartialOnly)
  val partialExcept: Set[String]      = headerList(InertiaCore.HdrPartialExcept)
  val method: String                  = req.exchange.getRequestMethod.toString.toUpperCase
  val url: String =
    val path = req.exchange.getRequestPath
    val qs   = req.exchange.getQueryString
    if qs.isEmpty then path else s"$path?$qs"

object InertiaCask:

  /**
   * Render a component from a Cask route handler.
   * P can be any type with a JsonObject[P] instance.
   * Typically Props (= Map[String, RawJson]) is used.
   */
  def render[P](
    req: cask.Request,
    component: String,
    props: P,
    sharedProps: Option[P]     = None,
    version: String            = "",
    layoutFn: String => String = InertiaCore.defaultLayout
  )(using J: JsonObject[P]): cask.Response[String] =
    val ireq = CaskInertiaRequest(req)
    val result: InertiaResult[P] = InertiaCore.render(ireq, component, props, sharedProps, version)
    resultToResponse(result, layoutFn)

  private def resultToResponse[P: JsonObject](
    result: InertiaResult[P],
    layoutFn: String => String
  ): cask.Response[String] = result match
    case r: InertiaResult.InertiaJson[P @unchecked] =>
      cask.Response(
        data       = InertiaCore.pageToJson(r.page),
        statusCode = 200,
        headers    = Seq(
          "Content-Type" -> "application/json; charset=utf-8",
          "X-Inertia"    -> "true",
          "Vary"         -> "X-Inertia"
        )
      )

    case r: InertiaResult.InertiaHtml[P @unchecked] =>
      cask.Response(
        data       = InertiaCore.pageToHtml(r.page, layoutFn),
        statusCode = 200,
        headers    = Seq("Content-Type" -> "text/html; charset=utf-8")
      )

    case InertiaResult.Conflict(location) =>
      cask.Response(
        data       = "",
        statusCode = 409,
        headers    = Seq("X-Inertia-Location" -> location)
      )

    case InertiaResult.Redirect(location, status) =>
      cask.Response(
        data       = "",
        statusCode = status,
        headers    = Seq("Location" -> location)
      )

  def redirect(
    req: cask.Request,
    location: String,
    status: Int = 302
  ): cask.Response[String] =
    val normalized = InertiaCore.normalizeRedirectStatus(
      req.exchange.getRequestMethod.toString, status
    )
    cask.Response("", statusCode = normalized, headers = Seq("Location" -> location))
