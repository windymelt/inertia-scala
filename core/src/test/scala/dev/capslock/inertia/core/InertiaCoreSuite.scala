package dev.capslock.inertia.core

import dev.capslock.inertia.core.JsoniterProps.*

class InertiaCoreSuite extends munit.FunSuite:

  // テスト用の InertiaRequest 実装
  case class FakeRequest(
      isInertia: Boolean = false,
      clientVersion: Option[String] = None,
      url: String = "/test",
      method: String = "GET",
      partialComponent: Option[String] = None,
      partialOnly: Set[String] = Set.empty,
      partialExcept: Set[String] = Set.empty,
      errorBag: Option[String] = None
  ) extends InertiaRequest

  private def json(result: InertiaResult[Props]): String = result match
    case r: InertiaResult.InertiaJson[Props @unchecked] => InertiaCore.pageToJson(r.page)
    case r: InertiaResult.InertiaHtml[Props @unchecked] => InertiaCore.pageToJson(r.page)
    case other => fail(s"expected a page result, got $other")

  // ── errors プロパティ ──────────────────────────────────────────────────

  test("errors は常に props に含まれ、空のときは {} になる") {
    val result = InertiaCore.render(
      FakeRequest(isInertia = true), "Home", Props.of("greeting" -> str("hi"))
    )
    assert(json(result).contains("\"errors\":{}"), json(result))
  }

  test("errors にメッセージがある場合は props に反映される") {
    val result = InertiaCore.render(
      FakeRequest(isInertia = true), "Home", Props.empty,
      errors = Map("email" -> "必須です")
    )
    assert(json(result).contains("\"errors\":{\"email\":\"必須です\"}"), json(result))
  }

  test("errorBag が指定されるとエラーがバッグ配下にネストされる") {
    val req = FakeRequest(isInertia = true, errorBag = Some("createUser"))
    val result = InertiaCore.render(
      req, "Home", Props.empty, errors = Map("email" -> "invalid")
    )
    assert(
      json(result).contains("\"errors\":{\"createUser\":{\"email\":\"invalid\"}}"),
      json(result)
    )
  }

  test("errorBag があってもエラーが空なら errors は {}") {
    val req = FakeRequest(isInertia = true, errorBag = Some("createUser"))
    val result = InertiaCore.render(req, "Home", Props.empty)
    assert(json(result).contains("\"errors\":{}"), json(result))
  }

  test("partial reload でも errors は除外されず常に含まれる") {
    val req = FakeRequest(
      isInertia = true,
      partialComponent = Some("Home"),
      partialOnly = Set("greeting")
    )
    val result = InertiaCore.render(
      req, "Home",
      Props.of("greeting" -> str("hi"), "other" -> str("x")),
      errors = Map("name" -> "ng")
    )
    val out = json(result)
    assert(out.contains("\"greeting\":\"hi\""), out)
    assert(!out.contains("\"other\""), out)            // only で除外される
    assert(out.contains("\"errors\":{\"name\":\"ng\"}"), out) // errors は残る
  }

  // ── バージョン競合（GET 限定） ─────────────────────────────────────────

  test("GET でバージョン不一致なら 409 Conflict") {
    val req = FakeRequest(isInertia = true, method = "GET", clientVersion = Some("old"))
    val result = InertiaCore.render(req, "Home", Props.empty, version = "new")
    assertEquals(result, InertiaResult.Conflict("/test"))
  }

  test("非 GET ではバージョン不一致でも Conflict にならない") {
    val req = FakeRequest(isInertia = true, method = "POST", clientVersion = Some("old"))
    val result = InertiaCore.render(req, "Home", Props.empty, version = "new")
    assert(result.isInstanceOf[InertiaResult.InertiaJson[?]], result.toString)
  }

  // ── リダイレクト計画 ────────────────────────────────────────────────────

  test("planRedirect: 通常の遷移先は Location リダイレクト") {
    assertEquals(
      InertiaCore.planRedirect("GET", "/home", 302, isInertia = true),
      RedirectPlan.Location("/home", 302)
    )
  }

  test("planRedirect: POST の 302 は 303 に正規化される") {
    assertEquals(
      InertiaCore.planRedirect("POST", "/home", 302, isInertia = true),
      RedirectPlan.Location("/home", 303)
    )
  }

  test("planRedirect: Inertia リクエストでフラグメント付きなら Fragment") {
    assertEquals(
      InertiaCore.planRedirect("GET", "/home#section", 302, isInertia = true),
      RedirectPlan.Fragment("/home#section")
    )
  }

  test("planRedirect: 非 Inertia ではフラグメント付きでも Location") {
    assertEquals(
      InertiaCore.planRedirect("GET", "/home#section", 302, isInertia = false),
      RedirectPlan.Location("/home#section", 302)
    )
  }
