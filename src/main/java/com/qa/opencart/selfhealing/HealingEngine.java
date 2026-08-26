package com.qa.opencart.selfhealing;

import java.time.Instant;
import java.util.Optional;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.qa.opencart.selfhealing.ai.AiLocatorProvider;
import com.qa.opencart.selfhealing.ai.AiLocatorSuggestion;

/**
 * Central piece of the self-healing framework. HealingPage delegates every click/fill/
 * textContent/isVisible call here.
 *
 * Flow for every call:
 *   1. If a previously-healed locator exists for this element key, try that FIRST
 *      (requirement: reuse stored healed locators in future executions).
 *   2. Otherwise try the original locator supplied by the page object.
 *   3. If that locator can't be found at all (a "locator-related failure" -- detected via a
 *      short attached-state wait, so it applies uniformly to click/fill/textContent/isVisible):
 *        - if self-healing is disabled, propagate the original failure untouched.
 *        - otherwise, capture full context (URL, DOM, original/failed locator, operation,
 *          page object, exception) and call the configured AiLocatorProvider for a
 *          replacement, retrying up to self.healing.max.attempts times.
 *        - on success: persist the healed locator to healed-locators.json and log a HEALED
 *          entry to the attempt log; the calling test method proceeds normally.
 *        - on failure: log a FAILED entry to the attempt log and propagate the original
 *          failure (except for isVisible, which never throws -- see performIsVisible).
 *
 * Nothing here ever writes into a page object's .java source. That file stays exactly as
 * the developer wrote it.
 */
public class HealingEngine {

	private static final int LOCATE_TIMEOUT_MS = 3000;

	private final SelfHealingConfig config;
	private final HealedLocatorStore healedStore;
	private final HealingAttemptLog attemptLog;
	private final AiLocatorProvider aiProvider;

	public HealingEngine(SelfHealingConfig config, HealedLocatorStore healedStore,
			HealingAttemptLog attemptLog, AiLocatorProvider aiProvider) {
		this.config = config;
		this.healedStore = healedStore;
		this.attemptLog = attemptLog;
		this.aiProvider = aiProvider;
	}

	public void performClick(Page page, Class<?> pageObjectClass, String elementKey, String originalLocator) {
		execute(page, pageObjectClass, elementKey, originalLocator, "click", loc -> {
			loc.click();
			return null;
		}, false);
	}

	public void performFill(Page page, Class<?> pageObjectClass, String elementKey, String originalLocator, String text) {
		execute(page, pageObjectClass, elementKey, originalLocator, "fill", loc -> {
			loc.fill(text);
			return null;
		}, false);
	}

	public String performTextContent(Page page, Class<?> pageObjectClass, String elementKey, String originalLocator) {
		return execute(page, pageObjectClass, elementKey, originalLocator, "textContent", Locator::textContent, false);
	}

	/**
	 * Mirrors Playwright's own isVisible() contract: never throws. If the locator matches
	 * nothing at all, that's treated as a candidate locator failure and healing is attempted;
	 * if healing also can't find anything, this returns false rather than throwing, exactly
	 * like the original page.isVisible() call it replaces.
	 */
	public boolean performIsVisible(Page page, Class<?> pageObjectClass, String elementKey, String originalLocator) {
		Boolean result = execute(page, pageObjectClass, elementKey, originalLocator, "isVisible",
				Locator::isVisible, true);
		return result != null && result;
	}

	private <T> T execute(Page page, Class<?> pageObjectClass, String elementKey, String originalLocator,
			String operation, LocatorAction<T> action, boolean swallowFinalFailure) {

		Optional<HealedLocatorRecord> healed = healedStore.get(elementKey);
		String firstAttemptLocator = healed.map(h -> h.locatorValue).orElse(originalLocator);
		String firstAttemptType = healed.map(h -> h.locatorType).orElse(detectType(originalLocator));

		try {
			Locator loc = buildLocator(page, firstAttemptType, firstAttemptLocator);
			waitAttached(loc);
			return action.run(loc);
		} catch (TimeoutError firstFailure) {
			if (!config.isEnabled()) {
				if (swallowFinalFailure) {
					return null;
				}
				throw firstFailure;
			}
			return healAndRetry(page, pageObjectClass, elementKey, originalLocator, firstAttemptLocator,
					operation, action, firstFailure, swallowFinalFailure);
		}
	}

	private <T> T healAndRetry(Page page, Class<?> pageObjectClass, String elementKey, String originalLocator,
			String failedLocator, String operation, LocatorAction<T> action, TimeoutError originalFailure,
			boolean swallowFinalFailure) {

		String pageUrl = safePageUrl(page);
		String dom = safePageContent(page);
		String sourceFile = pageObjectClass.getSimpleName() + ".java";
		String testName = HealingTestContext.getCurrentTestName();

		System.out.println("[SELF-HEALING] Locator failure for '" + elementKey + "' (" + operation
				+ ") -- invoking AI provider to find a replacement...");

		int attempts = Math.max(1, config.getMaxAttempts());
		Exception lastError = originalFailure;
		String lastTriedLocator = failedLocator;

		for (int attempt = 1; attempt <= attempts; attempt++) {
			HealingContext context = new HealingContext(elementKey, operation, originalLocator, lastTriedLocator,
					pageUrl, dom, pageObjectClass.getName(), sourceFile, describe(lastError), testName);

			try {
				AiLocatorSuggestion suggestion = aiProvider.suggestLocator(context);
				Locator healedLoc = buildLocator(page, suggestion.locatorType, suggestion.locatorValue);
				waitAttached(healedLoc);
				T result = action.run(healedLoc);

				healedStore.save(new HealedLocatorRecord(elementKey, originalLocator, suggestion.locatorType,
						suggestion.locatorValue, suggestion.confidence, aiProvider.getProviderName(),
						Instant.now().toString()));

				attemptLog.add(buildEntry(context, suggestion, "HEALED", aiProvider.getProviderName()));

				System.out.println("[SELF-HEALING] Healed '" + elementKey + "' -> "
						+ suggestion.locatorType + ": " + suggestion.locatorValue
						+ " (confidence=" + suggestion.confidence + ")");

				return result;
			} catch (Exception attemptError) {
				lastError = attemptError;
				attemptLog.add(buildEntry(context, null, "FAILED", aiProvider.getProviderName()));
				System.out.println("[SELF-HEALING] Attempt " + attempt + "/" + attempts
						+ " failed to heal '" + elementKey + "': " + attemptError.getMessage());
			}
		}

		System.out.println("[SELF-HEALING] Could not heal '" + elementKey + "' after " + attempts
				+ " attempt(s). Needs a manual fix -- see the report.");

		if (swallowFinalFailure) {
			return null;
		}
		throw new RuntimeException("Self-healing could not resolve element '" + elementKey
				+ "' (" + operation + ") after " + attempts + " attempt(s)", originalFailure);
	}

	private HealingAttemptEntry buildEntry(HealingContext context, AiLocatorSuggestion suggestion,
			String status, String providerName) {
		HealingAttemptEntry entry = new HealingAttemptEntry();
		entry.testName = context.testName;
		entry.pageObjectClass = context.pageObjectClass;
		entry.sourceFile = context.sourceFile;
		entry.pageUrl = context.pageUrl;
		entry.elementKey = context.elementKey;
		entry.operation = context.operation;
		entry.originalLocator = context.originalLocator;
		entry.failedLocator = context.failedLocator;
		entry.failureMessage = context.failureMessage;
		entry.timestamp = Instant.now().toString();
		entry.status = status;
		entry.aiProvider = providerName;
		if (suggestion != null) {
			entry.updatedLocator = suggestion.locatorType + ": " + suggestion.locatorValue;
			entry.confidence = suggestion.confidence;
			entry.aiReasoning = suggestion.reasoning;
		}
		return entry;
	}

	private Locator buildLocator(Page page, String type, String value) {
		if ("XPATH".equalsIgnoreCase(type)) {
			return page.locator("xpath=" + value);
		}
		return page.locator(value);
	}

	private String detectType(String locatorValue) {
		String trimmed = locatorValue == null ? "" : locatorValue.trim();
		return trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("(") ? "XPATH" : "CSS";
	}

	private void waitAttached(Locator loc) {
		loc.first().waitFor(new Locator.WaitForOptions()
				.setTimeout(LOCATE_TIMEOUT_MS)
				.setState(WaitForSelectorState.ATTACHED));
	}

	private String safePageUrl(Page page) {
		try {
			return page.url();
		} catch (Exception e) {
			return "unknown";
		}
	}

	private String safePageContent(Page page) {
		try {
			return page.content();
		} catch (Exception e) {
			return "";
		}
	}

	private String describe(Exception e) {
		return e == null ? "" : (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
	}

	@FunctionalInterface
	private interface LocatorAction<T> {
		T run(Locator locator);
	}
}
