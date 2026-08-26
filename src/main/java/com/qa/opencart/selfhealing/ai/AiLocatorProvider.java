package com.qa.opencart.selfhealing.ai;

import java.util.Map;

import com.qa.opencart.selfhealing.HealingContext;

/**
 * Abstraction over whatever AI backend actually forms a replacement locator. The framework
 * never hard-codes a vendor -- it only depends on this interface. Implementations are wired
 * up by AiProviderFactory, either the built-in OpenAiCompatibleProvider or a custom class
 * supplied via self.healing.ai.provider.class.
 *
 * Custom providers must have a public no-arg constructor -- they are instantiated through
 * reflection.
 */
public interface AiLocatorProvider {

	/**
	 * Called once per provider instance, right after construction, with the settings from
	 * self-healing.properties (endpoint, model, api key env var name, output dir, max attempts).
	 * Never receives the API key itself -- only the name of the environment variable that
	 * holds it, so implementations should call System.getenv(...) themselves if they need it.
	 */
	default void configure(Map<String, String> settings) {
		// no-op by default; override if your provider needs any of these settings
	}

	/**
	 * Asks the AI backend for a replacement locator given the full failure context.
	 * Should throw on any failure (network error, malformed response, etc.) -- the caller
	 * treats a thrown exception the same as "no usable suggestion."
	 */
	AiLocatorSuggestion suggestLocator(HealingContext context) throws Exception;

	/** Short human-readable name recorded in reports, e.g. "openai-compatible" or a custom name. */
	default String getProviderName() {
		return this.getClass().getSimpleName();
	}
}
