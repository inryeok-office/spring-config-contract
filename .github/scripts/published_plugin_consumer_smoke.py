#!/usr/bin/env python3
"""Verify released runtime modules and a published Gradle plugin from fresh consumers."""

from __future__ import annotations

import argparse
import subprocess
import tempfile
from pathlib import Path


VERSION = "0.1.0"
PLUGIN_ID = "io.github.inryeok-office.config-contract"
CENTRAL_MODULES = (
    "config-contract-core",
    "config-contract-spring",
    "config-contract-deployment",
)


class SmokeTestError(RuntimeError):
    """Raised when a released artifact cannot be resolved or exercised."""


def run_gradle(gradle: Path, project: Path, gradle_home: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(gradle), "--no-daemon", "--gradle-user-home", str(gradle_home), "-p", str(project), *arguments],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
    )


def require_success(result: subprocess.CompletedProcess[str], purpose: str) -> None:
    if result.returncode != 0:
        raise SmokeTestError(f"{purpose} failed.\n{result.stdout}")


def write_central_consumer(project: Path, version: str) -> None:
    project.mkdir(parents=True, exist_ok=True)
    (project / "settings.gradle.kts").write_text('rootProject.name = "central-resolution"\n', encoding="utf-8")
    dependencies = "\n".join(
        f'    implementation("io.github.inryeok-office:{module}:{version}")' for module in CENTRAL_MODULES
    )
    (project / "build.gradle.kts").write_text(
        f"""plugins {{
    java
}}

repositories {{
    mavenCentral()
}}

dependencies {{
{dependencies}
}}
""",
        encoding="utf-8",
    )


def write_plugin_consumer(project: Path, version: str, mirrored: bool) -> None:
    project.mkdir(parents=True, exist_ok=True)
    plugin_management = ""
    if mirrored:
        plugin_management = """pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

"""
    (project / "settings.gradle.kts").write_text(
        plugin_management + 'rootProject.name = "published-plugin-consumer"\n',
        encoding="utf-8",
    )
    (project / "build.gradle.kts").write_text(
        f"""plugins {{
    java
    id("{PLUGIN_ID}") version "{version}"
}}

repositories {{
    mavenCentral()
}}

dependencies {{
    compileOnly("org.springframework:spring-beans:6.2.19")
}}

configContract {{
    dotenvExampleFiles.from(".env.example")
}}
""",
        encoding="utf-8",
    )
    source = project / "src/main/java/example/RequiredSettings.java"
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text(
        """package example;

import org.springframework.beans.factory.annotation.Value;

public class RequiredSettings {
    @Value("${app.api-key}")
    private String apiKey;
}
""",
        encoding="utf-8",
    )


def verify_central(gradle: Path, workspace: Path, version: str) -> None:
    project = workspace / "central"
    gradle_home = workspace / "central-gradle-home"
    write_central_consumer(project, version)
    result = run_gradle(gradle, project, gradle_home, "--refresh-dependencies", "dependencies", "--configuration", "runtimeClasspath")
    require_success(result, "Public Maven Central resolution")
    for module in CENTRAL_MODULES:
        coordinate = f"io.github.inryeok-office:{module}:{version}"
        if coordinate not in result.stdout:
            raise SmokeTestError(f"Public Maven Central resolution did not report {coordinate}.")


def verify_plugin_consumer(gradle: Path, workspace: Path, version: str, mirrored: bool) -> None:
    variant = "mirrored" if mirrored else "default"
    project = workspace / f"plugin-{variant}"
    gradle_home = workspace / f"plugin-{variant}-gradle-home"
    write_plugin_consumer(project, version, mirrored)

    require_success(run_gradle(gradle, project, gradle_home, "classes"), f"{variant} Plugin Portal resolution")

    (project / ".env.example").write_text("LEGACY_FLAG=true\n", encoding="utf-8")
    failing = run_gradle(gradle, project, gradle_home, "configContractCheck")
    if failing.returncode == 0:
        raise SmokeTestError(f"{variant} consumer unexpectedly passed the missing/unused diagnostic fixture.")
    for expected in ("MISSING app.api-key", "UNUSED  LEGACY_FLAG"):
        if expected not in failing.stdout:
            raise SmokeTestError(f"{variant} consumer did not report expected diagnostic: {expected}.")

    (project / ".env.example").write_text("APP_API_KEY=example\n", encoding="utf-8")
    passing = run_gradle(gradle, project, gradle_home, "configContractCheck")
    require_success(passing, f"{variant} configContractCheck")
    if "Configuration contract check passed: no findings" not in passing.stdout:
        raise SmokeTestError(f"{variant} consumer did not report a successful contract check.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--mode", choices=("central", "default", "mirrored", "all"), default="all")
    parser.add_argument("--workspace", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.version != VERSION:
        raise SmokeTestError(f"Published consumer smoke tests only support release version {VERSION}.")
    if not args.gradle.is_file():
        raise SmokeTestError("The checked-in Gradle Wrapper path does not exist.")

    temporary = None
    if args.workspace is None:
        temporary = tempfile.TemporaryDirectory(prefix="spring-config-contract-published-consumer-")
        workspace = Path(temporary.name)
    else:
        workspace = args.workspace
        workspace.mkdir(parents=True, exist_ok=True)
    try:
        if args.mode in {"central", "all"}:
            verify_central(args.gradle, workspace, args.version)
        if args.mode in {"default", "all"}:
            verify_plugin_consumer(args.gradle, workspace, args.version, mirrored=False)
        if args.mode in {"mirrored", "all"}:
            verify_plugin_consumer(args.gradle, workspace, args.version, mirrored=True)
    finally:
        if temporary is not None:
            temporary.cleanup()
    print(f"Published consumer smoke tests passed for {args.version}.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SmokeTestError as error:
        print(f"Published consumer smoke test failed: {error}")
        raise SystemExit(1)
