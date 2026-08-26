package com.qa.opencart.selfhealing;

/**
 * One successfully healed locator, persisted across runs so future executions can reuse it
 * directly instead of calling the AI again for the same element.
 */
public class HealedLocatorRecord {

	public String elementKey;
	public String originalLocator;
	public String locatorType;   // "CSS" or "XPATH"
	public String locatorValue;
	public double confidence;
	public String aiProvider;
	public String healedAt;

	public HealedLocatorRecord() {
	}

	public HealedLocatorRecord(String elementKey, String originalLocator, String locatorType, String locatorValue,
			double confidence, String aiProvider, String healedAt) {
		this.elementKey = elementKey;
		this.originalLocator = originalLocator;
		this.locatorType = locatorType;
		this.locatorValue = locatorValue;
		this.confidence = confidence;
		this.aiProvider = aiProvider;
		this.healedAt = healedAt;
	}
}
