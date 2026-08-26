package com.qa.opencart.selfhealing;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads self-healing.properties once and exposes typed accessors. Values in the file may be
 * overridden at the command line with matching -D system properties (e.g.
 * -Dself.healing.enabled=false), which is convenient for CI without editing the file.
 *
 * Never put an API key in this file. self.healing.ai.api.key.env holds the NAME of an
 * environment variable; the actual key is read from that environment variable at runtime.
 */
public class SelfHealingConfig {

	private static final String CONFIG_PATH = "./src/test/resources/config/self-healing.properties";

	private static volatile SelfHealingConfig instance;

	private final boolean enabled;
	private final Path outputDir;
	private final int maxAttempts;
	private final String aiEndpoint;
	private final String aiModel;
	private final String aiApiKeyEnvVar;
	private final String aiProviderClass;

	private SelfHealingConfig(Properties props) {
		this.enabled = Boolean.parseBoolean(resolve(props, "self.healing.enabled", "false"));
		this.outputDir = Paths.get(resolve(props, "self.healing.output.dir", "./self-healing-reports"));
		this.maxAttempts = parseIntSafe(resolve(props, "self.healing.max.attempts", "1"), 1);
		this.aiEndpoint = resolve(props, "self.healing.ai.endpoint", "");
		this.aiModel = resolve(props, "self.healing.ai.model", "");
		this.aiApiKeyEnvVar = resolve(props, "self.healing.ai.api.key.env", "");
		this.aiProviderClass = resolve(props, "self.healing.ai.provider.class", "");
	}

	public static synchronized SelfHealingConfig getInstance() {
		if (instance == null) {
			Properties props = new Properties();
			try (FileInputStream in = new FileInputStream(CONFIG_PATH)) {
				props.load(in);
			} catch (IOException e) {
				System.out.println("[SelfHealing] Could not load " + CONFIG_PATH
						+ " -- self-healing will be disabled. (" + e.getMessage() + ")");
			}
			instance = new SelfHealingConfig(props);
		}
		return instance;
	}

	private static String resolve(Properties props, String key, String fallback) {
		String sysVal = System.getProperty(key);
		if (sysVal != null && !sysVal.isBlank()) {
			return sysVal.trim();
		}
		String fileVal = props.getProperty(key);
		if (fileVal != null && !fileVal.isBlank()) {
			return fileVal.trim();
		}
		return fallback;
	}

	private static int parseIntSafe(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Path getOutputDir() {
		return outputDir;
	}

	public Path getHealedLocatorsPath() {
		return outputDir.resolve("healed-locators.json");
	}

	public Path getAttemptLogPath() {
		return outputDir.resolve("healing-attempts.json");
	}

	public Path getHtmlReportPath() {
		return outputDir.resolve("SelfHealingReport.html");
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public String getAiEndpoint() {
		return aiEndpoint;
	}

	public String getAiModel() {
		return aiModel;
	}

	public String getAiApiKeyEnvVar() {
		return aiApiKeyEnvVar;
	}

	public String getAiProviderClass() {
		return aiProviderClass;
	}

	/** Read-only bag of settings handed to every provider via configure(), built-in or custom. */
	public Map<String, String> asSettingsMap() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("self.healing.ai.endpoint", aiEndpoint);
		map.put("self.healing.ai.model", aiModel);
		map.put("self.healing.ai.api.key.env", aiApiKeyEnvVar);
		map.put("self.healing.output.dir", outputDir.toString());
		map.put("self.healing.max.attempts", String.valueOf(maxAttempts));
		return map;
	}
}
