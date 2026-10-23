import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramNotifierTest {

    private static final String TOKEN = "123456:SECRET-TOKEN-VALUE";
    private static final String CHAT_ID = "-1009876543210";
    private static final String PASSED_CASE = "<testcase name=\"passed\" classname=\"tests.SearchTests\"/>";
    private static final String FAILED_CASE = "<testcase name=\"failed\" classname=\"tests.SearchTests\"><failure message=\"boom\"/></testcase>";
    private static final String ERROR_CASE = "<testcase name=\"broken\" classname=\"tests.SearchTests\"><error message=\"npe\"/></testcase>";
    private static final String SKIPPED_CASE = "<testcase name=\"skipped\" classname=\"tests.SearchTests\"><skipped/></testcase>";

    @TempDir
    Path dir;

    private final List<String> log = new ArrayList<>();
    private final List<String> paths = new ArrayList<>();
    private final List<Map<String, String>> forms = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void countsPassedFailedBrokenSkipped() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE, PASSED_CASE, FAILED_CASE, ERROR_CASE, SKIPPED_CASE));
        assertEquals(new TelegramNotifier.Stats(2, 1, 1, 1), TelegramNotifier.readStats(dir));
    }

    @Test
    void sumsMultipleFiles() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE, FAILED_CASE));
        write("TEST-b.xml", suite(PASSED_CASE, SKIPPED_CASE));
        assertEquals(new TelegramNotifier.Stats(2, 1, 0, 1), TelegramNotifier.readStats(dir));
    }

    @Test
    void nestedTestsuitesAreNotCountedTwice() throws Exception {
        write("TEST-nested.xml", "<testsuites tests=\"3\" failures=\"1\"><testsuite name=\"outer\">" + PASSED_CASE
                + "<testsuite name=\"inner\">" + PASSED_CASE + FAILED_CASE + "</testsuite></testsuite></testsuites>");
        assertEquals(new TelegramNotifier.Stats(2, 1, 0, 0), TelegramNotifier.readStats(dir));
    }

    @Test
    void suiteCountersAreIgnored() throws Exception {
        write("TEST-a.xml", "<testsuite name=\"s\" tests=\"10\" failures=\"5\">" + PASSED_CASE + "</testsuite>");
        assertEquals(new TelegramNotifier.Stats(1, 0, 0, 0), TelegramNotifier.readStats(dir));
    }

    @Test
    void rejectsMissingEmptyInvalidAndForeignResults() throws Exception {
        assertThrows(TelegramNotifier.ResultsException.class, () -> TelegramNotifier.readStats(dir.resolve("missing")));
        assertThrows(TelegramNotifier.ResultsException.class, () -> TelegramNotifier.readStats(dir));
        write("TEST-broken.xml", "<testsuite><testcase");
        assertThrows(TelegramNotifier.ResultsException.class, () -> TelegramNotifier.readStats(dir));
        Files.delete(dir.resolve("TEST-broken.xml"));
        write("TEST-other.xml", "<project/>");
        assertThrows(TelegramNotifier.ResultsException.class, () -> TelegramNotifier.readStats(dir));
    }

    @Test
    void passedRunIsSent() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE, PASSED_CASE, PASSED_CASE));
        startServer(200, "{\"ok\":true,\"result\":{}}", 0);
        notifyTelegram(run("success", "success", "success"), TOKEN, CHAT_ID);

        assertEquals(List.of("/bot" + TOKEN + "/sendMessage"), paths);
        assertEquals(CHAT_ID, forms.get(0).get("chat_id"));
        assertEquals("""
                UI tests: PASSED
                Project: magnus80/getting-started-java
                CI: GitHub Actions
                Branch: hw1
                Commit: 6fe8e164069c
                Tests: passed 3, failed 0, broken 0, skipped 0, total 3
                Run: https://github.com/magnus80/getting-started-java/actions/runs/1""", forms.get(0).get("text"));
        assertTrue(log.contains("Telegram notification sent"));
    }

    @Test
    void failedAndBrokenTestsAreReported() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE, FAILED_CASE, ERROR_CASE, SKIPPED_CASE));
        startServer(200, "{\"ok\":true}", 0);
        notifyTelegram(run("failure", "success", "failure"), TOKEN, CHAT_ID);
        String text = forms.get(0).get("text");
        assertTrue(text.startsWith("UI tests: FAILED\n"), text);
        assertTrue(text.contains("Tests: passed 1, failed 1, broken 1, skipped 1, total 4"), text);
    }

    @Test
    void statusesWithoutPassingTests() throws Exception {
        assertEquals("CANCELLED", status(run("cancelled", "skipped", "skipped")));
        assertEquals("NO RESULTS", status(run("failure", "success", "failure")));
        write("TEST-a.xml", suite());
        assertEquals("NO TESTS", status(run("success", "success", "success")));
        write("TEST-a.xml", suite(PASSED_CASE));
        assertEquals("TEST STEP FAILED", status(run("failure", "success", "failure")));
        write("TEST-a.xml", "<testsuite><testcase");
        TelegramNotifier.Summary invalid = TelegramNotifier.resolve(run("failure", "success", "failure"));
        assertEquals("NO RESULTS", invalid.status());
        assertTrue(invalid.details().startsWith("invalid JUnit XML"), invalid.details());
    }

    @Test
    void buildErrorIgnoresStaleResults() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        TelegramNotifier.Run run = run("failure", "failure", "skipped");
        String message = TelegramNotifier.buildMessage(TelegramNotifier.resolve(run), run);
        assertTrue(message.startsWith("UI tests: BUILD ERROR\n"), message);
        assertTrue(message.contains("Details: tests did not run"), message);
        assertFalse(message.contains("Tests:"), message);
    }

    @Test
    void missingSecretsSkipWithoutRequest() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        startServer(200, "{\"ok\":true}", 0);
        notifyTelegram(run("success", "success", "success"), "", "");
        notifyTelegram(run("success", "success", "success"), TOKEN, "");
        assertTrue(paths.isEmpty());
        assertTrue(log.stream().anyMatch(line -> line.startsWith("::warning::")), log.toString());
    }

    @Test
    void httpErrorFails() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        startServer(401, "{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}", 0);
        assertEquals("Telegram API returned HTTP 401: Unauthorized", notifyFailure());
    }

    @Test
    void okFalseFails() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        startServer(200, "{\"ok\":false,\"description\":\"Bad Request: chat not found\"}", 0);
        assertEquals("Telegram API returned ok=false: Bad Request: chat not found", notifyFailure());
    }

    @Test
    void nonJsonAndArrayResponsesFail() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        startServer(200, "<html>proxy</html>", 0);
        assertTrue(notifyFailure().startsWith("Telegram API returned ok=false"));
        server.stop(0);
        startServer(200, "[true]", 0);
        assertTrue(notifyFailure().startsWith("Telegram API returned ok=false"));
    }

    @Test
    void timeoutFails() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        startServer(200, "{\"ok\":true}", 2000);
        long started = System.nanoTime();
        String error = notifyFailure();
        assertTrue(error.startsWith("Telegram API request failed"), error);
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1900);
    }

    @Test
    void connectionRefusedFails() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String apiUrl = "http://127.0.0.1:" + port;
        TelegramNotifier.NotifyException e = assertThrows(TelegramNotifier.NotifyException.class, () ->
                TelegramNotifier.notify(run("success", "success", "success"), apiUrl, TOKEN, CHAT_ID,
                        Duration.ofMillis(500), log::add));
        assertTrue(e.getMessage().startsWith("Telegram API request failed"), e.getMessage());
    }

    @Test
    void secretsInErrorDescriptionAreRedacted() throws Exception {
        write("TEST-a.xml", suite(PASSED_CASE));
        startServer(400, "{\"ok\":false,\"description\":\"bad token " + TOKEN + " for chat " + CHAT_ID + "\"}", 0);
        assertEquals("Telegram API returned HTTP 400: bad token *** for chat ***", notifyFailure());
    }

    private void notifyTelegram(TelegramNotifier.Run run, String token, String chatId) throws Exception {
        TelegramNotifier.notify(run, apiUrl(), token, chatId, Duration.ofMillis(500), log::add);
        assertNoSecretsInLog();
    }

    private String notifyFailure() {
        TelegramNotifier.NotifyException e = assertThrows(TelegramNotifier.NotifyException.class, () ->
                TelegramNotifier.notify(run("success", "success", "success"), apiUrl(), TOKEN, CHAT_ID,
                        Duration.ofMillis(500), log::add));
        assertNoSecretsInLog();
        assertFalse(e.getMessage().contains(TOKEN) || e.getMessage().contains(CHAT_ID), e.getMessage());
        return e.getMessage();
    }

    private void assertNoSecretsInLog() {
        String output = String.join("\n", log);
        assertFalse(output.contains(TOKEN) || output.contains(CHAT_ID), output);
    }

    private String status(TelegramNotifier.Run run) {
        return TelegramNotifier.resolve(run).status();
    }

    private TelegramNotifier.Run run(String jobStatus, String compileOutcome, String testsOutcome) {
        return new TelegramNotifier.Run(jobStatus, compileOutcome, testsOutcome, dir,
                "magnus80/getting-started-java", "GitHub Actions", "hw1",
                "6fe8e164069c27d638346d0d22ff7d11180e9a9d",
                "https://github.com/magnus80/getting-started-java/actions/runs/1");
    }

    private void startServer(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            forms.add(parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            try {
                Thread.sleep(delayMillis);
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, payload.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(payload);
                }
            } catch (InterruptedException | IOException ignored) {
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private String apiUrl() {
        return "http://127.0.0.1:" + (server == null ? 1 : server.getAddress().getPort());
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return form;
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    private static String suite(String... cases) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"tests.SearchTests\">" + String.join("", cases) + "</testsuite>";
    }
}
