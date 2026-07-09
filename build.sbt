val scala3Version = "3.3.7"
val tapirVersion  = "1.11.33"
val borerVersion  = "1.14.1"

// projectMatrix で JVM/JS へクロスビルドする。bare settings は全 subproject に適用されるため
// version は LocalRootProject にスコープする。
LocalRootProject / version := "0.1.0-SNAPSHOT"

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
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.4" % Test
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))
  .jsPlatform(scalaVersions = Seq(scala3Version))

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
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.4" % Test
  )
  .jvmPlatform(scalaVersions = Seq(scala3Version))
  .jsPlatform(scalaVersions = Seq(scala3Version))

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
