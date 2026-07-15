// sbt 2.x では projectMatrix が in-source 化されているため crossproject プラグインは不要
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.12.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
