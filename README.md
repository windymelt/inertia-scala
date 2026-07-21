# inertia-scala

*Read this in [日本語](./README.ja.md).*

A Scala 3 server-side adapter that implements the [Inertia.js](https://inertiajs.com/) protocol. It lets you build single-page apps while keeping server-side routing and controllers. The core does not depend on any specific JSON library or HTTP framework; framework and JSON backends are plugged in through typeclasses.

- License: BSD-3-Clause

## Features

The following parts of the [Inertia.js protocol](https://inertiajs.com/the-protocol) are implemented and covered by tests:

- Serving the initial HTML response and the Inertia JSON response conditionally
- `X-Inertia` / `Vary: X-Inertia` response headers
- `409 Conflict` + `X-Inertia-Location` on asset version mismatch (GET only, matching the official adapter)
- Partial reloads (`X-Inertia-Partial-Component` matching with `only` / `except` filters)
- Merging shared props
- `302` → `303` redirect normalization for `POST` / `PUT` / `PATCH` / `DELETE`
- The `errors` prop (always included in props; `{}` when empty)
- Error bags (`X-Inertia-Error-Bag` header)
- Fragment-carrying redirects (`409` + `X-Inertia-Redirect`)

## Module structure

```
root (aggregate)
├── core/   → inertia-core    (JVM + JS + Native)
├── cask/   → inertia-cask    (JVM only)
├── tapir/  → inertia-tapir   (JVM + JS + Native)
└── examples/
    ├── cask/    Cask example server  (port 9000)
    └── tapir/   Tapir example server (port 9001)
```

- `core` and `tapir` are `projectMatrix` definitions and build for JVM / JS / Native.
- `cask` is JVM-only.
- Framework dependencies (`cask`, `tapir-core`) are in the `Provided` scope.

## Architecture

The core lives in `dev.capslock.inertia.core` and stays framework-agnostic through typeclass abstractions.

- **`JsonObject[P]` typeclass** — abstracts JSON operations (`empty`, `merge`, `filterKeys`, `toJsonObjectString`, `errors`). The core depends only on this trait, so the JSON backend is swappable.
- **`InertiaRequest` trait** — abstracts HTTP request details (headers, method, URL). Framework integrations implement it.
- **`InertiaResult[P]` ADT** — four cases: `InertiaJson`, `InertiaHtml`, `Conflict`, `Redirect`. Callers pattern-match to produce framework-specific responses.
- **`InertiaCore.render`** — the main entry point. Takes the request, component name, and props, and returns an `InertiaResult`. It handles version-conflict detection, partial reloads, and prop merging.

### jsoniter-scala integration (`core/JsoniterProps.scala`)

Props are stored as pre-serialized JSON byte arrays, which avoids re-serialization on merge and output.

- **`Props`** (`Map[String, RawJson]`) — the standard props type.
- **`RawJson`** — an `opaque type` wrapping `Array[Byte]`. Build it with `RawJson.of[A](a)` (requires a `JsonValueCodec`) or `RawJson.raw(jsonString)`.
- A `given JsonObject[Props]` instance is provided, so `Props` works directly with `InertiaCore.render`.
- Helpers: `JsoniterProps.prop[A]` (typed values) and `JsoniterProps.str` (strings).

## Build commands

- Compile: `sbtn compile`
- Test all: `sbtn test`
- Test a single suite: `sbtn "testOnly dev.capslock.inertia.tapir.InertiaTapirSuite"`
- Continuous compile: `sbt ~compile` (use `sbt` only for interactive / continuous modes)

Cross-build targets:

- JVM only: `sbtn coreJVM/compile`
- JS only: `sbtn coreJS/compile`
- Specific module: `sbtn inertia-cask/compile`, `sbtn inertia-tapirJVM/compile`

### Scala Native system dependencies

Linking the Native targets (`coreNative` / `inertia-tapirNative`) requires `clang` and the `libidn2` development package (Ubuntu: `libidn2-dev`, openSUSE: `libidn2-devel`). Without `libidn2`, the inertia-tapir Native link fails on the missing `-lidn2`.

## Usage

### Cask

```scala
import dev.capslock.inertia.core.{*, given}
import dev.capslock.inertia.core.JsoniterProps.*
import dev.capslock.inertia.cask.InertiaCask

object ExampleServer extends cask.MainRoutes:
  override def port: Int = 9000

  @cask.get("/")
  def index(req: cask.Request) =
    InertiaCask.render(
      req,
      component = "Home",
      props = Props.of(
        "greeting"  -> str("Welcome to inertia-scala!"),
        "userCount" -> RawJson.raw("3")
      )
    )

  // POST → 303 redirect (fragment-carrying redirects become 409 + X-Inertia-Redirect)
  @cask.post("/todos")
  def create(req: cask.Request) =
    InertiaCask.redirect(req, "/todos", 303)

  initialize()
```

Validation errors are passed via the `errors` argument. The client's `useForm` picks them up as `form.errors`. Which error bag they are nested under is decided automatically by the core from the `X-Inertia-Error-Bag` header.

### Tapir

```scala
import dev.capslock.inertia.tapir.*
import sttp.tapir.*

val indexEndpoint = endpoint.get
  .in("")
  .in(InertiaTapir.inertiaHeadersInput)
  .out(InertiaTapir.inertiaOutput)
  .serverLogicSuccess[Future] { headers =>
    Future.successful(
      InertiaTapir.render(
        headers, "/", "GET", "Home",
        props = /* your JsonObject[P] value */ ???
      )
    )
  }
```

Add `.in(inertiaHeadersInput).out(inertiaOutput)` to the endpoint definition and call `InertiaTapir.render` in the server logic.

## Adding a new JSON backend

Implement a `given JsonObject[MyProps]` instance — no framework-side changes are needed. `examples/tapir/BorerProps.scala` is a worked example using the borer DOM.

## Examples

The `examples/` directory contains runnable servers:

- `examples/cask` — a Cask integration sample (port 9000)
- `examples/tapir` — a Tapir integration sample that converts JSON to CBOR (port 9001)

Both reference a Vite dev server for the frontend (ports 5173 / 5174).

## Tech stack

- Scala 3.3.7, sbt 1.12.8
- JSON: jsoniter-scala-core (core standard), borer (used in the Tapir example)
- Testing: MUnit
