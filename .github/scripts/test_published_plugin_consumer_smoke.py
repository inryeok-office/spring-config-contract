#!/usr/bin/env python3
"""Unit tests for the published-consumer smoke-test fixture generator."""

from __future__ import annotations

import importlib.util
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
