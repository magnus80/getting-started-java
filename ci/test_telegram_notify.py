import contextlib
import io
import json
import os
import socket
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from unittest import mock
from urllib.parse import parse_qs

import telegram_notify

TOKEN = "123456:SECRET-TOKEN-VALUE"
CHAT_ID = "-1009876543210"

PASSED_CASE = '<testcase name="passed" classname="tests.SearchTests"/>'
FAILED_CASE = '<testcase name="failed" classname="tests.SearchTests"><failure message="boom"/></testcase>'
ERROR_CASE = '<testcase name="broken" classname="tests.SearchTests"><error message="npe"/></testcase>'
SKIPPED_CASE = '<testcase name="skipped" classname="tests.SearchTests"><skipped/></testcase>'


def suite(*cases, name="tests.SearchTests"):
    return f'<?xml version="1.0" encoding="UTF-8"?><testsuite name="{name}" tests="{len(cases)}">{"".join(cases)}</testsuite>'


class FakeTelegram:
    def __init__(self, status=200, body=None, delay=0.0):
        self.status = status
        self.body = body if body is not None else json.dumps({"ok": True, "result": {}})
        self.delay = delay
        self.requests = []

    def __enter__(self):
        fake = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                length = int(self.headers.get("Content-Length", 0))
                fake.requests.append((self.path, parse_qs(self.rfile.read(length).decode("utf-8"))))
                if fake.delay:
                    time.sleep(fake.delay)
                payload = fake.body.encode("utf-8")
                try:
                    self.send_response(fake.status)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(payload)))
                    self.end_headers()
                    self.wfile.write(payload)
                except ConnectionError:
                    pass

            def log_message(self, *args):
                pass

        self.server = HTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}"
        return self

    def __exit__(self, *exc):
        self.server.shutdown()
        self.server.server_close()


class ReadStatsTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def write(self, name, content):
        (self.dir / name).write_text(content, encoding="utf-8")

    def test_counts_passed_failed_broken_skipped(self):
        self.write("TEST-a.xml", suite(PASSED_CASE, PASSED_CASE, FAILED_CASE, ERROR_CASE, SKIPPED_CASE))
        stats = telegram_notify.read_stats(self.dir)
        self.assertEqual((stats.passed, stats.failed, stats.broken, stats.skipped, stats.total), (2, 1, 1, 1, 5))

    def test_sums_multiple_files(self):
        self.write("TEST-a.xml", suite(PASSED_CASE, FAILED_CASE))
        self.write("TEST-b.xml", suite(PASSED_CASE, SKIPPED_CASE, name="tests.OtherTests"))
        stats = telegram_notify.read_stats(self.dir)
        self.assertEqual((stats.passed, stats.failed, stats.skipped, stats.total), (2, 1, 1, 4))

    def test_nested_testsuites_are_not_counted_twice(self):
        self.write(
            "TEST-nested.xml",
            '<testsuites tests="3" failures="1"><testsuite name="outer" tests="3">'
            f'{PASSED_CASE}<testsuite name="inner" tests="2">{PASSED_CASE}{FAILED_CASE}</testsuite>'
            "</testsuite></testsuites>",
        )
        stats = telegram_notify.read_stats(self.dir)
        self.assertEqual((stats.passed, stats.failed, stats.total), (2, 1, 3))

    def test_suite_counters_are_ignored(self):
        self.write("TEST-a.xml", '<testsuite name="s" tests="10" failures="5">' + PASSED_CASE + "</testsuite>")
        stats = telegram_notify.read_stats(self.dir)
        self.assertEqual((stats.passed, stats.failed, stats.total), (1, 0, 1))

    def test_zero_testcases(self):
        self.write("TEST-empty.xml", suite())
        self.assertEqual(telegram_notify.read_stats(self.dir).total, 0)

    def test_missing_directory(self):
        with self.assertRaises(telegram_notify.ResultsError):
            telegram_notify.read_stats(self.dir / "missing")

    def test_empty_directory(self):
        with self.assertRaises(telegram_notify.ResultsError):
            telegram_notify.read_stats(self.dir)

    def test_invalid_xml(self):
        self.write("TEST-broken.xml", "<testsuite><testcase")
        with self.assertRaises(telegram_notify.ResultsError):
            telegram_notify.read_stats(self.dir)

    def test_unexpected_root(self):
        self.write("TEST-other.xml", "<project/>")
        with self.assertRaises(telegram_notify.ResultsError):
            telegram_notify.read_stats(self.dir)


class MainTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.env = mock.patch.dict(os.environ, {}, clear=False)
        self.env.start()
        for name in ("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID", "TELEGRAM_API_URL"):
            os.environ.pop(name, None)

    def tearDown(self):
        self.env.stop()

    def write(self, content, name="TEST-tests.SearchTests.xml"):
        (self.dir / name).write_text(content, encoding="utf-8")

    def run_main(self, job_status="success", compile_outcome="success", tests_outcome="success",
                 extra=(), api=None, token=TOKEN, chat_id=CHAT_ID):
        if token is not None:
            os.environ["TELEGRAM_BOT_TOKEN"] = token
        if chat_id is not None:
            os.environ["TELEGRAM_CHAT_ID"] = chat_id
        if api is not None:
            os.environ["TELEGRAM_API_URL"] = api.url
        argv = [
            "--results-dir", str(self.dir),
            "--job-status", job_status,
            "--compile-outcome", compile_outcome,
            "--tests-outcome", tests_outcome,
            "--project", "magnus80/getting-started-java",
            "--ci", "GitHub Actions",
            "--branch", "hw1",
            "--commit", "6fe8e164069c27d638346d0d22ff7d11180e9a9d",
            "--run-url", "https://github.com/magnus80/getting-started-java/actions/runs/1",
            "--timeout", "0.5",
            *extra,
        ]
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            code = telegram_notify.main(argv)
        output = out.getvalue()
        self.assertNotIn(TOKEN, output)
        self.assertNotIn(CHAT_ID, output)
        return code, output

    def test_passed_run_is_sent(self):
        self.write(suite(PASSED_CASE, PASSED_CASE, PASSED_CASE))
        with FakeTelegram() as api:
            code, output = self.run_main(api=api)
        self.assertEqual(code, 0)
        self.assertIn("Telegram notification sent", output)
        self.assertEqual(len(api.requests), 1)
        path, form = api.requests[0]
        self.assertEqual(path, f"/bot{TOKEN}/sendMessage")
        self.assertEqual(form["chat_id"], [CHAT_ID])
        text = form["text"][0]
        self.assertIn("UI tests: PASSED", text)
        self.assertIn("Tests: passed 3, failed 0, broken 0, skipped 0, total 3", text)
        self.assertIn("Branch: hw1", text)
        self.assertIn("Commit: 6fe8e164069c", text)
        self.assertIn("Run: https://github.com/magnus80/getting-started-java/actions/runs/1", text)

    def test_failed_tests_are_reported_and_notification_succeeds(self):
        self.write(suite(PASSED_CASE, FAILED_CASE, SKIPPED_CASE))
        with FakeTelegram() as api:
            code, _ = self.run_main(job_status="failure", tests_outcome="failure", api=api)
        self.assertEqual(code, 0)
        text = api.requests[0][1]["text"][0]
        self.assertIn("UI tests: FAILED", text)
        self.assertIn("passed 1, failed 1, broken 0, skipped 1, total 3", text)

    def test_errors_are_reported_as_broken(self):
        self.write(suite(PASSED_CASE, ERROR_CASE))
        code, output = self.run_main(job_status="failure", tests_outcome="failure", extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: FAILED", output)
        self.assertIn("broken 1", output)

    def test_build_error_ignores_stale_results(self):
        self.write(suite(PASSED_CASE))
        code, output = self.run_main(job_status="failure", compile_outcome="failure", tests_outcome="",
                                     extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: BUILD ERROR", output)
        self.assertIn("tests did not run", output)
        self.assertNotIn("Tests:", output)

    def test_failure_before_compile_is_build_error(self):
        code, output = self.run_main(job_status="failure", compile_outcome="", tests_outcome="", extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: BUILD ERROR", output)

    def test_cancelled_run(self):
        code, output = self.run_main(job_status="cancelled", compile_outcome="", tests_outcome="",
                                     extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: CANCELLED", output)

    def test_missing_results_directory(self):
        os.rmdir(self.dir)
        code, output = self.run_main(job_status="failure", tests_outcome="failure", extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: NO RESULTS", output)

    def test_invalid_xml_is_no_results(self):
        self.write("<testsuite><testcase")
        code, output = self.run_main(job_status="failure", tests_outcome="failure", extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: NO RESULTS", output)
        self.assertIn("invalid JUnit XML", output)

    def test_zero_tests(self):
        self.write(suite())
        code, output = self.run_main(extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: NO TESTS", output)

    def test_gradle_failure_with_green_xml(self):
        self.write(suite(PASSED_CASE))
        code, output = self.run_main(job_status="failure", tests_outcome="failure", extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("UI tests: TEST STEP FAILED", output)

    def test_missing_secrets_skip_without_request(self):
        self.write(suite(PASSED_CASE))
        with FakeTelegram() as api:
            code, output = self.run_main(api=api, token=None, chat_id=None)
        self.assertEqual(code, 0)
        self.assertIn("::warning::", output)
        self.assertEqual(api.requests, [])

    def test_missing_chat_id_skips_without_request(self):
        self.write(suite(PASSED_CASE))
        with FakeTelegram() as api:
            code, output = self.run_main(api=api, chat_id=None)
        self.assertEqual(code, 0)
        self.assertIn("::warning::", output)
        self.assertEqual(api.requests, [])

    def test_http_401_fails(self):
        self.write(suite(PASSED_CASE))
        body = json.dumps({"ok": False, "error_code": 401, "description": "Unauthorized"})
        with FakeTelegram(status=401, body=body) as api:
            code, output = self.run_main(api=api)
        self.assertEqual(code, 1)
        self.assertIn("::error::Telegram API returned HTTP 401: Unauthorized", output)

    def test_http_502_fails(self):
        self.write(suite(PASSED_CASE))
        with FakeTelegram(status=502, body="Bad Gateway") as api:
            code, output = self.run_main(api=api)
        self.assertEqual(code, 1)
        self.assertIn("HTTP 502", output)

    def test_ok_false_fails(self):
        self.write(suite(PASSED_CASE))
        body = json.dumps({"ok": False, "description": "Bad Request: chat not found"})
        with FakeTelegram(body=body) as api:
            code, output = self.run_main(api=api)
        self.assertEqual(code, 1)
        self.assertIn("ok=false: Bad Request: chat not found", output)

    def test_non_json_response_fails(self):
        self.write(suite(PASSED_CASE))
        with FakeTelegram(body="<html>proxy</html>") as api:
            code, output = self.run_main(api=api)
        self.assertEqual(code, 1)
        self.assertIn("non-JSON", output)

    def test_json_array_response_fails(self):
        self.write(suite(PASSED_CASE))
        with FakeTelegram(body="[true]") as api:
            code, output = self.run_main(api=api)
        self.assertEqual(code, 1)
        self.assertIn("ok=false", output)

    def test_timeout_fails(self):
        self.write(suite(PASSED_CASE))
        with FakeTelegram(delay=2.0) as api:
            started = time.monotonic()
            code, output = self.run_main(api=api)
            elapsed = time.monotonic() - started
        self.assertEqual(code, 1)
        self.assertIn("request failed", output)
        self.assertLess(elapsed, 1.9)

    def test_connection_refused_fails(self):
        self.write(suite(PASSED_CASE))
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            port = s.getsockname()[1]
        os.environ["TELEGRAM_API_URL"] = f"http://127.0.0.1:{port}"
        code, output = self.run_main()
        self.assertEqual(code, 1)
        self.assertIn("request failed", output)

    def test_secrets_in_error_description_are_redacted(self):
        self.write(suite(PASSED_CASE))
        body = json.dumps({"ok": False, "description": f"bad token {TOKEN} for chat {CHAT_ID}"})
        with FakeTelegram(status=400, body=body) as api:
            code, output = self.run_main(api=api)
        self.assertEqual(code, 1)
        self.assertIn("bad token *** for chat ***", output)

    def test_dry_run_does_not_send(self):
        self.write(suite(PASSED_CASE))
        with FakeTelegram() as api:
            code, output = self.run_main(api=api, extra=["--dry-run"])
        self.assertEqual(code, 0)
        self.assertIn("Dry run", output)
        self.assertEqual(api.requests, [])


if __name__ == "__main__":
    unittest.main()
