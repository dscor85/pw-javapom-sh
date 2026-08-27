package com.qa.opencart.selfhealing;

import java.nio.file.Files;
import java.util.List;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Wire this into your TestNG suite XML alongside your existing ExtentReportListener (which
 * this does not touch or replace):
 *
 *   <listener class-name="com.qa.opencart.selfhealing.SelfHealingListener" />
 *
 * Responsibilities:
 *   - Tags HealingTestContext with the current test method name, so HealingEngine can put it
 *     in each report row without page objects needing to pass it around (ITestListener).
 *   - After the ENTIRE TestNG suite finishes -- not each <test> block within it -- reads
 *     healing-attempts.json (written during the run by HealingAttemptLog) and renders
 *     self-healing-reports/SelfHealingReport.html (ISuiteListener.onFinish(ISuite), which
 *     fires exactly once per <suite>, unlike ITestListener.onFinish(ITestContext) which
 *     would fire once per <test> block).
 */
public class SelfHealingListener implements ITestListener, ISuiteListener {

	@Override
	public void onTestStart(ITestResult result) {
		HealingTestContext.setCurrentTestName(result.getMethod().getMethodName());
	}

	@Override
	public void onTestSuccess(ITestResult result) {
		HealingTestContext.clear();
	}

	@Override
	public void onTestFailure(ITestResult result) {
		HealingTestContext.clear();
	}

	@Override
	public void onTestSkipped(ITestResult result) {
		HealingTestContext.clear();
	}

	@Override
	public void onFinish(ISuite suite) {
		generateReport();
	}

	private void generateReport() {
		SelfHealingConfig config = SelfHealingConfig.getInstance();
		List<HealingAttemptEntry> entries = HealingAttemptLog.readFrom(config.getAttemptLogPath());

		if (entries.isEmpty()) {
			System.out.println("[SELF-HEALING] No locator failures were analyzed this run. No report generated.");
			return;
		}

		long healedCount = entries.stream().filter(e -> "HEALED".equals(e.status)).count();
		long failedCount = entries.size() - healedCount;

		String html = buildHtml(entries, healedCount, failedCount);

		try {
			Files.createDirectories(config.getHtmlReportPath().getParent());
			Files.writeString(config.getHtmlReportPath(), html);
			System.out.println("[SELF-HEALING] Report generated: " + config.getHtmlReportPath().toAbsolutePath());
		} catch (Exception e) {
			System.out.println("[SELF-HEALING] Could not write HTML report: " + e.getMessage());
		}
	}

	private String buildHtml(List<HealingAttemptEntry> entries, long healedCount, long failedCount) {
		StringBuilder rows = new StringBuilder();
		for (HealingAttemptEntry e : entries) {
			String badgeClass = "HEALED".equals(e.status) ? "badge-healed" : "badge-failed";
			rows.append("<tr>")
					.append(td(e.testName))
					.append(td(e.pageObjectClass))
					.append(td(e.sourceFile))
					.append(td(e.pageUrl))
					.append(td(e.elementKey))
					.append(td(e.operation))
					.append(tdCode(e.originalLocator))
					.append(tdCode(e.failedLocator))
					.append(tdCode(e.updatedLocator))
					.append(td(e.aiProvider))
					.append(td(e.updatedLocator != null ? String.format("%.0f", e.confidence) : "-"))
					.append(td(e.failureMessage))
					.append(td(e.aiReasoning))
					.append(td(e.timestamp))
					.append("<td><span class='badge ").append(badgeClass).append("'>").append(e.status).append("</span></td>")
					.append("</tr>\n");
		}

		return "<!DOCTYPE html>\n"
				+ "<html><head><meta charset='UTF-8'><title>Self-Healing Locator Report</title><style>\n"
				+ "body { font-family: -apple-system, Segoe UI, Arial, sans-serif; margin: 28px; color: #1a1a1a; background:#fafafa; }\n"
				+ "h1 { margin-bottom: 4px; }\n"
				+ "p.subtitle { color: #555; margin-top: 0; }\n"
				+ "table { border-collapse: collapse; width: 100%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.1); font-size: 12px; }\n"
				+ "th, td { border: 1px solid #e2e2e2; padding: 7px 9px; text-align: left; vertical-align: top; max-width: 220px; word-break: break-word; }\n"
				+ "th { background: #f0f0f0; position: sticky; top: 0; }\n"
				+ "code { background: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-size: 11px; }\n"
				+ ".badge { padding: 2px 8px; border-radius: 10px; font-size: 10px; font-weight: 600; color: #fff; white-space: nowrap; }\n"
				+ ".badge-healed { background: #2e9e4f; }\n"
				+ ".badge-failed { background: #c0392b; }\n"
				+ ".summary { margin: 14px 0; font-size: 14px; }\n"
				+ ".table-wrap { overflow-x: auto; }\n"
				+ "</style></head><body>\n"
				+ "<h1>Self-Healing Locator Report</h1>\n"
				+ "<p class='subtitle'>Generated after this TestNG run finished. Healed locators are also saved to "
				+ "<code>healed-locators.json</code> for reuse in future runs -- nothing here has been written into "
				+ "any page object source file.</p>\n"
				+ "<div class='summary'>"
				+ "<strong>" + entries.size() + "</strong> locator failure(s) analyzed &nbsp;|&nbsp; "
				+ "<strong>" + healedCount + "</strong> fixed automatically &nbsp;|&nbsp; "
				+ "<strong>" + failedCount + "</strong> could not be healed"
				+ "</div>\n"
				+ "<div class='table-wrap'><table><thead><tr>"
				+ "<th>Test</th><th>Page Object</th><th>Source File</th><th>Page URL</th>"
				+ "<th>Element</th><th>Operation</th><th>Original Locator</th><th>Failed Locator</th>"
				+ "<th>Updated Locator</th><th>AI Provider</th><th>Confidence</th>"
				+ "<th>Failure Message</th><th>AI Reasoning</th><th>Timestamp</th><th>Status</th>"
				+ "</tr></thead><tbody>\n"
				+ rows
				+ "</tbody></table></div>\n"
				+ "</body></html>";
	}

	private String td(String value) {
		return "<td>" + escape(value) + "</td>";
	}

	private String tdCode(String value) {
		return value == null ? "<td>&mdash;</td>" : "<td><code>" + escape(value) + "</code></td>";
	}

	private String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
