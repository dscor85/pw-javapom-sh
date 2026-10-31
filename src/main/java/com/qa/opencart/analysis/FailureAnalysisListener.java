package com.qa.opencart.analysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * Optional. Runs the failure analysis once the whole suite has finished (after ExtentReportListener
 * has flushed the report), only when -Dfailure.analysis=true is passed. Never fails the build.
 *
 * Properties (all optional):
 *   failure.analysis.report  default ./extent-reports/TestExecutionReport.html
 *   failure.analysis.out     default ./extent-reports/analysis
 */
public class FailureAnalysisListener implements ISuiteListener {

	@Override
	public void onFinish(ISuite suite) {
		if (!Boolean.getBoolean("failure.analysis")) {
			System.out.println("[FailureAnalysis] Skipped: pass -Dfailure.analysis=true to enable.");
			return;
		}
		System.out.println("[FailureAnalysis] Suite finished, starting analysis...");
		try {
			Path report = Paths
					.get(System.getProperty("failure.analysis.report", "./extent-reports/TestExecutionReport.html"));
			if (!Files.exists(report)) {
				System.out.println("[FailureAnalysis] Report not found: " + report);
				return;
			}
			Path out = Paths.get(System.getProperty("failure.analysis.out", "./extent-reports/analysis"));
			new FailureAnalysisAgent().analyze(report, null, out);
		} catch (Exception e) {
			System.out.println("[FailureAnalysis] Skipped due to error: " + e.getMessage());
		}
	}
}
