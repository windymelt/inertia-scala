package dev.capslock.inertia.tapir

import dev.capslock.inertia.core.*
import sttp.model.StatusCode

class InertiaTapirSuite extends munit.FunSuite:

  // Minimal JsonObject for testing
  given JsonObject[Map[String, String]] with
    def empty = Map.empty
    def merge(base: Map[String, String], overlay: Map[String, String]) = base ++ overlay
    def filterKeys(p: Map[String, String], only: Set[String], except: Set[String]) =
      val afterOnly = if only.nonEmpty then p.filter((k, _) => only.contains(k)) else p
      afterOnly.filterNot((k, _) => except.contains(k))
    def toJsonObjectString(p: Map[String, String]) =
      p.map((k, v) => s""""$k":"$v"""").mkString("{", ",", "}")

  private val noHeaders = InertiaHeaders(
    isInertia = false,
    clientVersion = None,
    partialComponent = None,
    partialOnly = Set.empty,
    partialExcept = Set.empty
  )

  private val inertiaHeaders = InertiaHeaders(
    isInertia = true,
    clientVersion = None,
    partialComponent = None,
    partialOnly = Set.empty,
    partialExcept = Set.empty
  )

  test("render returns HTML for non-Inertia request") {
    val resp = InertiaTapir.render(
      noHeaders, "/test", "GET", "TestPage", Map("key" -> "value")
    )
    assertEquals(resp.statusCode, StatusCode.Ok)
    assert(resp.body.contains("data-page="))
    assert(resp.headers.exists(h => h.name == "Content-Type" && h.value.contains("text/html")))
  }

  test("render returns JSON for Inertia request") {
    val resp = InertiaTapir.render(
      inertiaHeaders, "/test", "GET", "TestPage", Map("key" -> "value")
    )
    assertEquals(resp.statusCode, StatusCode.Ok)
    assert(resp.body.contains("\"component\":\"TestPage\""))
    assert(resp.headers.exists(h => h.name == "X-Inertia" && h.value == "true"))
  }

  test("render returns 409 Conflict on version mismatch") {
    val headers = inertiaHeaders.copy(clientVersion = Some("old"))
    val resp = InertiaTapir.render(
      headers, "/test", "GET", "TestPage", Map.empty[String, String], version = "new"
    )
    assertEquals(resp.statusCode, StatusCode.Conflict)
    assert(resp.headers.exists(h => h.name == "X-Inertia-Location" && h.value == "/test"))
  }

  test("redirect normalizes POST 302 to 303") {
    val resp = InertiaTapir.redirect("POST", "/target", 302)
    assertEquals(resp.statusCode, StatusCode.SeeOther)
    assert(resp.headers.exists(h => h.name == "Location" && h.value == "/target"))
  }

  test("redirect preserves GET 302") {
    val resp = InertiaTapir.redirect("GET", "/target", 302)
    assertEquals(resp.statusCode, StatusCode.Found)
  }
