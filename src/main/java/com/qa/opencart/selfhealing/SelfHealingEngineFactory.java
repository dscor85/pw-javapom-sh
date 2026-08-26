package com.qa.opencart.selfhealing;

import com.qa.opencart.selfhealing.ai.AiLocatorProvider;
import com.qa.opencart.selfhealing.ai.AiProviderFactory;

/**
 * Builds one shared HealingEngine for the whole suite run (so every page object writes to
 * the same healed-locators.json / healing-attempts.json), and resets the per-run attempt log
 * exactly once when that engine is first created.
 */
public class SelfHealingEngineFactory {

	private static volatile HealingEngine defaultEngine;

	public static synchronized HealingEngine getDefault() {
		if (defaultEngine == null) {
			SelfHealingConfig config = SelfHealingConfig.getInstance();
			HealedLocatorStore healedStore = new HealedLocatorStore(config.getHealedLocatorsPath());
			HealingAttemptLog attemptLog = new HealingAttemptLog(config.getAttemptLogPath());
			attemptLog.reset(); // fresh report each run; healed-locators.json is NOT reset

			AiLocatorProvider provider = AiProviderFactory.create(config);

			defaultEngine = new HealingEngine(config, healedStore, attemptLog, provider);

			System.out.println("[SELF-HEALING] Initialized. enabled=" + config.isEnabled()
					+ ", provider=" + provider.getProviderName()
					+ ", outputDir=" + config.getOutputDir());
		}
		return defaultEngine;
	}
}
