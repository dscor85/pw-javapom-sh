package com.qa.opencart.selfhealing;

/**
 * One row of the SelfHealingReport.html -- every field the HTML report is required to show,
 * for a single healing attempt (successful or not).
 */
public class HealingAttemptEntry {

	public String testName;
	public String pageObjectClass;
	public String sourceFile;
	public String pageUrl;
	public String elementKey;         // "Element/logical locator name"
	public String operation;
	public String originalLocator;
	public String failedLocator;
	public String updatedLocator;     // null when status == FAILED
	public String aiProvider;
	public double confidence;
	public String failureMessage;
	public String aiReasoning;
	public String timestamp;
	public String status;             // "HEALED" or "FAILED"

	public HealingAttemptEntry() {
	}
}
