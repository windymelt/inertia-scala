# inertia-scala

[![Latest version](https://index.scala-lang.org/windymelt/inertia-scala/inertia-core/latest.svg)](https://index.scala-lang.org/windymelt/inertia-scala/inertia-core)
[![Maven Central](https://maven-badges.sml.io/maven-central/dev.capslock/inertia-core_3/badge.svg)](https://maven-badges.sml.io/maven-central/dev.capslock/inertia-core_3/)

*English version: [README.md](./README.md)*

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
    // ... ユーザーを永続化 ...
    InertiaCask.redirect(req, "/")  // POST なので 303 に正規化される

  initialize()
```

inertia-scala は [Inertia.js](https://inertiajs.com/) プロトコルの Scala 3 サーバーサイドアダプターです。サーバーサイドのルーティングとコントローラーをそのまま使いながら、標準の Inertia クライアント（`@inertiajs/react`、`@inertiajs/vue3` など）で SPA を構築できます。REST / GraphQL のような API を別途作成する必要はありません。

コアは特定の JSON ライブラリや HTTP フレームワークに依存せず、どちらも typeclass によって差し替え可能です。[Cask](https://com-lihaoyi.github.io/cask/) と [Tapir](https://tapir.softwaremill.com/) の統合を同梱しており、デフォルトの JSON バックエンドとして jsoniter-scala ベースの props 型を提供しています。

## Getting Started

```scala
libraryDependencies ++= Seq(
  "dev.capslock" %% "inertia-core"  % "0.1.0",
  "dev.capslock" %% "inertia-cask"  % "0.1.0",  // Cask 統合
  "dev.capslock" %% "inertia-tapir" % "0.1.0"   // Tapir 統合
)
```

フレームワーク依存（`cask`、`tapir-core`）は `Provided` スコープなので、使うフレームワークは自分の依存に追加してください。`inertia-core` と `inertia-tapir` は JVM / Scala.js / Scala Native にクロスビルドされ、`inertia-cask` は JVM 専用です（Scala.js / Scala Native プロジェクトでは `%%%` を使ってください）。

フロントエンド側に Scala 固有の要素はありません。標準の Inertia.js クライアントをセットアップし、HTML レイアウト（後述の `layoutFn`）からフロントエンドのバンドルを読み込みます。初回リクエストにはサーバーが `data-page` ペイロード入りの HTML を返し、以降の遷移には JSON を返します。[プロトコル](https://inertiajs.com/the-protocol)どおりの挙動です。

実行可能なサンプルは `examples/` にあります。Cask サーバー（port 9000）と Tapir サーバー（port 9001）で、それぞれ Vite + React のフロントエンドと組み合わせています。

```console
$ sbtn example-cask/run                                    # バックエンド (:9000)
$ cd examples/cask-frontend && npm install && npm run dev  # フロントエンド (:5173)
```

## Cookbook

### 型付き props を渡す

`Props` の値は、jsoniter-scala の `JsonValueCodec` を持つ任意の型から作れます。

```scala
Props.of(
  "user"  -> prop(user),          // JsonValueCodec[A] を持つ任意の A
  "title" -> str("My page"),      // 文字列
  "count" -> RawJson.raw("42")    // シリアライズ済み JSON 断片
)
```

### 共有データ（shared props）

全ページで共有するデータ（現在のユーザー、フラッシュメッセージなど）は `sharedProps` に渡します。キーが衝突した場合はページ側の props が優先されます。

```scala
InertiaCask.render(req, "Dashboard", props, sharedProps = Some(shared))
```

### バリデーションエラーとエラーバッグ

バリデーション結果は `errors` に渡します。クライアントの `useForm` が `form.errors` として受け取ります。クライアントが `errorBag` オプション付きで送信した場合は、コアが `X-Inertia-Error-Bag` ヘッダーを読み取り、自動的にそのバッグ配下にネストします。

```scala
val errs = Map("email" -> "メールアドレスの形式が正しくありません")
InertiaCask.render(req, "Users/Edit", props, errors = errs)
```

### フォーム送信後のリダイレクト

`redirect` はプロトコルの要求どおり、`POST` / `PUT` / `PATCH` / `DELETE` の `301` / `302` を `303` に正規化します。遷移先にフラグメント（`#`）が含まれ、かつ Inertia リクエストの場合は、代わりに `409` + `X-Inertia-Redirect` を返します。

```scala
InertiaCask.redirect(req, "/todos")
```

### アセットのバージョン管理

現在のアセットバージョンを `render` に渡します。GET リクエストの `X-Inertia-Version` が古い場合、コアが `409 Conflict` + `X-Inertia-Location` を返し、クライアントがページ全体をリロードします。

```scala
InertiaCask.render(req, "Home", props, version = assetVersion)
```

### HTML レイアウトのカスタマイズ

`layoutFn` は `<div id="app" data-page="...">` のマークアップを受け取り、完全な HTML ドキュメントに包みます。フロントエンドのバンドルはここで読み込みます。

```scala
InertiaCask.render(req, "Home", props, layoutFn = myLayout)
```

### Partial reloads（ページの一部のみ再読み込みする機能）

アプリケーション側の対応は不要です。クライアントが partial reload（`only` / `except`）を要求すると、`render` が props を自動的にフィルタします。`errors` は常に保持されます。

### Cask の代わりに Tapir を使う

エンドポイントに `.in(InertiaTapir.inertiaHeadersInput)` と `.out(InertiaTapir.inertiaOutput)` を追加し、サーバーロジックで `InertiaTapir.render` を呼びます。

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

### 好みの JSON ライブラリを使う

`given JsonObject[MyProps]` インスタンスを実装するだけで、コアとフレームワーク統合は任意の `P: JsonObject` で動きます。`examples/tapir/BorerProps.scala` が borer DOM を使った実装例です。

## Data Types

| 型 | モジュール | 説明 |
| --- | --- | --- |
| `JsonObject[P]` | core | JSON 操作を抽象化する typeclass です（`empty`、`merge`、`filterKeys`、`toJsonObjectString`、`errors`）。コアはこれのみに依存します。 |
| `Props` | core | デフォルトの props 型（`Map[String, RawJson]`）です。`given JsonObject[Props]` を提供します。 |
| `RawJson` | core | シリアライズ済み JSON 値の opaque ラッパーです。`RawJson.of[A](a)`（`JsonValueCodec[A]` が必要）または `RawJson.raw(jsonString)` で構築します。 |
| `InertiaRequest` | core | HTTP リクエスト（ヘッダー、メソッド、URL）の抽象化です。フレームワーク統合側が実装します。 |
| `InertiaResult[P]` | core | `InertiaCore.render` が返す ADT です。`InertiaJson`、`InertiaHtml`、`Conflict`、`Redirect` の 4 ケースがあります。 |
| `InertiaPage[P]` | core | Inertia のページオブジェクトです（`component`、`props`、`url`、`version`）。 |
| `InertiaHeaders` | tapir | `inertiaHeadersInput` が抽出する Inertia リクエストヘッダーです。 |
| `InertiaResponse` | tapir | `inertiaOutput` が運ぶステータスコード、ボディ、ヘッダーです。 |

## 内部構造

```
root
├── core/   → inertia-core    (JVM + JS + Native)
├── cask/   → inertia-cask    (JVM のみ)
├── tapir/  → inertia-tapir   (JVM + JS + Native)
└── examples/
```

フレームワーク非依存のコアは `dev.capslock.inertia.core` にあります。エントリーポイントの `InertiaCore.render` は、リクエストを次の順に処理します。

1. GET の Inertia リクエストでアセットバージョンが古ければ、即座に `Conflict` を返します。
2. `sharedProps` とページ props をマージします（ページ側が優先）。
3. 同一コンポーネントへの partial reload であれば `only` / `except` フィルタを適用します。
4. フィルタ後に `errors` オブジェクトをマージします。これにより `errors` はフィルタで落ちません。常に含まれ（空なら `{}`）、`X-Inertia-Error-Bag` があればバッグ配下にネストされます。
5. Inertia リクエストなら `InertiaJson`、そうでなければ `InertiaHtml` を返します。

フレームワーク統合（`InertiaCask`、`InertiaTapir`）は、リクエストを `InertiaRequest` に適合させ、`InertiaResult` をパターンマッチしてフレームワーク固有のレスポンスに変換するだけです。プロトコルのロジックはすべてコアにあります。

デフォルトの jsoniter-scala バックエンドでは、各 prop 値をシリアライズ済みの JSON バイト配列（`RawJson`）として保持します。props のマージは `Map` の `++` そのもので、最終的なページ JSON は保持済みバイト列の連結で組み立てるため、シリアライズは値ごとに 1 回だけ行われます。

## ライセンス

BSD-3-Clause
