// projectMatrix is in-sourced in sbt 2.x, so no separate crossproject plugin is needed
addSbtPlugin("org.scala-js"     % "sbt-scalajs"      % "1.22.0")
addSbtPlugin("com.github.sbt"   % "sbt-ci-release"   % "1.12.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"     % "2.6.2")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"     % "0.14.7")
