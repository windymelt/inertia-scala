# TODO — Inertia.js プロトコル未実装機能

Inertia.js 公式プロトコル仕様（https://inertiajs.com/the-protocol）と現在の実装
（`core/src/main/scala/dev/capslock/inertia/core/Core.scala`）を照合した結果の未実装項目です。

## 実装済み（参考）

- 初期 HTML レスポンスと Inertia JSON レスポンスの出し分け
- `X-Inertia` / `Vary: X-Inertia` レスポンスヘッダー
- 資産バージョン不一致時の `409 Conflict` + `X-Inertia-Location`（**GET 限定**）
- partial reload（`X-Inertia-Partial-Component` 照合、`only` / `except` フィルタ）
- shared props のマージ
- `POST/PUT/PATCH/DELETE` の 302→303 リダイレクト正規化
- **バリデーションエラーの `errors` プロパティ**（常に props に含める。空なら `{}`）
- **エラーバッグ**（`X-Inertia-Error-Bag` ヘッダー）
- **フラグメント付きリダイレクト**（`409` + `X-Inertia-Redirect`）

---

## 完了した高優先度項目（2026-06-12）

以下は実装済み。`core/InertiaCoreSuite` でテスト済み。

### 1. バリデーションエラーの `errors` プロパティ ✅

`JsonObject` typeclass に `errors(messages, errorBag)` を追加し、`render` が partial
reload を含め常に `errors` を props にマージするようにした。エラーが無い場合は `{}`。

### 2. エラーバッグ（`X-Inertia-Error-Bag`） ✅

`InertiaRequest` に `errorBag: Option[String]` を追加し、Cask / Tapir でヘッダーを抽出。
エラーをバッグ名配下にネストする（`{errors: {bag: {...}}}`）。
Cask サンプルの `Users/Show` でプロフィール更新・パスワード変更の 2 フォームを
`router.post(..., { errorBag })` で送り分けて実演している。

### 3. フラグメント付きリダイレクト（`X-Inertia-Redirect`） ✅

`RedirectPlan` ADT と `InertiaCore.planRedirect` を追加。Inertia リクエストかつ遷移先に
`#` を含む場合に `409` + `X-Inertia-Redirect` を返す。Cask / Tapir の `redirect` ヘルパーが利用する。

### A. バージョン競合の GET 限定化 ✅

`render` のバージョン不一致チェックを GET リクエストのみに制限した（公式 inertia-laravel
の `Middleware.php` 準拠）。非 GET ではバージョン差異でリダイレクトを発生させない。

---

## 優先度: 中（モダンな機能・UX 向上）

### 4. Deferred props（遅延ロード props）

初回レスポンスから除外し、クライアントが後続リクエストで取得する props。
ページオブジェクトに `deferredProps`（グループ名→プロパティキーのマップ）を出力する。

- 失敗した deferred prop を示す `rescuedProps`（配列）も対象
- props を「即時評価」と「遅延評価」に区別する API 設計が必要
（現在の `Props` は事前シリアライズ済みのため、評価遅延の仕組みを別途設計する）

### 5. Merge props（`mergeProps` / `prependProps` / `deepMergeProps` / `matchPropsOn`）

クライアント側で既存 props に追記・先頭追加・深いマージを行うための指示。
ページオブジェクトに対応する配列キーを出力する。

- 無限スクロールやページネーション蓄積で利用される

### 6. Once props（`onceProps` / `X-Inertia-Except-Once-Props`）

複数ページ間で再利用される props。リクエストヘッダー
`X-Inertia-Except-Once-Props` で既ロード済みキーを受け取り、該当 props の解決とレスポンス出力を省略する。
ページオブジェクトに `onceProps`（プロパティ名→任意の失効タイムスタンプ）を出力する。

### 7. 履歴暗号化（`encryptHistory` / `clearHistory`）

ページオブジェクトに真偽値 `encryptHistory` / `clearHistory` を出力する。
機密データを含むページで履歴ステートを暗号化・クリアする指示。

### 8. `preserveFragment`

ページオブジェクトに `preserveFragment`（真偽値）を出力し、URL フラグメントの保持を制御する。

### 9. `X-Inertia-Reset` ヘッダー

ナビゲーション時にリセットすべき props を示すリクエストヘッダー。merge props と併用される。

---

## 優先度: 低（特定ユースケース）

### 10. 無限スクロール（`scrollProps` / `X-Inertia-Infinite-Scroll-Merge-Intent`）

ページオブジェクトの `scrollProps` 設定と、append/prepend を指示するリクエストヘッダー
`X-Inertia-Infinite-Scroll-Merge-Intent` の処理。merge props（項目 5）に依存する。

### 11. Precognition バリデーション

`Precognition: true` / `Precognition-Validate-Only` ヘッダーを受け取り、
バリデーションのみ実行する。成功時 `204 No Content` + `Precognition-Success: true`、
失敗時 `422 Unprocessable Entity` + エラー詳細を返す。`Vary: Precognition` も必要。

- `InertiaResult` に `204` / `422` ケースの追加が必要

### 12. フラッシュメッセージの reflash

`409` 競合時にセッションのフラッシュデータを再フラッシュする。
コアはフレームワーク非依存のためセッション機構を持たず、各統合（Cask / Tapir）側での実装になる。

---

## 確認・検討事項（仕様適合性）

### A. バージョン競合の対象メソッド ✅ 対応済み

公式 inertia-laravel の `Middleware.php` で `$request->method() === 'GET'` に限定されている
ことを確認し、`render` を GET 限定に修正した。

### B. 資産バージョンの算出ヘルパー

現在 `version` は呼び出し側が文字列で渡す前提。マニフェストのハッシュ等から
バージョンを算出するヘルパーを提供するかは要検討（仕様上は必須ではない）。

### C. ページオブジェクトの `sharedProps` トップレベルキー

現在 shared props は `props` にマージするのみで、ページオブジェクトに
`sharedProps`（トップレベルキー）として別途出力していない。最新クライアントの
instant visits 最適化に必要かどうかを公式アダプター実装と照合する。
