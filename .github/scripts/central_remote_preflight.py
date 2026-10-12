#!/usr/bin/env python3
"""Create and validate a Maven Central USER_MANAGED deployment without publishing it."""

from __future__ import annotations

import argparse
import base64
import json
import os
import shutil
import sys
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


GROUP_PATH = Path("io/github/inryeok-office")
MODULES = (
    "config-contract-core",
    "config-contract-spring",
    "config-contract-deployment",
)


class PreflightError(RuntimeError):
    """Raised for a safe, actionable preflight failure."""


def required_artifact_names(module: str, version: str) -> tuple[str, ...]:
    base = f"{module}-{version}"
    artifacts = (
        f"{base}.jar",
        f"{base}-sources.jar",
        f"{base}-javadoc.jar",
        f"{base}.pom",
        f"{base}.module",
    )
    return artifacts + tuple(f"{artifact}.asc" for artifact in artifacts)


def validate_repository(repository: Path, version: str) -> None:
    missing: list[str] = []
    for module in MODULES:
        module_directory = repository / GROUP_PATH / module / version
        for artifact in required_artifact_names(module, version):
            if not (module_directory / artifact).is_file():
                missing.append(str(Path(module) / version / artifact))
    if missing:
        raise PreflightError("Central bundle is incomplete: " + ", ".join(missing))


def create_bundle(repository: Path, bundle: Path, version: str) -> None:
    validate_repository(repository, version)
    bundle.parent.mkdir(parents=True, exist_ok=True)
    if bundle.exists():
        bundle.unlink()
    archive_base = bundle.with_suffix("")
    shutil.make_archive(str(archive_base), "zip", root_dir=repository)
    created = archive_base.with_suffix(".zip")
    if created != bundle:
        created.replace(bundle)


def authorization_header(username: str, password: str) -> str:
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Bearer {token}"


def request(
    url: str,
    method: str,
    authorization: str,
    body: bytes | None = None,
    content_type: str | None = None,
    retry_transient: bool = False,
) -> bytes | None:
    headers = {"Authorization": authorization}
    if content_type:
        headers["Content-Type"] = content_type
    try:
        with urlopen(Request(url, data=body, headers=headers, method=method), timeout=60) as response:
            return response.read()
    except HTTPError as error:
        if retry_transient and (error.code == 408 or error.code == 429 or error.code >= 500):
            return None
        raise PreflightError(f"Central request failed with HTTP {error.code}.") from error
    except URLError as error:
        if retry_transient:
            return None
        raise PreflightError("Central request outcome is uncertain; do not retry automatically.") from error


def upload_bundle(base_url: str, bundle: Path, authorization: str, deployment_name: str) -> str:
    boundary = f"----spring-config-contract-{uuid.uuid4().hex}"
    payload = bundle.read_bytes()
    body = b"\r\n".join(
        (
            f"--{boundary}".encode(),
            b'Content-Disposition: form-data; name="bundle"; filename="central-bundle.zip"',
            b"Content-Type: application/octet-stream",
            b"",
            payload,
            f"--{boundary}--".encode(),
            b"",
        ),
    )
    query = urlencode({"publishingType": "USER_MANAGED", "name": deployment_name})
    response = request(
        f"{base_url.rstrip('/')}/api/v1/publisher/upload?{query}",
        "POST",
        authorization,
        body,
        f"multipart/form-data; boundary={boundary}",
    )
    assert response is not None
    deployment_id = response.decode("utf-8").strip()
    if not deployment_id:
        raise PreflightError("Central upload returned no deployment ID; do not retry automatically.")
    return deployment_id


def wait_for_validation(base_url: str, deployment_id: str, authorization: str, poll_seconds: float, max_polls: int) -> None:
    status_url = f"{base_url.rstrip('/')}/api/v1/publisher/status?{urlencode({'id': deployment_id})}"
    for attempt in range(max_polls):
        response = request(status_url, "POST", authorization, retry_transient=True)
        if response is None:
            if attempt + 1 < max_polls:
                time.sleep(poll_seconds)
                continue
            raise PreflightError(f"Central status could not be read for deployment {deployment_id}.")
        try:
            status = json.loads(response.decode("utf-8"))
        except json.JSONDecodeError as error:
            raise PreflightError("Central status response was not valid JSON.") from error
        state = status.get("deploymentState")
        if state == "VALIDATED":
            return
        if state == "FAILED":
            raise PreflightError(f"Central validation failed for deployment {deployment_id}; inspect it in the Portal.")
        if state not in {"PENDING", "VALIDATING"}:
            raise PreflightError(f"Central returned unexpected deployment state {state!r}.")
        if attempt + 1 < max_polls:
            time.sleep(poll_seconds)
    raise PreflightError(f"Central validation did not finish for deployment {deployment_id}.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--base-url", default="https://central.sonatype.com")
    parser.add_argument("--deployment-name", default="spring-config-contract-0.1.0-preflight")
    parser.add_argument("--poll-seconds", type=float, default=10)
    parser.add_argument("--max-polls", type=int, default=30)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.version != "0.1.0":
        raise PreflightError("Remote preflight only supports release version 0.1.0.")
    username = os.environ.get("MAVEN_CENTRAL_USERNAME")
    password = os.environ.get("MAVEN_CENTRAL_PASSWORD")
    if not username or not password:
        raise PreflightError("Missing required Maven Central credential inputs.")
    create_bundle(args.repository, args.bundle, args.version)
    deployment_id = upload_bundle(
        args.base_url,
        args.bundle,
        authorization_header(username, password),
        args.deployment_name,
    )
    wait_for_validation(args.base_url, deployment_id, authorization_header(username, password), args.poll_seconds, args.max_polls)
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with Path(github_output).open("a", encoding="utf-8") as output:
            output.write(f"deployment_id={deployment_id}\n")
    print(f"Central USER_MANAGED deployment validated: {deployment_id}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PreflightError as error:
        print(f"Remote preflight failed: {error}", file=sys.stderr)
        raise SystemExit(1)
