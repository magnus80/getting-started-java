#!/usr/bin/env python3
"""Send a UI test run summary to Telegram based on JUnit XML results."""

import argparse
import json
import os
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

DEFAULT_API_URL = "https://api.telegram.org"
DEFAULT_TIMEOUT_SECONDS = 10.0


@dataclass
class Stats:
    passed: int = 0
    failed: int = 0
    broken: int = 0
    skipped: int = 0

    @property
    def total(self):
        return self.passed + self.failed + self.broken + self.skipped


class ResultsError(Exception):
    pass


class NotifyError(Exception):
    pass


def read_stats(results_dir):
    directory = Path(results_dir)
    if not directory.is_dir():
        raise ResultsError(f"results directory not found: {directory}")
    files = sorted(directory.rglob("*.xml"))
    if not files:
        raise ResultsError(f"no JUnit XML files in {directory}")
    stats = Stats()
    for file in files:
        try:
            root = ET.parse(file).getroot()
        except ET.ParseError as e:
            raise ResultsError(f"invalid JUnit XML {file.name}: {e}") from e
        if root.tag not in ("testsuite", "testsuites"):
            raise ResultsError(f"unexpected root element <{root.tag}> in {file.name}")
        for testcase in root.iter("testcase"):
            if testcase.find("failure") is not None:
                stats.failed += 1
            elif testcase.find("error") is not None:
                stats.broken += 1
            elif testcase.find("skipped") is not None:
                stats.skipped += 1
            else:
                stats.passed += 1
    return stats


def resolve_status(job_status, compile_outcome, tests_outcome, results_dir):
    if job_status == "cancelled":
        return "CANCELLED", None, None
    if compile_outcome != "success":
        return "BUILD ERROR", None, "tests did not run"
    try:
        stats = read_stats(results_dir)
    except ResultsError as e:
        return "NO RESULTS", None, str(e)
    if stats.total == 0:
        return "NO TESTS", stats, None
    if stats.failed or stats.broken:
        return "FAILED", stats, None
    if tests_outcome != "success":
        return "TEST STEP FAILED", stats, "Gradle test step failed while all JUnit test cases passed"
    return "PASSED", stats, None


def build_message(status, stats, details, args):
    lines = [
        f"UI tests: {status}",
        f"Project: {args.project}",
        f"CI: {args.ci}",
        f"Branch: {args.branch}",
        f"Commit: {args.commit[:12]}",
    ]
    if stats is not None:
        lines.append(
            f"Tests: passed {stats.passed}, failed {stats.failed}, broken {stats.broken}, "
            f"skipped {stats.skipped}, total {stats.total}"
        )
    if details:
        lines.append(f"Details: {details}")
    lines.append(f"Run: {args.run_url}")
    return "\n".join(lines)


def redact(text, secrets):
    for secret in secrets:
        if secret:
            text = text.replace(secret, "***")
    return text


def send_message(api_url, token, chat_id, text, timeout):
    url = f"{api_url.rstrip('/')}/bot{token}/sendMessage"
    data = urllib.parse.urlencode(
        {"chat_id": chat_id, "text": text, "disable_web_page_preview": "true"}
    ).encode("utf-8")
    request = urllib.request.Request(url, data=data, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            body = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise NotifyError(f"Telegram API returned HTTP {e.code}: {describe(body)}") from None
    except (urllib.error.URLError, socket.timeout, TimeoutError) as e:
        reason = getattr(e, "reason", e)
        raise NotifyError(f"Telegram API request failed: {reason}") from None
    if status != 200:
        raise NotifyError(f"Telegram API returned HTTP {status}: {describe(body)}")
    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        raise NotifyError("Telegram API returned a non-JSON response") from None
    if not isinstance(payload, dict) or payload.get("ok") is not True:
        raise NotifyError(f"Telegram API returned ok=false: {describe(body)}")


def describe(body):
    try:
        payload = json.loads(body)
    except json.JSONDecodeError:
        return body[:200]
    if isinstance(payload, dict) and "description" in payload:
        return str(payload["description"])[:200]
    return body[:200]


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-dir", required=True, help="directory with JUnit XML files")
    parser.add_argument("--job-status", required=True, help="success, failure, cancelled or skipped")
    parser.add_argument("--compile-outcome", default="", help="outcome of the compile step")
    parser.add_argument("--tests-outcome", default="", help="outcome of the test step")
    parser.add_argument("--project", required=True)
    parser.add_argument("--ci", required=True)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--run-url", required=True)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--dry-run", action="store_true", help="print the message without sending it")
    return parser.parse_args(argv)


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    args = parse_args(argv)
    status, stats, details = resolve_status(
        args.job_status, args.compile_outcome or "skipped", args.tests_outcome or "skipped", args.results_dir
    )
    message = build_message(status, stats, details, args)
    print(message)

    if args.dry_run:
        print("Dry run: message was not sent")
        return 0

    token = os.environ.get("TELEGRAM_BOT_TOKEN", "")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "")
    if not token or not chat_id:
        print("::warning::TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is not set, notification skipped")
        return 0

    api_url = os.environ.get("TELEGRAM_API_URL", DEFAULT_API_URL)
    try:
        send_message(api_url, token, chat_id, message, args.timeout)
    except NotifyError as e:
        print(f"::error::{redact(str(e), [token, chat_id])}")
        return 1
    print("Telegram notification sent")
    return 0


if __name__ == "__main__":
    sys.exit(main())
