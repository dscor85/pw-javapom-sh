package com.qa.opencart.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns a Playwright trace zip (saved by ExtentReportListener as ./traces/<testMethod>_<endMillis>.zip)
 * into a short text summary: the last actions before the failure with the error, console errors,
 * page errors and failed network requests. Parsing is deliberately tolerant: if the trace format
 * differs or the file is unreadable, a one-line note is returned instead of failing the analysis.
 */
public class TraceSummarizer {

	private static final int LAST_ACTIONS = 12;
	private static final int MAX_LIST = 8;
	private static final int MAX_CHARS = 3500;

	private final ObjectMapper mapper = new ObjectMapper();

	public String summarize(Path tracesDir, String testName) {
		try {
			Path zip = findLatestTrace(tracesDir, testName);
			if (zip == null) {
				return "TRACE: no trace file found for this test in " + tracesDir + "\n";
			}
			return parse(zip);
		} catch (Exception e) {
			return "TRACE: could not be read (" + e.getMessage() + ")\n";
		}
	}

	private Path findLatestTrace(Path dir, String testName) throws IOException {
		if (!Files.isDirectory(dir)) {
			return null;
		}
		Pattern p = Pattern.compile("^" + Pattern.quote(testName) + "_\\d+\\.zip$");
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(f -> p.matcher(f.getFileName().toString()).matches())
					.max(Comparator.comparingLong(f -> f.toFile().lastModified())).orElse(null);
		}
	}

	private String parse(Path zipPath) throws IOException {
		Map<String, String[]> actions = new LinkedHashMap<>(); // callId -> {description, error}
		List<String> consoleErrors = new ArrayList<>();
		List<String> pageErrors = new ArrayList<>();
		List<String> badRequests = new ArrayList<>();

		try (ZipFile zf = new ZipFile(zipPath.toFile())) {
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				boolean isTrace = name.endsWith("trace.trace");
				boolean isNetwork = name.endsWith("trace.network");
				if (!isTrace && !isNetwork) {
					continue;
				}
				try (BufferedReader br = new BufferedReader(
						new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8))) {
					String line;
					while ((line = br.readLine()) != null) {
						JsonNode n;
						try {
							n = mapper.readTree(line);
						} catch (Exception ignore) {
							continue; // skip malformed line
						}
						if (n == null) {
							continue;
						}
						if (isTrace) {
							readTraceEvent(n, actions, consoleErrors, pageErrors);
						} else {
							readNetworkEvent(n, badRequests);
						}
					}
				}
			}
		}

		StringBuilder sb = new StringBuilder("TRACE SUMMARY (from ").append(zipPath.getFileName()).append(")\n");
		sb.append("Last actions before the end of the test:\n");
		List<String[]> all = new ArrayList<>(actions.values());
		for (String[] a : all.subList(Math.max(0, all.size() - LAST_ACTIONS), all.size())) {
			sb.append(" - ").append(a[0]);
			if (!a[1].isEmpty()) {
				sb.append("  => FAILED: ").append(a[1]);
			}
			sb.append('\n');
		}
		appendList(sb, "Console errors/warnings", consoleErrors);
		appendList(sb, "Page errors", pageErrors);
		appendList(sb, "Failed network requests (HTTP 400+ or no response)", badRequests);

		String out = sb.toString();
		return out.length() > MAX_CHARS ? out.substring(0, MAX_CHARS) + "\n[trace summary truncated]\n" : out;
	}

	private void readTraceEvent(JsonNode n, Map<String, String[]> actions, List<String> consoleErrors,
			List<String> pageErrors) {
		String type = n.path("type").asText("");
		switch (type) {
		case "before": {
			String callId = n.path("callId").asText("");
			String api = n.path("apiName").asText(n.path("method").asText("action"));
			JsonNode params = n.path("params");
			String detail = params.path("selector").asText(params.path("url").asText(""));
			actions.put(callId, new String[] { api + (detail.isEmpty() ? "" : " [" + detail + "]"), "" });
			break;
		}
		case "after": {
			String[] a = actions.get(n.path("callId").asText(""));
			JsonNode err = n.path("error");
			if (a != null && !err.isMissingNode() && !err.isNull()) {
				String msg = err.isTextual() ? err.asText() : err.path("message").asText("error");
				a[1] = firstLine(msg);
			}
			break;
		}
		case "console": {
			String level = n.path("messageType").asText("");
			if (level.equals("error") || level.equals("warning")) {
				consoleErrors.add(level + ": " + firstLine(n.path("text").asText("")));
			}
			break;
		}
		case "event": {
			if ("pageError".equals(n.path("method").asText(""))) {
				pageErrors.add(firstLine(n.path("params").path("error").path("message").asText("page error")));
			}
			break;
		}
		default:
			break;
		}
	}

	private void readNetworkEvent(JsonNode n, List<String> badRequests) {
		if (!"resource-snapshot".equals(n.path("type").asText(""))) {
			return;
		}
		JsonNode snap = n.path("snapshot");
		int status = snap.path("response").path("status").asInt(0);
		if (status >= 400 || status < 0) {
			badRequests.add(snap.path("request").path("method").asText("GET") + " "
					+ snap.path("request").path("url").asText("?") + " -> " + status);
		}
	}

	private void appendList(StringBuilder sb, String title, List<String> items) {
		if (items.isEmpty()) {
			return;
		}
		sb.append(title).append(":\n");
		items.stream().limit(MAX_LIST).forEach(i -> sb.append(" - ").append(i).append('\n'));
	}

	private static String firstLine(String s) {
		String t = s == null ? "" : s.strip();
		int nl = t.indexOf('\n');
		t = nl > 0 ? t.substring(0, nl) : t;
		return t.length() > 300 ? t.substring(0, 300) + "..." : t;
	}
}
