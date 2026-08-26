package com.qa.opencart.selfhealing.ai;

import com.qa.opencart.selfhealing.SelfHealingConfig;

/**
 * Builds the single AiLocatorProvider instance used for the run.
 *
 * If self.healing.ai.provider.class is set, that fully-qualified class is instantiated via
 * reflection (it must have a public no-arg constructor and implement AiLocatorProvider).
 * Otherwise the built-in OpenAiCompatibleProvider is used, configured from
 * self.healing.ai.endpoint / self.healing.ai.model / self.healing.ai.api.key.env.
 *
 * Either way, configure(settings) is called once after construction so both built-in and
 * custom providers see the same settings without extra plumbing.
 */
public class AiProviderFactory {

	private AiProviderFactory() {
	}

	public static AiLocatorProvider create(SelfHealingConfig config) {
		AiLocatorProvider provider;
		String customClass = config.getAiProviderClass();

		if (customClass != null && !customClass.isBlank()) {
			provider = instantiateCustomProvider(customClass);
		} else {
			provider = new OpenAiCompatibleProvider();
		}

		provider.configure(config.asSettingsMap());
		return provider;
	}

	private static AiLocatorProvider instantiateCustomProvider(String className) {
		try {
			Class<?> clazz = Class.forName(className);
			Object instance = clazz.getDeclaredConstructor().newInstance();
			if (!(instance instanceof AiLocatorProvider)) {
				throw new IllegalStateException(
						"Class '" + className + "' does not implement AiLocatorProvider.");
			}
			return (AiLocatorProvider) instance;
		} catch (Exception e) {
			throw new RuntimeException(
					"Could not instantiate custom AI provider '" + className
							+ "' via reflection. It must be a public class with a public no-arg constructor "
							+ "that implements AiLocatorProvider.",
					e);
		}
	}
}
