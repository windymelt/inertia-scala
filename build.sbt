import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val scala3Version = "3.3.7"

ThisBuild / scalaVersion := scala3Version
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .aggregate(core.jvm, core.js, `inertia-cask`, example)
  .settings(
    name := "inertia-scala",
    publish / skip := true
  )

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "inertia-core",
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % "2.38.9",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.2.4" % Test
  )

lazy val `inertia-cask` = project
  .in(file("cask"))
  .dependsOn(core.jvm)
  .settings(
    name := "inertia-cask",
    libraryDependencies += "com.lihaoyi" %% "cask" % "0.11.3",
    libraryDependencies += "org.scalameta" %% "munit" % "1.2.4" % Test
  )

lazy val example = project
  .in(file("example"))
  .dependsOn(`inertia-cask`)
  .settings(
    name := "inertia-example",
    publish / skip := true,
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.38.9",
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.9" % "compile-internal"
  )
