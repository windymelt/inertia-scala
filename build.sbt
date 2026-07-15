val scala3Version = "3.3.7"
val tapirVersion  = "1.11.33"
val borerVersion  = "1.14.1"

inThisBuild(List(
  organization := "dev.capslock",
  homepage := Some(url("https://github.com/windymelt/inertia-scala")),
  licenses := List("BSD-3-Clause" -> url("https://spdx.org/licenses/BSD-3-Clause.html")),
  developers := List(Developer("windymelt", "Windymelt", "windymelt@capslock.dev", url("https://www.3qe.us"))),
  scmInfo := Some(ScmInfo(url("https://github.com/windymelt/inertia-scala"), "scm:git:https://github.com/windymelt/inertia-scala.git")),
  versionScheme := Some("early-semver"),
  description := "Server-side adapter implementing the Inertia.js protocol for Scala 3, decoupled from any specific JSON library or HTTP framework."
))

lazy val root = (project in file("."))
  .aggregate(
    (core.projectRefs
      ++ `inertia-cask`.projectRefs
      ++ `inertia-tapir`.projectRefs
      ++ `example-cask`.projectRefs
      ++ `example-tapir`.projectRefs)*
  )
  .settings(
    name := "inertia-scala",
    publish / skip := true
  )

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "inertia-core",
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.38.9",
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.4" % Test,
    // munit_native0.5_3 1.2.4 は test-interface_native0.5_3 0.5.10 に依存するが、
    // sbt-scala-native 0.5.12 のツールチェーンは 0.5.12 を要求するため整合させる。
    // native アーティファクト名を明示した単一 % なので JVM/JS 軸には現れず無害。
    dependencyOverrides += "org.scala-native" % "test-interface_native0.5_3" % "0.5.12"
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))
  .jsPlatform(scalaVersions = Seq(scala3Version))
  .nativePlatform(scalaVersions = Seq(scala3Version))

lazy val `inertia-cask` = (projectMatrix in file("cask"))
  .dependsOn(core)
  .settings(
    name := "inertia-cask",
    libraryDependencies += "com.lihaoyi" %% "cask" % "0.11.3" % Provided,
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.4" % Test
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))

lazy val `inertia-tapir` = (projectMatrix in file("tapir"))
  .dependsOn(core)
  .settings(
    name := "inertia-tapir",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion % Provided,
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.4" % Test,
    // munit_native0.5_3 1.2.4 は test-interface_native0.5_3 0.5.10 に依存するが、
    // sbt-scala-native 0.5.12 のツールチェーンは 0.5.12 を要求するため整合させる。
    // tapir は独自に munit を持つため core 側の override は伝播せず、ここにも必要。
    // native アーティファクト名を明示した単一 % なので JVM/JS 軸には現れず無害。
    dependencyOverrides += "org.scala-native" % "test-interface_native0.5_3" % "0.5.12"
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))
  .jsPlatform(scalaVersions = Seq(scala3Version))
  .nativePlatform(scalaVersions = Seq(scala3Version))

lazy val `example-cask` = (projectMatrix in file("examples/cask"))
  .dependsOn(`inertia-cask`)
  .settings(
    name := "inertia-example-cask",
    publish / skip := true,
    libraryDependencies += "com.lihaoyi" %% "cask" % "0.11.3",
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.38.9",
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.9" % "compile-internal"
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))

lazy val `example-tapir` = (projectMatrix in file("examples/tapir"))
  .dependsOn(`inertia-tapir`)
  .settings(
    name := "inertia-example-tapir",
    publish / skip := true,
    run / fork := true,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % tapirVersion,
      "io.bullet" %% "borer-core"       % borerVersion,
      "io.bullet" %% "borer-derivation" % borerVersion
    )
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))
