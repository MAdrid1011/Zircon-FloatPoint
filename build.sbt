// enablePlugins(VerilogPlugin)
scalaVersion := "2.13.16"
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
)
val chiselVersion = "6.7.0"
Test / parallelExecution := true
addCompilerPlugin ("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "org.typelevel" %% "spire" % "0.18.0"
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" %"6.0.0"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.17.0" % Test
libraryDependencies += "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test