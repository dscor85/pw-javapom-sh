package com.qa.opencart.selfhealing.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qa.opencart.selfhealing.HealingContext;

/**
 * Built-in provider for any OpenAI-compatible chat-completions HTTP endpoint (OpenAI itself,
 * Azure OpenAI behind a compatible gateway, a locally hosted OpenAI-compatible server, etc).
 *
 * Configured entirely through self-healing.properties:
 *   self.healing.ai.endpoint       - full chat-completions URL
 *   self.healing.ai.model          - model name to send
 *   self.healing.ai.api.key.env    - name of the ENV VAR holding the API key (never the key itself)
 *
 * Uses only java.net.http.HttpClient (JDK 21 built-in) and Jackson -- no extra HTTP dependency.
 */
public class OpenAiCompatibleProvider implements AiLocatorProvider {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int DOM_CHAR_LIMIT = 20_000;

	private String endpoint;
	private String model;
	private String apiKeyEnvVar;
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	// Reflection requires a public no-arg constructor.
	public OpenAiCompatibleProvider() {
	}

	@Override
	public void configure(Map<String, String> settings) {
		this.endpoint = settings.getOrDefault("self.healing.ai.endpoint", "");
		this.model = settings.getOrDefault("self.healing.ai.model", "");
		this.apiKeyEnvVar = settings.getOrDefault("self.healing.ai.api.key.env", "");
	}

	@Override
	public String getProviderName() {
		return "openai-compatible" + (model != null && !model.isBlank() ? " (" + model + ")" : "");
	}

	@Override
	public AiLocatorSuggestion suggestLocator(HealingContext context) throws Exception {
		if (endpoint == null || endpoint.isBlank()) {
			throw new IllegalStateException(
					"self.healing.ai.endpoint is not configured -- cannot call the built-in AI provider.");
		}

		String apiKey = resolveApiKey();
		String requestBody = buildRequestBody(context);

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(endpoint))
				.timeout(Duration.ofSeconds(30))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody));

		if (apiKey != null && !apiKey.isBlank()) {
			requestBuilder.header("Authorization", "Bearer " + apiKey);
		}

		HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new RuntimeException("AI provider returned HTTP " + response.statusCode() + ": " + response.body());
		}

		return parseSuggestion(response.body());
	}

	private String resolveApiKey() {
		if (apiKeyEnvVar == null || apiKeyEnvVar.isBlank()) {
			return null; // some local/self-hosted OpenAI-compatible servers don't require a key
		}
		String key = System.getenv(apiKeyEnvVar);
		if (key == null || key.isBlank()) {
			throw new IllegalStateException("Environment variable '" + apiKeyEnvVar
					+ "' (named by self.healing.ai.api.key.env) is not set.");
		}
		return key;
	}

	private String buildRequestBody(HealingContext context) throws Exception {
		String systemPrompt = "You are a Playwright test locator repair assistant. A UI test tried to "
				+ "interact with an element using a CSS or XPath locator that no longer matches anything "
				+ "on the page. You are given the page URL, the current page HTML, the operation being "
				+ "performed, and the locator that failed. Respond with ONLY a single JSON object, no "
				+ "markdown fences, no explanation outside the JSON, in exactly this shape: "
				+ "{\"locatorType\":\"CSS\"|\"XPATH\",\"locatorValue\":\"<selector>\","
				+ "\"confidence\":<0-100 number>,\"reasoning\":\"<one short sentence>\"}. "
				+ "Pick the single element in the HTML that most plausibly serves the same purpose as "
				+ "the original locator described below.";

		String userPrompt = "Page URL: " + context.pageUrl + "\n"
				+ "Page object: " + context.pageObjectClass + " (" + context.sourceFile + ")\n"
				+ "Element (logical name): " + context.elementKey + "\n"
				+ "Operation: " + context.operation + "\n"
				+ "Original locator: " + context.originalLocator + "\n"
				+ "Locator that just failed: " + context.failedLocator + "\n"
				+ "Failure message: " + context.failureMessage + "\n\n"
				+ "Current page HTML (may be truncated):\n" + truncate(context.domSnapshot, DOM_CHAR_LIMIT);

		var root = MAPPER.createObjectNode();
		root.put("model", model);
		root.put("temperature", 0);
		var messages = MAPPER.createArrayNode();
		messages.add(MAPPER.createObjectNode().put("role", "system").put("content", systemPrompt));
		messages.add(MAPPER.createObjectNode().put("role", "user").put("content", userPrompt));
		root.set("messages", messages);

		return MAPPER.writeValueAsString(root);
	}

	private AiLocatorSuggestion parseSuggestion(String responseBody) throws Exception {
		JsonNode root = MAPPER.readTree(responseBody);
		JsonNode choices = root.path("choices");
		if (!choices.isArray() || choices.isEmpty()) {
			throw new RuntimeException("AI response had no 'choices': " + responseBody);
		}
		String content = choices.get(0).path("message").path("content").asText("");
		content = stripMarkdownFences(content).trim();

		JsonNode suggestionNode = MAPPER.readTree(content);
		AiLocatorSuggestion suggestion = new AiLocatorSuggestion();
		suggestion.locatorType = suggestionNode.path("locatorType").asText("CSS").toUpperCase();
		suggestion.locatorValue = suggestionNode.path("locatorValue").asText("");
		suggestion.confidence = suggestionNode.path("confidence").asDouble(0);
		suggestion.reasoning = suggestionNode.path("reasoning").asText("");

		if (suggestion.locatorValue.isBlank()) {
			throw new RuntimeException("AI response did not include a usable locatorValue: " + content);
		}
		return suggestion;
	}

	private String stripMarkdownFences(String s) {
		String trimmed = s.trim();
		if (trimmed.startsWith("```")) {
			int firstNewline = trimmed.indexOf('\n');
			int lastFence = trimmed.lastIndexOf("```");
			if (firstNewline != -1 && lastFence > firstNewline) {
				return trimmed.substring(firstNewline + 1, lastFence);
			}
		}
		return trimmed;
	}

	private String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() > max ? s.substring(0, max) : s;
	}
}
