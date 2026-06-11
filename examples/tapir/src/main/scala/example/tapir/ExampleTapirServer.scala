package example.tapir

import dev.capslock.inertia.tapir.*
import example.tapir.BorerProps.{Props, str, nil, given}
import io.bullet.borer.{Cbor, Dom, Json}
import sttp.tapir.*
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}

import java.util.Base64
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

object ExampleTapirServer:

  private val port = 9001

  // HTML layout referencing the Vite dev server on port 5174
  private def layout(content: String): String =
    s"""|<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |  <meta charset="UTF-8">
        |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
        |  <title>JSON → CBOR Converter</title>
        |  <script type="module">
        |    import RefreshRuntime from "http://localhost:5174/@react-refresh"
        |    RefreshRuntime.injectIntoGlobalHook(window)
        |    window.$$RefreshReg$$ = () => {}
        |    window.$$RefreshSig$$ = () => (type) => type
        |    window.__vite_plugin_react_preamble_installed__ = true
        |  </script>
        |  <script type="module" src="http://localhost:5174/@vite/client"></script>
        |  <script type="module" src="http://localhost:5174/src/main.jsx"></script>
        |</head>
        |<body>
        |  $content
        |</body>
        |</html>""".stripMargin

  // ── Endpoints ───────────────────────────────────────────────────────────

  val indexEndpoint = endpoint.get
    .in("")
    .in(InertiaTapir.inertiaHeadersInput)
    .out(InertiaTapir.inertiaOutput)
    .serverLogicSuccess[Future] { headers =>
      Future.successful(
        InertiaTapir.render(
          headers, "/", "GET", "Converter",
          Props.of(
            "input"      -> str("""{"hello": "world", "number": 42, "nested": {"key": true}}"""),
            "cborHex"    -> nil,
            "cborBase64" -> nil
          ),
          layoutFn = layout
        )
      )
    }

  val convertEndpoint = endpoint.post
    .in("convert")
    .in(InertiaTapir.inertiaHeadersInput)
    .in(stringJsonBody)
    .out(InertiaTapir.inertiaOutput)
    .serverLogicSuccess[Future] { (headers, body) =>
      Future.successful {
        val jsonInput = extractField(body, "jsonInput")
        convertJsonToCbor(headers, jsonInput)
      }
    }

  /** Parse the Inertia useForm body and extract a string field. */
  private def extractField(body: String, field: String): String =
    try
      val dom = Json.decode(body.getBytes("UTF-8")).to[Dom.Element].value
      dom match
        case m: Dom.MapElem =>
          m.toMap.collectFirst {
            case (Dom.StringElem(k), Dom.StringElem(v)) if k == field => v
          }.getOrElse("")
        case _ => ""
    catch case _: Exception => ""

  /** Convert JSON input to CBOR and return an Inertia response. */
  private def convertJsonToCbor(headers: InertiaHeaders, jsonInput: String): InertiaResponse =
    if jsonInput.isBlank then
      renderConverter(headers, jsonInput, error = Some("Please enter JSON input."))
    else
      try
        val dom       = Json.decode(jsonInput.getBytes("UTF-8")).to[Dom.Element].value
        val cborBytes = Cbor.encode(dom).toByteArray
        val hex       = cborBytes.map(b => f"$b%02x").mkString
        val base64    = Base64.getEncoder.encodeToString(cborBytes)
        renderConverter(headers, jsonInput, cborHex = Some(hex), cborBase64 = Some(base64))
      catch
        case e: Exception =>
          renderConverter(headers, jsonInput, error = Some(s"Invalid JSON: ${e.getMessage}"))

  private def renderConverter(
      headers: InertiaHeaders,
      input: String,
      cborHex: Option[String] = None,
      cborBase64: Option[String] = None,
      error: Option[String] = None
  ): InertiaResponse =
    // エラーは Inertia 標準の errors メカニズムで返す。クライアントの useForm が
    // errors.jsonInput として拾う。空メッセージの場合は errors: {} になる。
    InertiaTapir.render(
      headers, "/convert", "POST", "Converter",
      Props.of(
        "input"      -> str(input),
        "cborHex"    -> cborHex.map(str).getOrElse(nil),
        "cborBase64" -> cborBase64.map(str).getOrElse(nil)
      ),
      errors = error.map(msg => Map("jsonInput" -> msg)).getOrElse(Map.empty),
      layoutFn = layout
    )

  // ── Main ────────────────────────────────────────────────────────────────

  def main(args: Array[String]): Unit =
    println(s"Starting Tapir example server on http://localhost:$port")
    val binding: Future[NettyFutureServerBinding] =
      NettyFutureServer()
        .port(port)
        .addEndpoint(indexEndpoint)
        .addEndpoint(convertEndpoint)
        .start()
    Await.result(binding, Duration.Inf)
    println(s"Server started. Press Enter to stop.")
    scala.io.StdIn.readLine()
