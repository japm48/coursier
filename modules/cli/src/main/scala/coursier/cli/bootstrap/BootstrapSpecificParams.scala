package coursier.cli.bootstrap

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.launcher.MergeRule
import coursier.launcher.internal.Windows

final case class BootstrapSpecificParams(
  output: Path,
  force: Boolean,
  standalone: Boolean,
  embedFiles: Boolean,
  javaOptions: Seq[String],
  assembly: Boolean,
  createBatFile: Boolean,
  assemblyRules: Seq[MergeRule],
  withPreamble: Boolean,
  deterministicOutput: Boolean,
  proguarded: Boolean,
  hybrid: Boolean,
  disableJarCheckingOpt: Option[Boolean]
) {
  import BootstrapSpecificParams.BootstrapPackaging
  def batOutput: Path =
    output.getParent.resolve(output.getFileName.toString + ".bat")
  def bootstrapPackaging: BootstrapPackaging =
    BootstrapPackaging(
      standalone,
      hybrid,
      embedFiles
    )
}

object BootstrapSpecificParams {
  def apply(options: BootstrapSpecificOptions, native: Boolean): ValidatedNel[String, BootstrapSpecificParams] = {

    val validateOutputType = {
      val count = Seq(
        options.assembly.exists(identity),
        options.standalone.exists(identity),
        options.hybrid.exists(identity),
        native
      ).count(identity)
      if (count > 1)
        Validated.invalidNel("Only one of --assembly, --standalone, --hybrid, or --native, can be specified")
      else
        Validated.validNel(())
    }

    val output = Paths.get {
      options
        .output
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse("bootstrap")
    }.toAbsolutePath

    val createBatFile = options.bat.getOrElse(Windows.isWindows)

    val rulesV = options.assemblyRule.traverse { s =>
      val idx = s.indexOf(':')
      if (idx < 0)
        Validated.invalidNel(s"Malformed assembly rule: $s")
      else {
        val ruleName = s.substring(0, idx)
        val ruleValue = s.substring(idx + 1)
        ruleName match {
          case "append" => Validated.validNel(MergeRule.Append(ruleValue))
          case "append-pattern" => Validated.validNel(MergeRule.AppendPattern(ruleValue))
          case "exclude" => Validated.validNel(MergeRule.Exclude(ruleValue))
          case "exclude-pattern" => Validated.validNel(MergeRule.ExcludePattern(ruleValue))
          case _ => Validated.invalidNel(s"Unrecognized rule name '$ruleName' in rule '$s'")
        }
      }
    }

    val prependRules = if (options.defaultAssemblyRules) MergeRule.default else Nil

    val assembly = options.assembly.getOrElse(false)
    val standalone = options.standalone.getOrElse(false)
    val hybrid = options.hybrid.getOrElse(false)

    (validateOutputType, rulesV).mapN {
      (_, rules) =>
        val javaOptions = options.javaOpt
        BootstrapSpecificParams(
          output,
          options.force,
          standalone,
          options.embedFiles,
          javaOptions,
          assembly,
          createBatFile,
          prependRules ++ rules,
          options.preamble,
          options.deterministic,
          options.proguarded,
          hybrid,
          options.disableJarChecking
        )
    }
  }

  final case class BootstrapPackaging(
    standalone: Boolean,
    hybrid: Boolean,
    embedFiles: Boolean
  )
}