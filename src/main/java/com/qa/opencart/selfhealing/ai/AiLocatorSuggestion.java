package com.qa.opencart.selfhealing.ai;

/**
 * What an AiLocatorProvider hands back: a replacement locator plus enough metadata to
 * populate the report (confidence, reasoning) and to build a live Playwright locator
 * (locatorType tells the caller whether to treat locatorValue as CSS or XPath).
 */
public class AiLocatorSuggestion {

	public String locatorType;   // "CSS" or "XPATH"
	public String locatorValue;
	public double confidence;    // 0-100
	public String reasoning;

	public AiLocatorSuggestion() {
	}

	public AiLocatorSuggestion(String locatorType, String locatorValue, double confidence, String reasoning) {
		this.locatorType = locatorType;
		this.locatorValue = locatorValue;
		this.confidence = confidence;
		this.reasoning = reasoning;
	}
}
