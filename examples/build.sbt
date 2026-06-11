val scala3Version = "3.3.7"
val tapirVersion  = "1.11.33"
val borerVersion  = "1.14.1"

ThisBuild / scalaVersion := scala3Version

// Reference library projects from the parent build
lazy val inertiaCaskRef = ProjectRef(file(".."), "inertia-cask")
lazy val inertiaTapirJVMRef = ProjectRef(file(".."), "inertia-tapirJVM")

lazy val root = project
  .in(file("."))
  .aggregate(`example-cask`, `example-tapir`)
  .settings(
    name := "inertia-scala-examples",
    publish / skip := true
  )

lazy val `example-cask` = project
  .in(file("cask"))
  .dependsOn(inertiaCaskRef)
  .settings(
    name := "inertia-example-cask",
    publish / skip := true,
    libraryDependencies += "com.lihaoyi" %% "cask" % "0.11.3",
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % "2.38.9",
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.9" % "compile-internal"
  )

lazy val `example-tapir` = project
  .in(file("tapir"))
  .dependsOn(inertiaTapirJVMRef)
  .settings(
    name := "inertia-example-tapir",
    publish / skip := true,
    run / fork := true,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % tapirVersion,
      "io.bullet" %% "borer-core"       % borerVersion,
      "io.bullet" %% "borer-derivation" % borerVersion,
    )
  )
