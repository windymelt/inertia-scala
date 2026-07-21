# inertia-scala

*English version: [README.md](./README.md)*

[Inertia.js](https://inertiajs.com/) プロトコルの Scala 3 実装です。サーバーサイドのルーティングとコントローラーを使いながら SPA を構築できるサーバーサイドアダプターです。コアは特定の JSON ライブラリや HTTP フレームワークに依存せず、フレームワークと JSON バックエンドは typeclass 経由で差し込みます。

- ライセンス: BSD-3-Clause

## 機能

[Inertia.js プロトコル](https://inertiajs.com/the-protocol)のうち、以下が実装済みでテストされています。

- 初期 HTML レスポンスと Inertia JSON レスポンスの出し分け
- `X-Inertia` / `Vary: X-Inertia` レスポンスヘッダー
- 資産バージョン不一致時の `409 Conflict` + `X-Inertia-Location`（公式アダプターと同様に GET 限定）
- partial reload（`X-Inertia-Partial-Component` の照合と `only` / `except` フィルタ）
- shared props のマージ
- `POST` / `PUT` / `PATCH` / `DELETE` の `302` → `303` リダイレクト正規化
- `errors` プロパティ（常に props に含める。空なら `{}`）
- エラーバッグ（`X-Inertia-Error-Bag` ヘッダー）
- フラグメント付きリダイレクト（`409` + `X-Inertia-Redirect`）

## モジュール構成

```
root (aggregate)
├── core/   → inertia-core    (JVM + JS + Native)
├── cask/   → inertia-cask    (JVM のみ)
├── tapir/  → inertia-tapir   (JVM + JS + Native)
└── examples/
    ├── cask/    Cask 統合サンプルサーバー  (port 9000)
    └── tapir/   Tapir 統合サンプルサーバー (port 9001)
```

- `core` と `tapir` は `projectMatrix` で定義され、JVM / JS / Native にビルドされます。
- `cask` は JVM 専用です。
- フレームワーク依存（`cask`, `tapir-core`）は `Provided` スコープです。

## アーキテクチャ

コアは `dev.capslock.inertia.core` にあり、typeclass 抽象化によってフレームワーク非依存を保っています。

- **`JsonObject[P]` typeclass** — JSON 操作を抽象化します（`empty`, `merge`, `filterKeys`, `toJsonObjectString`, `errors`）。コアはこの trait のみに依存するため、JSON バックエンドを差し替えられます。
- **`InertiaRequest` trait** — HTTP リクエストの詳細（ヘッダー、メソッド、URL）を抽象化します。フレームワーク統合側がこれを実装します。
- **`InertiaResult[P]` ADT** — `InertiaJson`、`InertiaHtml`、`Conflict`、`Redirect` の 4 ケースです。呼び出し側がパターンマッチしてフレームワーク固有のレスポンスを生成します。
- **`InertiaCore.render`** — メインエントリーポイントです。リクエスト・コンポーネント名・props を受け取り `InertiaResult` を返します。バージョン競合検出・partial reload・props マージを処理します。

### jsoniter-scala 連携（`core/JsoniterProps.scala`）

props は事前シリアライズ済みの JSON バイト配列として保存され、マージ・出力時の再シリアライズを回避します。

- **`Props`**（`Map[String, RawJson]`）— 標準の props 型です。
- **`RawJson`** — `Array[Byte]` をラップする `opaque type` です。`RawJson.of[A](a)`（`JsonValueCodec` が必要）または `RawJson.raw(jsonString)` で構築します。
- `given JsonObject[Props]` インスタンスが提供されるため、`Props` は `InertiaCore.render` と直接連携します。
- 補助ヘルパー: `JsoniterProps.prop[A]`（型付き値）、`JsoniterProps.str`（文字列）

## ビルドコマンド

- コンパイル: `sbtn compile`
- 全テスト: `sbtn test`
- 単一スイート: `sbtn "testOnly dev.capslock.inertia.tapir.InertiaTapirSuite"`
- 継続コンパイル: `sbt ~compile`（継続モードのみ `sbt` を使用）

クロスビルドターゲット:

- JVM のみ: `sbtn coreJVM/compile`
- JS のみ: `sbtn coreJS/compile`
- 特定モジュール: `sbtn inertia-cask/compile`, `sbtn inertia-tapirJVM/compile`

### Scala Native のシステム依存

Native ターゲット（`coreNative` / `inertia-tapirNative`）のリンクには `clang` と `libidn2` の開発パッケージが必要です（Ubuntu: `libidn2-dev`、openSUSE: `libidn2-devel`）。`libidn2` が無いと inertia-tapir の Native リンクが `-lidn2` 不足で失敗します。

## 使い方

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

  // POST → 303 リダイレクト（フラグメント付きは 409 + X-Inertia-Redirect になる）
  @cask.post("/todos")
  def create(req: cask.Request) =
    InertiaCask.redirect(req, "/todos", 303)

  initialize()
```

バリデーションエラーは `errors` 引数で渡します。クライアントの `useForm` が `form.errors` として受け取ります。どのエラーバッグにネストされるかは、`X-Inertia-Error-Bag` ヘッダーからコアが自動判定します。

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
        props = /* JsonObject[P] を持つ値 */ ???
      )
    )
  }
```

エンドポイント定義に `.in(inertiaHeadersInput).out(inertiaOutput)` を追加し、サーバーロジックで `InertiaTapir.render` を呼びます。

## 新しい JSON バックエンドの追加

`given JsonObject[MyProps]` インスタンスを実装するだけです。フレームワーク側の変更は不要です。`examples/tapir/BorerProps.scala` が borer DOM を使った実装例です。

## サンプル

`examples/` ディレクトリに実行可能なサーバーがあります。

- `examples/cask` — Cask 統合サンプル（port 9000）
- `examples/tapir` — JSON を CBOR に変換する Tapir 統合サンプル（port 9001）

いずれもフロントエンドとして Vite dev server（port 5173 / 5174）を参照します。

## 技術スタック

- Scala 3.3.7、sbt 1.12.8
- JSON: jsoniter-scala-core（コア標準）、borer（Tapir サンプルで使用）
- テスト: MUnit
