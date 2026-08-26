package com.qa.opencart.selfhealing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Records every healing attempt (successful or not) made during THIS run, so
 * SelfHealingListener can turn them into SelfHealingReport.html once the suite finishes.
 * Reset at the start of every run -- unlike HealedLocatorStore, this describes "what
 * happened this time," not a durable memory across runs.
 */
public class HealingAttemptLog {

	private final Path filePath;
	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private final List<HealingAttemptEntry> entries = new ArrayList<>();

	public HealingAttemptLog(Path filePath) {
		this.filePath = filePath;
	}

	public synchronized void reset() {
		entries.clear();
		persist();
	}

	public synchronized void add(HealingAttemptEntry entry) {
		entries.add(entry);
		persist();
	}

	private void persist() {
		try {
			Files.createDirectories(filePath.getParent());
			mapper.writeValue(filePath.toFile(), entries);
		} catch (IOException e) {
			System.out.println("[SelfHealing] Could not write " + filePath + ": " + e.getMessage());
		}
	}

	public static List<HealingAttemptEntry> readFrom(Path filePath) {
		try {
			File file = filePath.toFile();
			if (!file.exists() || file.length() == 0) {
				return new ArrayList<>();
			}
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(file,
					mapper.getTypeFactory().constructCollectionType(List.class, HealingAttemptEntry.class));
		} catch (IOException e) {
			System.out.println("[SelfHealing] Could not read " + filePath + ": " + e.getMessage());
			return new ArrayList<>();
		}
	}
}
