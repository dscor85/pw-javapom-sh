package com.qa.opencart.selfhealing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Persists successfully healed locators to self-healing-reports/healed-locators.json.
 * This file is NEVER reset automatically -- it's the whole point of requirement 9
 * ("reuse stored healed locators in future executions"). It only grows or gets updated,
 * across as many runs as you like, until you decide to clear it yourself.
 *
 * This never touches your page object .java source files.
 */
public class HealedLocatorStore {

	private final Path filePath;
	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private final Map<String, HealedLocatorRecord> store = new ConcurrentHashMap<>();

	public HealedLocatorStore(Path filePath) {
		this.filePath = filePath;
		load();
	}

	public Optional<HealedLocatorRecord> get(String elementKey) {
		return Optional.ofNullable(store.get(elementKey));
	}

	public synchronized void save(HealedLocatorRecord record) {
		store.put(record.elementKey, record);
		persist();
	}

	private void load() {
		try {
			File file = filePath.toFile();
			if (!file.exists() || file.length() == 0) {
				return;
			}
			Map<String, HealedLocatorRecord> loaded = mapper.readValue(file,
					mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, HealedLocatorRecord.class));
			store.putAll(loaded);
		} catch (IOException e) {
			System.out.println("[SelfHealing] Could not load " + filePath + " (starting fresh): " + e.getMessage());
		}
	}

	private void persist() {
		try {
			Files.createDirectories(filePath.getParent());
			mapper.writeValue(filePath.toFile(), store);
		} catch (IOException e) {
			System.out.println("[SelfHealing] Could not write " + filePath + ": " + e.getMessage());
		}
	}
}
