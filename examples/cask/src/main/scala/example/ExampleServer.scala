package example

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import dev.capslock.inertia.core.{*, given}
import dev.capslock.inertia.core.JsoniterProps.*
import dev.capslock.inertia.cask.InertiaCask

// ── Data types ───────────────────────────────────────────────────────────────

case class User(id: Int, name: String, email: String)
object User:
  given JsonValueCodec[User] = JsonCodecMaker.make
  given JsonValueCodec[List[User]] = JsonCodecMaker.make

case class Post(id: Int, title: String, body: String, authorId: Int)
object Post:
  given JsonValueCodec[Post] = JsonCodecMaker.make
  given JsonValueCodec[List[Post]] = JsonCodecMaker.make

case class Todo(id: Int, title: String, done: Boolean)
object Todo:
  given JsonValueCodec[Todo] = JsonCodecMaker.make
  given JsonValueCodec[List[Todo]] = JsonCodecMaker.make

case class CreateTodoRequest(title: String)
object CreateTodoRequest:
  given JsonValueCodec[CreateTodoRequest] = JsonCodecMaker.make

case class UpdateProfileRequest(name: String, email: String)
object UpdateProfileRequest:
  given JsonValueCodec[UpdateProfileRequest] = JsonCodecMaker.make

case class UpdatePasswordRequest(password: String, passwordConfirmation: String)
object UpdatePasswordRequest:
  given JsonValueCodec[UpdatePasswordRequest] = JsonCodecMaker.make

// ── Server ───────────────────────────────────────────────────────────────────

object ExampleServer extends cask.MainRoutes:

  override def port: Int = 9000

  // Sample data
  private var users = List(
    User(1, "Alice", "alice@example.com"),
    User(2, "Bob", "bob@example.com"),
    User(3, "Charlie", "charlie@example.com")
  )

  // In-memory TODO list
  private var todos = List(
    Todo(1, "inertia-scala を試す", true),
    Todo(2, "Cask バインディングを書く", true),
    Todo(3, "Example アプリを作る", false)
  )
  private var todoNextId = 4

  private val posts = List(
    Post(1, "Hello Inertia", "This is the first post using inertia-scala!", 1),
    Post(2, "Scala 3 is great", "Pattern matching, given instances, opaque types...", 2),
    Post(3, "Cask is simple", "A micro web framework for Scala.", 1)
  )

  // HTML layout referencing the Vite dev server
  private def layout(content: String): String =
    s"""|<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |  <meta charset="UTF-8">
        |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
        |  <title>inertia-scala example</title>
        |  <script type="module">
        |    import RefreshRuntime from "http://localhost:5173/@react-refresh"
        |    RefreshRuntime.injectIntoGlobalHook(window)
        |    window.$$RefreshReg$$ = () => {}
        |    window.$$RefreshSig$$ = () => (type) => type
        |    window.__vite_plugin_react_preamble_installed__ = true
        |  </script>
        |  <script type="module" src="http://localhost:5173/@vite/client"></script>
        |  <script type="module" src="http://localhost:5173/src/main.jsx"></script>
        |</head>
        |<body>
        |  $content
        |</body>
        |</html>""".stripMargin

  @cask.get("/")
  def index(req: cask.Request) =
    InertiaCask.render(
      req,
      component = "Home",
      props = Props.of(
        "greeting"  -> str("Welcome to inertia-scala!"),
        "userCount" -> RawJson.raw(users.length.toString)
      ),
      layoutFn = layout
    )

  @cask.get("/users")
  def userList(req: cask.Request) =
    InertiaCask.render(
      req,
      component = "Users/Index",
      props = Props.of(
        "users" -> prop(users)
      ),
      layoutFn = layout
    )

  @cask.get("/users/:id")
  def userShow(req: cask.Request, id: Int) =
    users.find(_.id == id) match
      case Some(user) => renderUserShow(req, user)
      case None       => cask.Response("Not Found", statusCode = 404)

  // Shared helper that renders Users/Show.
  // Callers just pass errors through; which error bag they end up nested under
  // is decided automatically by the core based on the X-Inertia-Error-Bag header the client sends.
  private def renderUserShow(
    req: cask.Request,
    user: User,
    errors: Map[String, String] = Map.empty
  ): cask.Response[String] =
    InertiaCask.render(
      req,
      component = "Users/Show",
      props = Props.of(
        "user"  -> prop(user),
        "posts" -> prop(posts.filter(_.authorId == user.id))
      ),
      errors = errors,
      layoutFn = layout
    )

  // Profile update form. The client sends this with errorBag "updateProfile".
  @cask.post("/users/:id/profile")
  def updateProfile(req: cask.Request, id: Int) =
    users.find(_.id == id) match
      case None => cask.Response("Not Found", statusCode = 404)
      case Some(user) =>
        val body = readFromString[UpdateProfileRequest](req.text())
        val errors = Map.newBuilder[String, String]
        if body.name.trim.isEmpty then
          errors += "name" -> "名前を入力してください"
        if !body.email.contains("@") then
          errors += "email" -> "メールアドレスの形式が正しくありません"
        val errs = errors.result()
        if errs.nonEmpty then
          renderUserShow(req, user, errs)
        else
          users = users.map(u =>
            if u.id == id then u.copy(name = body.name.trim, email = body.email) else u
          )
          InertiaCask.redirect(req, s"/users/$id", 303)

  // Password change form. The client sends this with errorBag "updatePassword".
  // (For the demo, the password is not actually persisted.)
  @cask.post("/users/:id/password")
  def updatePassword(req: cask.Request, id: Int) =
    users.find(_.id == id) match
      case None => cask.Response("Not Found", statusCode = 404)
      case Some(user) =>
        val body = readFromString[UpdatePasswordRequest](req.text())
        val errors = Map.newBuilder[String, String]
        if body.password.length < 8 then
          errors += "password" -> "パスワードは8文字以上にしてください"
        else if body.password != body.passwordConfirmation then
          errors += "passwordConfirmation" -> "パスワードが一致しません"
        val errs = errors.result()
        if errs.nonEmpty then
          renderUserShow(req, user, errs)
        else
          InertiaCask.redirect(req, s"/users/$id", 303)

  @cask.get("/about")
  def about(req: cask.Request) =
    InertiaCask.render(
      req,
      component = "About",
      props = Props.of(
        "version" -> str("0.1.0-SNAPSHOT")
      ),
      layoutFn = layout
    )

  // ── TODO routes ─────────────────────────────────────────────────────────────

  @cask.get("/todos")
  def todoIndex(req: cask.Request) =
    InertiaCask.render(
      req,
      component = "Todos/Index",
      props = Props.of(
        "todos" -> prop(todos)
      ),
      layoutFn = layout
    )

  // @cask.postJson re-serializes the return value's body as JSON, so here—where
  // we need to return an Inertia response that already has a JSON body—we use
  // @cask.post instead and parse the request body manually.
  @cask.post("/todos")
  def todoCreate(req: cask.Request) =
    val title = readFromString[CreateTodoRequest](req.text()).title
    if title.trim.isEmpty then
      // Server-side validation. Returning the same component with errors attached
      // lets the client's useForm pick it up as form.errors.
      // (Since this library has no session mechanism, we return a page with
      //  errors attached directly, rather than a 303 redirect + flash.)
      InertiaCask.render(
        req,
        component = "Todos/Index",
        props = Props.of("todos" -> prop(todos)),
        errors = Map("title" -> "タイトルを入力してください"),
        layoutFn = layout
      )
    else
      val todo = Todo(todoNextId, title.trim, done = false)
      todoNextId += 1
      todos = todos :+ todo
      // A normal 303 redirect.
      // Note: a fragment-carrying redirect (409 + X-Inertia-Redirect) is already
      //       supported on the server side, but since the current client
      //       @inertiajs/core 2.3.18 doesn't handle x-inertia-redirect, the
      //       example uses a normal redirect instead.
      InertiaCask.redirect(req, "/todos", 303)

  @cask.postJson("/todos/:id/toggle")
  def todoToggle(req: cask.Request, id: Int) =
    todos = todos.map: t =>
      if t.id == id then t.copy(done = !t.done) else t
    InertiaCask.redirect(req, "/todos", 303)

  @cask.postJson("/todos/:id/delete")
  def todoDelete(req: cask.Request, id: Int) =
    todos = todos.filterNot(_.id == id)
    InertiaCask.redirect(req, "/todos", 303)

  initialize()
