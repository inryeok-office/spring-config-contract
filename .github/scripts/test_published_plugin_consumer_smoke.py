#!/usr/bin/env python3
"""Unit tests for the published-consumer smoke-test fixture generator."""

from __future__ import annotations

import importlib.util
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("published_plugin_consumer_smoke.py")
WORKFLOW = Path(__file__).parents[1] / "workflows" / "plugin-portal-publication.yml"
SPEC = importlib.util.spec_from_file_location("published_plugin_consumer_smoke", SCRIPT)
assert SPEC and SPEC.loader
SMOKE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SMOKE)


class PublishedPluginConsumerSmokeTest(unittest.TestCase):
    def test_relative_wrapper_path_resolves_before_running_in_external_consumer_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            wrapper = root / "wrapper path" / "gradlew"
            wrapper.parent.mkdir()
            wrapper.touch()
            consumer = root / "external consumer"
            consumer.mkdir()
            gradle_home = root / "gradle home"

            previous_directory = Path.cwd()
            try:
                os.chdir(root)
                resolved = SMOKE.resolve_gradle_wrapper(Path("wrapper path") / "gradlew")
            finally:
                os.chdir(previous_directory)

            with patch.object(SMOKE.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "")) as run:
                SMOKE.run_gradle(resolved, consumer, gradle_home, "help")

            command = run.call_args.args[0]
            self.assertEqual(str(wrapper.resolve()), command[0])
            self.assertTrue(Path(command[0]).is_absolute())
            self.assertIn(str(consumer), command)

    def test_absolute_gradle_wrapper_path_is_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            wrapper = Path(temporary) / "gradlew"
            wrapper.touch()

            self.assertEqual(wrapper.resolve(), SMOKE.resolve_gradle_wrapper(wrapper.resolve()))

    def test_windows_batch_wrapper_path_is_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            wrapper = Path(temporary) / "wrapper path" / "gradlew.bat"
            wrapper.parent.mkdir()
            wrapper.touch()

            resolved = SMOKE.resolve_gradle_wrapper(wrapper)

            with (
                patch.object(SMOKE.sys, "platform", "win32"),
                patch.object(SMOKE.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "")) as run,
            ):
                SMOKE.run_gradle(resolved, Path(temporary) / "external consumer", Path(temporary) / "gradle home", "help")

            self.assertEqual(".bat", resolved.suffix)
            self.assertEqual(wrapper.resolve(), resolved)
            command = run.call_args.args[0]
            self.assertIsInstance(command, str)
            self.assertIn(f'"{resolved}"', command)
            self.assertTrue(run.call_args.kwargs["shell"])

    def test_central_consumer_uses_only_maven_central_and_all_runtime_modules(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            project = Path(temporary) / "central"
            SMOKE.write_central_consumer(project, "0.1.0")
            build = (project / "build.gradle.kts").read_text(encoding="utf-8")
            self.assertIn("mavenCentral()", build)
            self.assertNotIn("mavenLocal()", build)
            for module in SMOKE.CENTRAL_MODULES:
                self.assertIn(f"io.github.inryeok-office:{module}:0.1.0", build)

    def test_plugin_consumers_cover_default_and_mirrored_resolution_without_source_builds(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            default = root / "default"
            mirrored = root / "mirrored"
            SMOKE.write_plugin_consumer(default, "0.1.0", mirrored=False)
            SMOKE.write_plugin_consumer(mirrored, "0.1.0", mirrored=True)
            default_settings = (default / "settings.gradle.kts").read_text(encoding="utf-8")
            mirrored_settings = (mirrored / "settings.gradle.kts").read_text(encoding="utf-8")
            build = (default / "build.gradle.kts").read_text(encoding="utf-8")
            self.assertNotIn("pluginManagement", default_settings)
            self.assertIn("gradlePluginPortal()", mirrored_settings)
            self.assertIn("mavenCentral()", mirrored_settings)
            self.assertIn('id("io.github.inryeok-office.config-contract") version "0.1.0"', build)
            self.assertNotIn("includeBuild", default_settings + build)
            self.assertNotIn("mavenLocal()", default_settings + mirrored_settings + build)

    def test_plugin_smoke_requires_missing_unused_and_passing_contract_outcomes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            calls: list[tuple[str, ...]] = []

            def fake_run(_: Path, __: Path, ___: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
                calls.append(arguments)
                if arguments == ("configContractCheck",) and len(calls) == 2:
                    return subprocess.CompletedProcess([], 1, "MISSING app.api-key\nUNUSED  LEGACY_FLAG\n")
                return subprocess.CompletedProcess([], 0, "Configuration contract check passed: no findings\n")

            with patch.object(SMOKE, "run_gradle", side_effect=fake_run):
                SMOKE.verify_plugin_consumer(Path(__file__), workspace, "0.1.0", mirrored=False)
            self.assertEqual(("classes",), calls[0])
            self.assertEqual(("configContractCheck",), calls[1])
            self.assertEqual(("configContractCheck",), calls[2])

    def test_workflow_is_manual_protected_and_never_republishes_central(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        protected_job = workflow.split("  protected-plugin-portal-publication:", maxsplit=1)[1]
        self.assertIn("workflow_dispatch:", workflow)
        self.assertNotIn("pull_request:", workflow)
        self.assertNotIn("push:", workflow)
        self.assertIn("environment: production-release", protected_job)
        self.assertIn("github.ref == 'refs/heads/main'", protected_job)
        self.assertIn("ref: ${{ github.sha }}", protected_job)
        self.assertIn("publishPlugins -PreleaseVersion", protected_job)
        self.assertNotIn("publishPlugins --validate-only", protected_job)
        self.assertNotIn("MAVEN_CENTRAL_USERNAME", protected_job)
        self.assertNotIn("MAVEN_CENTRAL_PASSWORD", protected_job)
        self.assertNotIn("SIGNING_KEY", protected_job)
        self.assertNotIn("SIGNING_PASSWORD", protected_job)
        self.assertIn("cancel-in-progress: false", workflow)
        self.assertIn("GRADLE_PUBLISH_KEY", protected_job)
        self.assertIn("GRADLE_PUBLISH_SECRET", protected_job)


if __name__ == "__main__":
    unittest.main()
