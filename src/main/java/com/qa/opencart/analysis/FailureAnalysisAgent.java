package com.qa.opencart.analysis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qa.opencart.selfhealing.SelfHealingConfig;

/**
 * Analysis-only agent: reads the Extent HTML report, sends the failed tests to Groq and writes a
 * plain-language root-cause report. It never touches test or application code.
 *
 * Reuses the Groq endpoint, model and API-key env var already defined in self-healing.properties,
 * so there is nothing new to configure. Optional overrides (-D): failure.analysis.model, failure.analysis.traces (default ./traces).
 */
public class FailureAnalysisAgent {

	private static final String PROMPT_RESOURCE = "/prompts/failure-analysis-system-prompt.md";
	private static final String DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
	private static final String DEFAULT_KEY_ENV = "GROQ_API_KEY";
	private static final int MAX_EVIDENCE_CHARS = 20_000; // keeps requests within Groq per-minute token limits

	// Used only to group duplicate failures: error type + message head, and the first locator mentioned.
	private static final Pattern ERROR_HEAD = Pattern
			.compile("([\\w.]*(?:Error|Exception))[:\\s]+([^\\n]{0,160})");
	private static final Pattern LOCATOR = Pattern.compile("locator\\(\"([^\"]+)\"\\)");

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
	private final ObjectMapper mapper = new ObjectMapper();
	private final TraceSummarizer traces = new TraceSummarizer();
	private final Path tracesDir = Paths.get(System.getProperty("failure.analysis.traces", "./traces"));

	/** Returns the written analysis file, or null when the report has no failed tests. */
	public Path analyze(Path extentReport, Path extraLog, Path outputDir) throws Exception {
		String evidence = extractFailedTests(extentReport);
		if (evidence.isBlank()) {
			System.out.println("[FailureAnalysis] No failed tests found in report.");
			return null;
		}
		if (extraLog != null && Files.exists(extraLog)) {
			evidence += "\n\n=== CONSOLE / TEST LOG ===\n" + Files.readString(extraLog);
		}
		if (evidence.length() > MAX_EVIDENCE_CHARS) {
			evidence = evidence.substring(0, MAX_EVIDENCE_CHARS) + "\n[truncated]";
		}

		String analysis = callGroq(loadSystemPrompt(),
				"Analyze the failure groups below (duplicate failures are already grouped). Use the SUMMARY COUNTS line for totals. Evidence supplied is limited to what is shown "
						+ "(report text and a trace summary where available); "
						+ "do not assume screenshots or other evidence exist unless they appear here.\n\n" + evidence);

		Files.createDirectories(outputDir);
		Path out = outputDir.resolve("failure-analysis.md");
		Files.writeString(out, analysis, StandardCharsets.UTF_8);
		System.out.println("[FailureAnalysis] Written: " + out.toAbsolutePath());
		return out;
	}

	/**
	 * Pulls failed tests (Extent Spark: li.test-item[status=fail]) as text, without base64 screenshots, and
	 * groups tests that failed with the same error + locator so each root cause is sent (and analysed) once.
	 */
	String extractFailedTests(Path report) throws Exception {
		Document doc = Jsoup.parse(Files.readString(report, StandardCharsets.UTF_8));
		Elements failed = doc.select("li.test-item[status=fail]");
		if (failed.isEmpty()) {
			return "";
		}

		Map<String, List<String>> namesBySignature = new LinkedHashMap<>();
		Map<String, String> evidenceBySignature = new LinkedHashMap<>();
		for (Element e : failed) {
			e.select("img, script, style").remove();
			Element nameEl = e.selectFirst(".test-detail .name");
			String testName = nameEl != null ? nameEl.text().trim() : "(unnamed)";
			String detail = e.select(".detail-body").text();
			String signature = signature(detail, testName);
			namesBySignature.computeIfAbsent(signature, k -> new ArrayList<>()).add(testName);
			// Evidence is taken from the first test of each group (representative).
			evidenceBySignature.computeIfAbsent(signature,
					k -> detail + "\n" + traces.summarize(tracesDir, testName));
		}

		StringBuilder sb = new StringBuilder();
		sb.append("SUMMARY COUNTS: ").append(failed.size()).append(" failed test(s) in ")
				.append(namesBySignature.size()).append(" distinct failure group(s).\n")
				.append("Tests are grouped when they share the same error type, message and locator. ")
				.append("Evidence below is from the first test of each group.\n\n");
		int i = 1;
		for (Map.Entry<String, List<String>> g : namesBySignature.entrySet()) {
			List<String> names = g.getValue();
			sb.append("=== FAILURE GROUP ").append(i++).append(" (").append(names.size()).append(" test(s)): ")
					.append(String.join(", ", names)).append(" ===\n")
					.append(evidenceBySignature.get(g.getKey())).append("\n\n");
		}
		return sb.toString();
	}

	/** Same error type + message head + locator => same signature. No recognisable error => never grouped. */
	private String signature(String text, String testName) {
		Matcher head = ERROR_HEAD.matcher(text);
		if (!head.find()) {
			return "unique:" + testName;
		}
		String sig = head.group(1) + "|" + head.group(2);
		Matcher loc = LOCATOR.matcher(text);
		if (loc.find()) {
			sig += "|" + loc.group(1);
		}
		return sig.replaceAll("\\d+", "#").replaceAll("\\s+", " ");
	}

	private String loadSystemPrompt() throws Exception {
		try (var in = FailureAnalysisAgent.class.getResourceAsStream(PROMPT_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Missing classpath resource " + PROMPT_RESOURCE);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private String callGroq(String system, String userContent) throws Exception {
		SelfHealingConfig cfg = SelfHealingConfig.getInstance();
		String endpoint = nonBlank(cfg.getAiEndpoint(), DEFAULT_ENDPOINT);
		String model = System.getProperty("failure.analysis.model", cfg.getAiModel());
		String keyEnv = nonBlank(cfg.getAiApiKeyEnvVar(), DEFAULT_KEY_ENV);
		String key = System.getenv(keyEnv);
		if (key == null || key.isBlank()) {
			throw new IllegalStateException("Environment variable " + keyEnv + " is not set");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalStateException("No model configured (self.healing.ai.model or -Dfailure.analysis.model)");
		}

		ObjectNode body = mapper.createObjectNode();
		body.put("model", model);
		body.put("temperature", 0.2);
		body.put("max_tokens", 4000);
		ArrayNode messages = body.putArray("messages");
		messages.addObject().put("role", "system").put("content", system);
		messages.addObject().put("role", "user").put("content", userContent);

		HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(120))
				.header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
				.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();

		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) {
			throw new RuntimeException("Groq API error " + res.statusCode() + ": " + res.body());
		}
		JsonNode root = mapper.readTree(res.body());
		return root.path("choices").path(0).path("message").path("content").asText();
	}

	private static String nonBlank(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	/** Run standalone: args = [extentReport.html] [consoleLog.txt] [outputDir] */
	public static void main(String[] args) throws Exception {
		Path report = Paths.get(args.length > 0 ? args[0] : "./extent-reports/TestExecutionReport.html");
		Path log = args.length > 1 ? Paths.get(args[1]) : null;
		Path outDir = Paths.get(args.length > 2 ? args[2] : "./extent-reports/analysis");
		new FailureAnalysisAgent().analyze(report, log, outDir);
	}
}
