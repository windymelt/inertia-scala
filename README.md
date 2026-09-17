# inertia-scala

[![Latest version](https://index.scala-lang.org/windymelt/inertia-scala/inertia-core/latest.svg)](https://index.scala-lang.org/windymelt/inertia-scala/inertia-core)
[![Maven Central](https://maven-badges.sml.io/maven-central/dev.capslock/inertia-core_3/badge.svg)](https://maven-badges.sml.io/maven-central/dev.capslock/inertia-core_3/)

*Read this in [日本語](./README.ja.md).*

```scala
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import dev.capslock.inertia.core.{*, given}
import dev.capslock.inertia.core.JsoniterProps.*
import dev.capslock.inertia.cask.InertiaCask

case class User(id: Int, name: String)
object User:
  given JsonValueCodec[List[User]] = JsonCodecMaker.make

object Server extends cask.MainRoutes:
  private val users = List(User(1, "Alice"), User(2, "Bob"))

  @cask.get("/")
  def index(req: cask.Request) =
    InertiaCask.render(
      req,
      component = "Home",
      props = Props.of(
        "greeting" -> str("Hello from inertia-scala!"),
        "users"    -> prop(users)
      )
    )

  @cask.post("/users")
  def create(req: cask.Request) =
    // ... persist the user ...
    InertiaCask.redirect(req, "/")  // normalized to 303 for POST

  initialize()
```

inertia-scala is a Scala 3 server-side adapter for the [Inertia.js](https://inertiajs.com/) protocol. You keep server-side routing and controllers, and the standard Inertia client (`@inertiajs/react`, `@inertiajs/vue3`, ...) turns your pages into an SPA — no REST/GraphQL API layer needed.

The core is decoupled from any specific JSON library or HTTP framework: both are plugged in through typeclasses. Ready-made integrations are provided for [Cask](https://com-lihaoyi.github.io/cask/) and [Tapir](https://tapir.softwaremill.com/), and a jsoniter-scala-based props type is included as the default JSON backend.

## Getting Started

```scala
libraryDependencies ++= Seq(
  "dev.capslock" %% "inertia-core"  % "0.1.0",
  "dev.capslock" %% "inertia-cask"  % "0.1.0",  // Cask integration
  "dev.capslock" %% "inertia-tapir" % "0.1.0"   // Tapir integration
)
```

The framework dependencies (`cask`, `tapir-core`) are in the `Provided` scope, so add the one you use to your own dependencies. `inertia-core` and `inertia-tapir` are cross-built for JVM / Scala.js / Scala Native; `inertia-cask` is JVM-only (use `%%%` in a Scala.js / Scala Native project).

On the frontend, nothing is Scala-specific: set up a standard Inertia.js client and point the HTML layout (see `layoutFn` below) at your frontend bundle. On the first request the server responds with HTML that embeds the page object as a JSON script element (the Inertia v3 format); subsequent navigation gets JSON, exactly as the [protocol](https://inertiajs.com/the-protocol) specifies.

Runnable examples live in `examples/` — a Cask server (port 9000) and a Tapir server (port 9001), each paired with a Vite + React frontend:

```console
$ sbtn example-cask/run                                    # backend on :9000
$ cd examples/cask-frontend && npm install && npm run dev  # frontend on :5173
```

## Cookbook

### Passing typed props

`Props` values are built from anything that has a jsoniter-scala `JsonValueCodec`:

```scala
Props.of(
  "user"  -> prop(user),          // any A with a JsonValueCodec[A]
  "title" -> str("My page"),      // plain string
  "count" -> RawJson.raw("42")    // pre-rendered JSON snippet
)
```

### Shared props

Props common to every page (current user, flash messages, ...) go in `sharedProps`; page props win on key conflicts:

```scala
InertiaCask.render(req, "Dashboard", props, sharedProps = Some(shared))
```

### Validation errors and error bags

Pass validation failures through `errors`; the client's `useForm` receives them as `form.errors`. When the client submits with an `errorBag` option, the core reads the `X-Inertia-Error-Bag` header and nests the errors under that bag automatically:

```scala
val errs = Map("email" -> "Email address is invalid")
InertiaCask.render(req, "Users/Edit", props, errors = errs)
```

### Redirects after form submission

`redirect` normalizes `301` / `302` to `303` for `POST` / `PUT` / `PATCH` / `DELETE`, as the protocol requires. If the destination contains a fragment (`#`) and the request is an Inertia request, it responds with `409` + `X-Inertia-Redirect` instead:

```scala
InertiaCask.redirect(req, "/todos")
```

### Asset versioning

Pass your current asset version to `render`; when a GET request carries a stale `X-Inertia-Version`, the core responds with `409 Conflict` carrying `X-Inertia-Location` and the current `X-Inertia-Version`, and the client performs a full reload:

```scala
InertiaCask.render(req, "Home", props, version = assetVersion)
```

### Custom HTML layout

`layoutFn` receives the `<script data-page="app" type="application/json">` page-data element followed by the `<div id="app">` mount element, and wraps them in your full HTML document — this is where you load your frontend bundle:

```scala
InertiaCask.render(req, "Home", props, layoutFn = myLayout)
```

### Partial reloads

Nothing to do on your side: when the client requests a partial reload (`only` / `except`), `render` filters the props automatically. `errors` is always kept.

### Using Tapir instead of Cask

Add `.in(InertiaTapir.inertiaHeadersInput)` and `.out(InertiaTapir.inertiaOutput)` to an endpoint and call `InertiaTapir.render` in the server logic:

```scala
import dev.capslock.inertia.tapir.*
import sttp.tapir.*

val indexEndpoint = endpoint.get
  .in("")
  .in(InertiaTapir.inertiaHeadersInput)
  .out(InertiaTapir.inertiaOutput)
  .serverLogicSuccess[Future] { headers =>
    Future.successful(
      InertiaTapir.render(headers, "/", "GET", "Home", props)
    )
  }
```

### Bringing your own JSON library

Implement a `given JsonObject[MyProps]` instance — the core and the framework integrations work with any `P: JsonObject`. `examples/tapir/BorerProps.scala` is a worked example backed by the borer DOM.

## Data Types

| Type | Module | Description |
| --- | --- | --- |
| `JsonObject[P]` | core | Typeclass abstracting JSON operations (`empty`, `merge`, `filterKeys`, `toJsonObjectString`, `errors`). The core depends only on this. |
| `Props` | core | The default props type: `Map[String, RawJson]`. A `given JsonObject[Props]` is provided. |
| `RawJson` | core | Opaque wrapper around a pre-serialized JSON value. Built with `RawJson.of[A](a)` (needs a `JsonValueCodec[A]`) or `RawJson.raw(jsonString)`. |
| `InertiaRequest` | core | Abstraction over the incoming HTTP request (headers, method, URL). Framework integrations implement it. |
| `InertiaResult[P]` | core | ADT returned by `InertiaCore.render`: `InertiaJson`, `InertiaHtml`, `Conflict`, `Redirect`. |
| `InertiaPage[P]` | core | The Inertia page object (`component`, `props`, `url`, `version`). |
| `InertiaHeaders` | tapir | Inertia request headers extracted by `inertiaHeadersInput`. |
| `InertiaResponse` | tapir | Status code + body + headers, carried by `inertiaOutput`. |

## Internals

```
root
├── core/   → inertia-core    (JVM + JS + Native)
├── cask/   → inertia-cask    (JVM only)
├── tapir/  → inertia-tapir   (JVM + JS + Native)
└── examples/
```

The framework-agnostic core lives in `dev.capslock.inertia.core`. Its entry point, `InertiaCore.render`, processes a request in this order:

1. On a GET Inertia request with a stale asset version, return `Conflict` immediately.
2. Merge `sharedProps` and page props (page props win).
3. If the request is a partial reload for the same component, apply the `only` / `except` filters.
4. Merge in the `errors` object after filtering, so it is never filtered out. It is always present (`{}` when empty), nested under the error bag when `X-Inertia-Error-Bag` is set.
5. Return `InertiaJson` for Inertia requests, `InertiaHtml` otherwise.

The framework integrations (`InertiaCask`, `InertiaTapir`) only adapt requests into `InertiaRequest` and pattern-match the `InertiaResult` into framework-native responses — all protocol logic stays in the core.

In the default jsoniter-scala backend, each prop value is stored as a pre-serialized JSON byte array (`RawJson`). Merging props is a plain `Map ++`, and the final page JSON is assembled by concatenating the stored bytes, so values are serialized exactly once.

## License

BSD-3-Clause
