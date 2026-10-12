#!/usr/bin/env python3
"""Local mock tests for the Central remote-preflight client."""

from __future__ import annotations

import importlib.util
import json
import os
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("central_remote_preflight.py")
WORKFLOW = Path(__file__).parents[1] / "workflows" / "release-preflight.yml"
SPEC = importlib.util.spec_from_file_location("central_remote_preflight", SCRIPT)
assert SPEC and SPEC.loader
CLIENT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLIENT)


def populate_repository(repository: Path, version: str = "0.1.0") -> None:
    for module in CLIENT.MODULES:
        directory = repository / CLIENT.GROUP_PATH / module / version
        directory.mkdir(parents=True)
        for artifact in CLIENT.required_artifact_names(module, version):
            (directory / artifact).write_text("fixture", encoding="utf-8")


class CentralHandler(BaseHTTPRequestHandler):
    upload_count = 0
    state = "VALIDATED"
    transient_status_failures = 0
    query = ""

    def do_POST(self) -> None:  # noqa: N802
        if self.path.startswith("/api/v1/publisher/upload"):
            type(self).upload_count += 1
            type(self).query = self.path
            self.send_response(201)
            self.end_headers()
            self.wfile.write(b"deployment-123")
            return
        if self.path.startswith("/api/v1/publisher/status"):
            if type(self).transient_status_failures:
                type(self).transient_status_failures -= 1
                self.send_error(503)
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"deploymentState": type(self).state}).encode())
            return
        self.send_error(404)

    def log_message(self, format: str, *args: object) -> None:  # noqa: A003
        return


class CentralRemotePreflightTest(unittest.TestCase):
    def setUp(self) -> None:
        CentralHandler.upload_count = 0
        CentralHandler.state = "VALIDATED"
        CentralHandler.transient_status_failures = 0
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), CentralHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self) -> None:
        self.server.shutdown()
        self.thread.join()
        self.server.server_close()

    def test_user_managed_upload_reaches_validated_without_publish(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository = root / "repository"
            bundle = root / "bundle.zip"
            populate_repository(repository)
            CLIENT.create_bundle(repository, bundle, "0.1.0")
            deployment = CLIENT.upload_bundle(self.base_url, bundle, "Bearer fake", "test")
            CLIENT.wait_for_validation(self.base_url, deployment, "Bearer fake", 0, 1)
            self.assertEqual("deployment-123", deployment)
            self.assertIn("publishingType=USER_MANAGED", CentralHandler.query)
            self.assertEqual(1, CentralHandler.upload_count)

    def test_failed_status_is_actionable(self) -> None:
        CentralHandler.state = "FAILED"
        with self.assertRaisesRegex(CLIENT.PreflightError, "inspect it in the Portal"):
            CLIENT.wait_for_validation(self.base_url, "deployment-123", "Bearer fake", 0, 1)

    def test_transient_status_failure_retries_without_another_upload(self) -> None:
        CentralHandler.transient_status_failures = 1
        CLIENT.wait_for_validation(self.base_url, "deployment-123", "Bearer fake", 0, 2)
        self.assertEqual(0, CentralHandler.upload_count)

    def test_missing_artifacts_are_rejected_before_upload(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(CLIENT.PreflightError, "Central bundle is incomplete"):
                CLIENT.create_bundle(Path(temporary), Path(temporary) / "bundle.zip", "0.1.0")
        self.assertEqual(0, CentralHandler.upload_count)

    def test_upload_is_not_retried_after_an_uncertain_outcome(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repository = root / "repository"
            bundle = root / "bundle.zip"
            populate_repository(repository)
            CLIENT.create_bundle(repository, bundle, "0.1.0")
            with self.assertRaises(CLIENT.PreflightError):
                CLIENT.upload_bundle("http://127.0.0.1:1", bundle, "Bearer fake", "test")
        self.assertEqual(0, CentralHandler.upload_count)

    def test_invalid_version_is_rejected_before_credentials_or_network(self) -> None:
        with patch.object(sys, "argv", [str(SCRIPT), "--repository", "ignored", "--bundle", "ignored.zip", "--version", "0.2.0"]):
            with self.assertRaisesRegex(CLIENT.PreflightError, "only supports release version 0.1.0"):
                CLIENT.main()

    def test_missing_credentials_are_rejected_without_upload(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with patch.dict(os.environ, {}, clear=True):
                with patch.object(
                    sys,
                    "argv",
                    [str(SCRIPT), "--repository", str(root), "--bundle", str(root / "bundle.zip"), "--version", "0.1.0"],
                ):
                    with self.assertRaisesRegex(CLIENT.PreflightError, "Missing required Maven Central credential inputs"):
                        CLIENT.main()
        self.assertEqual(0, CentralHandler.upload_count)

    def test_workflow_requires_main_and_never_declares_an_irreversible_step(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        client = SCRIPT.read_text(encoding="utf-8")
        protected_job = workflow.split("  protected-remote-preflight:", maxsplit=1)[1].split("    steps:", maxsplit=1)[0]
        self.assertIn("environment: production-release", workflow)
        self.assertIn("github.ref == 'refs/heads/main'", workflow)
        self.assertIn("ref: ${{ github.sha }}", workflow)
        self.assertNotIn("MAVEN_CENTRAL_USERNAME", protected_job)
        self.assertIn("publishPlugins --validate-only", workflow)
        self.assertIn("if: ${{ false }}", workflow)
        self.assertNotIn("/api/v1/publisher/deployment/", client)
        self.assertNotIn("AUTOMATIC", client)


if __name__ == "__main__":
    unittest.main()
