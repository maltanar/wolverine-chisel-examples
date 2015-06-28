scalaVersion := "2.10.4"

addSbtPlugin("com.github.scct" % "sbt-scct" % "0.2")

libraryDependencies += "edu.berkeley.cs" %% "chisel" % "latest.release"

unmanagedSourceDirectories in Compile <++= baseDirectory { base =>
  Seq(
    base / "common",
    base / "fpga-tidbits/on-chip-memory",
    base / "fpga-tidbits/interfaces",
    base / "fpga-tidbits/streams",
    base / "fpga-tidbits/regfile",
    base / "fpga-tidbits/dma",
    base / "aeg-to-aeg",
    base / "multichan",
    base / "mem-sum",
    base / "bram-test"
  )
}
