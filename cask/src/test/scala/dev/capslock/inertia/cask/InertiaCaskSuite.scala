package dev.capslock.inertia.cask

import dev.capslock.inertia.core.*
import dev.capslock.inertia.core.JsoniterProps.str
import dev.capslock.inertia.core.given
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString


class InertiaCaskSuite extends munit.FunSuite:

  /** Build a cask.Request backed by a bare Undertow exchange, without starting a server. CaskInertiaRequest only reads
   *  the method, path, query string and headers, none of which needs a live connection.
   */
  private def request(
      method: String = "GET",
      path: String = "/test",
      query: String = "",
      headers: Seq[(String, String)] = Nil,
  ): cask.Request =
    val exchange = new HttpServerExchange(null) // scalafix:ok DisableSyntax.null
    exchange.setRequestMethod(HttpString(method))
    exchange.setRequestPath(path)
    exchange.setQueryString(query)
    headers.foreach((name, value) => exchange.getRequestHeaders.add(HttpString(name), value))
    cask.Request(exchange, Nil, Map.empty)

  private val inertiaHeader = "X-Inertia" -> "true"

  private def header(resp: cask.Response[String], name: String): Option[String] = resp.headers.collectFirst:
    case (k, v) if k.equalsIgnoreCase(name) => v

  // ── CaskInertiaRequest ─────────────────────────────────────────────────────

  test("CaskInertiaRequest maps a plain request without Inertia headers"):
    val ireq = CaskInertiaRequest(request())
    assertEquals(ireq.isInertia, false)
    assertEquals(ireq.clientVersion, None)
    assertEquals(ireq.partialComponent, None)
    assertEquals(ireq.partialOnly, Set.empty[String])
    assertEquals(ireq.partialExcept, Set.empty[String])
    assertEquals(ireq.errorBag, None)
    assertEquals(ireq.method, "GET")
    assertEquals(ireq.url, "/test")

  test("CaskInertiaRequest reads Inertia headers case-insensitively"):
    val ireq = CaskInertiaRequest(
      request(headers =
        Seq(
          "X-Inertia" -> "true",
          "X-Inertia-Version" -> "v1",
          "X-Inertia-Partial-Component" -> "Users",
          "X-Inertia-Partial-Data" -> "users, count ,,",
          "X-Inertia-Partial-Except" -> "secret",
          "X-Inertia-Error-Bag" -> "login",
        ),
      ),
    )
    assertEquals(ireq.isInertia, true)
    assertEquals(ireq.clientVersion, Some("v1"))
    assertEquals(ireq.partialComponent, Some("Users"))
    assertEquals(ireq.partialOnly, Set("users", "count"))
    assertEquals(ireq.partialExcept, Set("secret"))
    assertEquals(ireq.errorBag, Some("login"))

  test("CaskInertiaRequest upper-cases the method"):
    assertEquals(
      CaskInertiaRequest(
        request(
          method = "post"
        )
      ).method,
      "POST",
    )

  test("CaskInertiaRequest appends a non-empty query string to the url"):
    assertEquals(
      CaskInertiaRequest(
        request(
          path = "/users",
          query = "page=2&q=a",
        )
      ).url,
      "/users?page=2&q=a",
    )

  // ── render ─────────────────────────────────────────────────────────────────

  test("render returns HTML for the initial visit"):
    val resp = InertiaCask.render(
      request(),
      "TestPage",
      Props.of("key" -> str("value")),
      version = "v1",
    )
    assertEquals(resp.statusCode, 200)
    assertEquals(header(resp, "Content-Type"), Some("text/html; charset=utf-8"))
    assertEquals(header(resp, "X-Inertia"), None)
    assert(resp.data.contains("""<script data-page="app" type="application/json">"""))
    assert(resp.data.contains("""<div id="app"></div>"""))
    assert(resp.data.contains(""""component":"TestPage""""))
    assert(resp.data.contains(""""key":"value""""))
    assert(resp.data.contains(""""url":"\/test""""))
    assert(resp.data.contains(""""version":"v1""""))

  test("render applies a custom layoutFn to the initial visit"):
    val resp = InertiaCask.render(
      request(),
      "TestPage",
      Props.empty,
      layoutFn = content => s"<main>$content</main>",
    )
    assert(resp.data.startsWith("<main>"))
    assert(resp.data.endsWith("</main>"))

  test("render returns JSON with X-Inertia and Vary headers for an Inertia visit"):
    val resp = InertiaCask.render(
      request(
        path = "/users",
        query = "page=2",
        headers = Seq(inertiaHeader, "X-Inertia-Version" -> "v1"),
      ),
      "Users",
      Props.of("key" -> str("value")),
      version = "v1",
    )
    assertEquals(resp.statusCode, 200)
    assertEquals(header(resp, "Content-Type"), Some("application/json; charset=utf-8"))
    assertEquals(header(resp, "X-Inertia"), Some("true"))
    assertEquals(header(resp, "Vary"), Some("X-Inertia"))
    assertEquals(
      resp.data,
      """{"component":"Users","props":{"key":"value","errors":{}},"url":"/users?page=2","version":"v1"}""",
    )

  test("render merges shared props under page props"):
    val resp = InertiaCask.render(
      request(
        headers = Seq(inertiaHeader)
      ),
      "TestPage",
      Props.of("a" -> str("page")),
      sharedProps = Some(Props.of("a" -> str("shared"), "b" -> str("shared"))),
    )
    assert(resp.data.contains(""""a":"page""""))
    assert(resp.data.contains(""""b":"shared""""))

  test("render nests errors under the requested error bag"):
    val resp = InertiaCask.render(
      request(
        headers = Seq(inertiaHeader, "X-Inertia-Error-Bag" -> "login")
      ),
      "TestPage",
      Props.empty,
      errors = Map("email" -> "required"),
    )
    assert(resp.data.contains(""""errors":{"login":{"email":"required"}}"""))

  test("render returns 409 with X-Inertia-Location on a GET version mismatch"):
    val resp = InertiaCask.render(
      request(
        path = "/users",
        query = "page=2",
        headers = Seq(inertiaHeader, "X-Inertia-Version" -> "old"),
      ),
      "Users",
      Props.empty,
      version = "new",
    )
    assertEquals(resp.statusCode, 409)
    assertEquals(resp.data, "")
    assertEquals(header(resp, "X-Inertia-Location"), Some("/users?page=2"))
    assertEquals(header(resp, "X-Inertia-Version"), Some("new"))

  test("render does not return 409 on a version mismatch for a non-GET request"):
    val resp = InertiaCask.render(
      request(
        method = "POST",
        headers = Seq(inertiaHeader, "X-Inertia-Version" -> "old"),
      ),
      "TestPage",
      Props.empty,
      version = "new",
    )
    assertEquals(resp.statusCode, 200)

  test("render does not return 409 on a version mismatch for a non-Inertia request"):
    val resp = InertiaCask.render(
      request(
        headers = Seq("X-Inertia-Version" -> "old")
      ),
      "TestPage",
      Props.empty,
      version = "new",
    )
    assertEquals(resp.statusCode, 200)
    assertEquals(header(resp, "Content-Type"), Some("text/html; charset=utf-8"))

  test("render filters props by X-Inertia-Partial-Data on a partial reload, keeping errors"):
    val resp = InertiaCask.render(
      request(headers =
        Seq(inertiaHeader, "X-Inertia-Partial-Component" -> "TestPage", "X-Inertia-Partial-Data" -> "a"),
      ),
      "TestPage",
      Props.of("a" -> str("1"), "b" -> str("2")),
    )
    assert(resp.data.contains(""""props":{"a":"1","errors":{}}"""))

  test("render filters props by X-Inertia-Partial-Except on a partial reload"):
    val resp = InertiaCask.render(
      request(headers =
        Seq(inertiaHeader, "X-Inertia-Partial-Component" -> "TestPage", "X-Inertia-Partial-Except" -> "b"),
      ),
      "TestPage",
      Props.of("a" -> str("1"), "b" -> str("2")),
    )
    assert(resp.data.contains(""""props":{"a":"1","errors":{}}"""))

  test("render ignores partial headers when the partial component differs"):
    val resp = InertiaCask.render(
      request(headers =
        Seq(inertiaHeader, "X-Inertia-Partial-Component" -> "Other", "X-Inertia-Partial-Data" -> "a"),
      ),
      "TestPage",
      Props.of("a" -> str("1"), "b" -> str("2")),
    )
    assert(resp.data.contains(""""b":"2""""))

  // ── redirect ───────────────────────────────────────────────────────────────

  for method <- Seq("POST", "PUT", "PATCH", "DELETE") do
    test(s"redirect normalizes $method 302 to 303"):
      val resp = InertiaCask.redirect(
        request(
          method = method
        ),
        "/target",
      )
      assertEquals(resp.statusCode, 303)
      assertEquals(resp.data, "")
      assertEquals(header(resp, "Location"), Some("/target"))

  test("redirect normalizes PUT 301 to 303"):
    assertEquals(
      InertiaCask.redirect(
        request(
          method = "PUT"
        ),
        "/target",
        301,
      ).statusCode,
      303,
    )

  test("redirect preserves GET 302"):
    val resp = InertiaCask.redirect(request(), "/target")
    assertEquals(resp.statusCode, 302)
    assertEquals(header(resp, "Location"), Some("/target"))

  test("redirect preserves other statuses on POST"):
    assertEquals(
      InertiaCask.redirect(
        request(
          method = "POST"
        ),
        "/target",
        307,
      ).statusCode,
      307,
    )

  test("redirect returns 409 with X-Inertia-Redirect for an Inertia request to a URL with a fragment"):
    val resp = InertiaCask.redirect(
      request(
        method = "POST",
        headers = Seq(inertiaHeader),
      ),
      "/target#section",
    )
    assertEquals(resp.statusCode, 409)
    assertEquals(header(resp, "X-Inertia-Redirect"), Some("/target#section"))
    assertEquals(header(resp, "Location"), None)

  test("redirect uses Location for a non-Inertia request to a URL with a fragment"):
    val resp = InertiaCask.redirect(
      request(
        method = "POST"
      ),
      "/target#section",
    )
    assertEquals(resp.statusCode, 303)
    assertEquals(header(resp, "Location"), Some("/target#section"))
end InertiaCaskSuite
