package com.qa.opencart.selfhealing;

/**
 * Everything captured at the moment a locator-related failure happens, handed to the AI
 * provider and also used to populate the report row for this attempt.
 */
public class HealingContext {

	public final String elementKey;
	public final String operation;
	public final String originalLocator;
	public final String failedLocator;
	public final String pageUrl;
	public final String domSnapshot;
	public final String pageObjectClass;
	public final String sourceFile;
	public final String failureMessage;
	public final String testName;

	public HealingContext(String elementKey, String operation, String originalLocator, String failedLocator,
			String pageUrl, String domSnapshot, String pageObjectClass, String sourceFile,
			String failureMessage, String testName) {
		this.elementKey = elementKey;
		this.operation = operation;
		this.originalLocator = originalLocator;
		this.failedLocator = failedLocator;
		this.pageUrl = pageUrl;
		this.domSnapshot = domSnapshot;
		this.pageObjectClass = pageObjectClass;
		this.sourceFile = sourceFile;
		this.failureMessage = failureMessage;
		this.testName = testName;
	}
}
