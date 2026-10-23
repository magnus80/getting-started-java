import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TelegramNotifier {

    private static final Pattern OK_TRUE = Pattern.compile("\"ok\"\\s*:\\s*true\\b");
    private static final Pattern DESCRIPTION = Pattern.compile("\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    public record Stats(int passed, int failed, int broken, int skipped) {
        public int total() {
            return passed + failed + broken + skipped;
        }
    }

    public record Summary(String status, Stats stats, String details) {
    }

    public record Run(String jobStatus, String compileOutcome, String testsOutcome, Path resultsDir,
                      String project, String ci, String branch, String commit, String runUrl) {
    }

    public static final class ResultsException extends Exception {
        ResultsException(String message) {
            super(message);
        }
    }

    public static final class NotifyException extends Exception {
        NotifyException(String message) {
            super(message);
        }
    }

    private TelegramNotifier() {
    }

    public static void notify(Run run, String apiUrl, String token, String chatId, Duration timeout,
                              Consumer<String> log) throws NotifyException {
        String message = buildMessage(resolve(run), run);
        log.accept(message);
        if (token.isEmpty() || chatId.isEmpty()) {
            log.accept("::warning::TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is not set, notification skipped");
            return;
        }
        try {
            send(apiUrl, token, chatId, message, timeout);
        } catch (NotifyException e) {
            throw new NotifyException(redact(e.getMessage(), token, chatId));
        }
        log.accept("Telegram notification sent");
    }

    static Stats readStats(Path dir) throws ResultsException {
        if (!Files.isDirectory(dir)) {
            throw new ResultsException("results directory not found: " + dir);
        }
        List<Path> files;
        try (Stream<Path> paths = Files.walk(dir)) {
            files = paths.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
        } catch (IOException e) {
            throw new ResultsException("cannot read " + dir + ": " + e.getMessage());
        }
        if (files.isEmpty()) {
            throw new ResultsException("no JUnit XML files in " + dir);
        }
        int passed = 0, failed = 0, broken = 0, skipped = 0;
        for (Path file : files) {
            Element root = parse(file);
            if (!root.getTagName().equals("testsuite") && !root.getTagName().equals("testsuites")) {
                throw new ResultsException("unexpected root element <" + root.getTagName() + "> in " + file.getFileName());
            }
            NodeList testcases = root.getElementsByTagName("testcase");
            for (int i = 0; i < testcases.getLength(); i++) {
                Element testcase = (Element) testcases.item(i);
                if (hasChild(testcase, "failure")) {
                    failed++;
                } else if (hasChild(testcase, "error")) {
                    broken++;
                } else if (hasChild(testcase, "skipped")) {
                    skipped++;
                } else {
                    passed++;
                }
            }
        }
        return new Stats(passed, failed, broken, skipped);
    }

    static Summary resolve(Run run) {
        if (run.jobStatus().equals("cancelled")) {
            return new Summary("CANCELLED", null, null);
        }
        if (!run.compileOutcome().equals("success")) {
            return new Summary("BUILD ERROR", null, "tests did not run");
        }
        Stats stats;
        try {
            stats = readStats(run.resultsDir());
        } catch (ResultsException e) {
            return new Summary("NO RESULTS", null, e.getMessage());
        }
        if (stats.total() == 0) {
            return new Summary("NO TESTS", stats, null);
        }
        if (stats.failed() > 0 || stats.broken() > 0) {
            return new Summary("FAILED", stats, null);
        }
        if (!run.testsOutcome().equals("success")) {
            return new Summary("TEST STEP FAILED", stats, "Gradle test step failed while all JUnit test cases passed");
        }
        return new Summary("PASSED", stats, null);
    }

    static String buildMessage(Summary summary, Run run) {
        StringBuilder text = new StringBuilder()
                .append("UI tests: ").append(summary.status())
                .append("\nProject: ").append(run.project())
                .append("\nCI: ").append(run.ci())
                .append("\nBranch: ").append(run.branch())
                .append("\nCommit: ").append(run.commit(), 0, Math.min(12, run.commit().length()));
        Stats stats = summary.stats();
        if (stats != null) {
            text.append("\nTests: passed ").append(stats.passed())
                    .append(", failed ").append(stats.failed())
                    .append(", broken ").append(stats.broken())
                    .append(", skipped ").append(stats.skipped())
                    .append(", total ").append(stats.total());
        }
        if (summary.details() != null) {
            text.append("\nDetails: ").append(summary.details());
        }
        return text.append("\nRun: ").append(run.runUrl()).toString();
    }

    static void send(String apiUrl, String token, String chatId, String text, Duration timeout) throws NotifyException {
        String form = "chat_id=" + encode(chatId) + "&text=" + encode(text) + "&disable_web_page_preview=true";
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl.replaceAll("/+$", "") + "/bot" + token + "/sendMessage"))
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new NotifyException("Telegram API request failed: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NotifyException("Telegram API request interrupted");
        }
        if (response.statusCode() != 200) {
            throw new NotifyException("Telegram API returned HTTP " + response.statusCode() + ": " + describe(response.body()));
        }
        if (!response.body().strip().startsWith("{") || !OK_TRUE.matcher(response.body()).find()) {
            throw new NotifyException("Telegram API returned ok=false: " + describe(response.body()));
        }
    }

    static String redact(String text, String... secrets) {
        for (String secret : secrets) {
            if (!secret.isEmpty()) {
                text = text.replace(secret, "***");
            }
        }
        return text;
    }

    private static String describe(String body) {
        Matcher matcher = DESCRIPTION.matcher(body);
        String description = matcher.find() ? matcher.group(1) : body;
        return description.substring(0, Math.min(200, description.length()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Element parse(Path file) throws ResultsException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        } catch (SAXException | IOException e) {
            throw new ResultsException("invalid JUnit XML " + file.getFileName() + ": " + e.getMessage());
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasChild(Element element, String tagName) {
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e && e.getTagName().equals(tagName)) {
                return true;
            }
        }
        return false;
    }
}
