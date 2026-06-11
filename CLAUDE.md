# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

inertia-scala は Inertia.js プロトコルの Scala 3 実装です。サーバーサイドルーティングとコントローラーを使いながら SPA を構築できるサーバーサイドアダプターで、コアは特定の JSON ライブラリや HTTP フレームワークに依存しません。

## Build Commands

- **Compile:** `sbtn compile`
- **Test all:** `sbtn test`
- **Test single suite:** `sbtn "testOnly dev.capslock.inertia.tapir.InertiaTapirSuite"` (完全修飾クラス名を使用)
- **Continuous compile:** `sbt ~compile` (インタラクティブ/継続モードのみ `sbt` を使用)
- **REPL:** `sbt console`

`sbtn`（thin client）を優先し、`sbt` は `~compile` 等の継続モードにのみ使用する。

### Cross-build targets

- **JVM のみビルド:** `sbtn coreJVM/compile`
- **JS のみビルド:** `sbtn coreJS/compile`
- **特定モジュール:** `sbtn inertia-cask/compile`, `sbtn inertia-tapirJVM/compile`

## Module Structure

```
root (aggregate)
├── core/           → inertia-core          (JVM + JS)
├── cask/           → inertia-cask          (JVM のみ)
├── tapir/          → inertia-tapir         (JVM + JS)
└── examples/
    ├── cask/       → Cask 統合サンプルサーバー (port 9000)
    └── tapir/      → Tapir 統合サンプルサーバー (port 9001)
```

- `core` と `tapir` は `crossProject(JVMPlatform, JSPlatform)` で定義されており JVM/JS の両方にビルドされる
- `cask` は JVM 専用 (`project` で定義)
- フレームワーク依存ライブラリ（cask, tapir-core）は `Provided` スコープ

## Tech Stack

- Scala 3.3.7, sbt 1.12.8
- JSON: jsoniter-scala-core（コア標準）、borer（Tapirサンプルで使用例あり）
- Testing: MUnit

## Architecture

コアはフレームワーク非依存で `dev.capslock.inertia.core` に存在します。typeclass 抽象化によって特定の JSON ライブラリや HTTP フレームワークへの依存を避けています。

### Key abstractions (`core/Core.scala`)

- **`JsonObject[P]` typeclass** — JSON 操作を抽象化（`empty`, `merge`, `filterKeys`, `toJsonObjectString`）。コアはこの trait のみに依存し、JSON バックエンドを差し替え可能にする。
- **`InertiaRequest` trait** — HTTP リクエストの詳細（ヘッダー、メソッド、URL）を抽象化。フレームワーク統合側がこれを実装する。
- **`InertiaResult[P]` ADT** — `InertiaJson`、`InertiaHtml`、`Conflict`、`Redirect` の 4 ケース。呼び出し側がパターンマッチしてフレームワーク固有の HTTP レスポンスを生成する。
- **`InertiaCore.render`** — メインエントリーポイント。リクエスト・コンポーネント名・props を受け取り `InertiaResult` を返す。バージョン競合検出・partial reload・props マージを処理する。

### jsoniter-scala integration (`core/JsoniterProps.scala`)

- **`Props` type** (`Map[String, RawJson]`) — props は事前シリアライズ済みの JSON バイト配列として保存することで、マージ・出力時の再シリアライズを回避する。
- **`RawJson` opaque type** — `Array[Byte]` のラッパー。`RawJson.of[A](a)`（`JsonValueCodec` が必要）または `RawJson.raw(jsonString)` で構築する。
- `given JsonObject[Props]` インスタンスを提供するため、`Props` は `InertiaCore.render` と直接連携する。
- 補助ヘルパー: `JsoniterProps.prop[A]`（型付き値）、`JsoniterProps.str`（文字列）

### Framework integrations

**Cask (`cask/InertiaCask.scala`)**
- `CaskInertiaRequest` が `cask.Request` を `InertiaRequest` にラップする
- `InertiaCask.render(req, component, props, ...)` — `cask.Response[String]` を返す
- `InertiaCask.redirect(req, location, status)` — POST→302 を 303 に正規化する

**Tapir (`tapir/InertiaTapir.scala`)**
- `InertiaTapir.inertiaHeadersInput` — Inertia ヘッダーを抽出する `EndpointInput[InertiaHeaders]`
- `InertiaTapir.inertiaOutput` — `InertiaResponse`（ステータス・ボディ・ヘッダー）を運ぶ `EndpointOutput`
- `InertiaTapir.render(headers, url, method, component, props, ...)` — `InertiaResponse` を返す
- エンドポイント定義に `.in(inertiaHeadersInput).out(inertiaOutput)` を追加してサーバーロジックで `InertiaTapir.render` を呼ぶだけで統合できる

### Adding a new JSON backend

`JsonObject[MyProps]` の `given` インスタンスを実装するだけでよい。`examples/tapir/BorerProps.scala` が borer DOM を使った実装例になっている。フレームワーク側の変更は不要。

### Code is in Japanese

コードベースのコメントとドキュメントは日本語で記述する。
