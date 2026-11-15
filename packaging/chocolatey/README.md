# MCFPP Chocolatey package

The package ID is `mcfpp`. It embeds the existing Windows release ZIP and frozen
project source archive, validates the release manifest's SHA-256 hashes, and
depends on `Temurin25`. Installation creates the `mcfpp` command; uninstallation
removes it. The compiler uses `JAVA_HOME` when set, otherwise Java on PATH.

## Pack

From the repository root, with Chocolatey installed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\packaging\chocolatey\pack.ps1
```

The default input is `build/releases/0.1.0`; output is
`build/chocolatey/mcfpp.0.1.0.nupkg`. For another frozen release, pass
`-ReleaseDirectory` and optionally `-OutputDirectory`. To create new release
artifacts first, use `gradlew.bat release` with JDK 25 and the Mcfpm SDK described
in [the CLI guide](../../docs/cli.md).

## Install and verify locally

In an administrator PowerShell terminal at the repository root:

```powershell
choco install mcfpp --version=0.1.0 --source="build/chocolatey;https://community.chocolatey.org/api/v2/" -y
# Open a new terminal so the JDK dependency's PATH changes are available.
mcfpp --version
mcfpp --help
mcfpp init hello
cd hello
mcfpp build --offline
choco uninstall mcfpp -y
```

If `JAVA_HOME` is already set, point it to JDK 25. Uninstalling `mcfpp` keeps
user projects and the separately installed JDK.

## Submit to the community repository

Configure the account's API key locally using the
[Chocolatey API key instructions](https://docs.chocolatey.org/en-us/create/commands/api-key/),
then submit the tested package:

```powershell
choco push .\build\chocolatey\mcfpp.0.1.0.nupkg --source=https://push.chocolatey.org/
```

Submission enters Chocolatey's moderation process. After approval, users install
with `choco install mcfpp -y` and upgrade with `choco upgrade mcfpp -y`.

Packaging references: [embedded ZIP extraction](https://docs.chocolatey.org/en-us/create/functions/get-chocolateyunzip/),
[command shims](https://docs.chocolatey.org/en-us/create/functions/install-binfile/),
[publishing](https://docs.chocolatey.org/en-us/create/commands/push/).

## Validation on 2026-11-08

`mcfpp.0.1.0.nupkg` (SHA-256
`19e4bac9cbce0c1fed87b83de010d2ea881ea862949cfc42c9a730a871099b53`)
was installed and uninstalled with Chocolatey 2.2.2 in an isolated directory
whose path contains spaces. The installed `mcfpp` command passed `--version`,
`--help`, `init hello`, and `build --offline`, producing `pack.mcmeta`.
An invalid option returned exit code 2 through the generated shim. Uninstall
removed the package and shim while retaining the user project.

This check used the existing JDK 25.0.4 and `--ignore-dependencies`; installation
of the Temurin25 dependency was not executed. The embedded ZIP matches the
[previous release evidence](../../docs/releases/0.1.0-compact.md). Local logs and
results are in `build/chocolatey-validation-d861dce15db24de490c593f9e463e05a`.
The package was successfully submitted to `https://push.chocolatey.org/` on
2026-11-08 after the account API key was configured. The repository reports
`PackageStatus: Submitted`, `PackageTestResultStatus: Pending`, and
`PackageValidationResultStatus: Pending`; approval is pending. Package page:
https://community.chocolatey.org/packages/mcfpp/0.1.0
