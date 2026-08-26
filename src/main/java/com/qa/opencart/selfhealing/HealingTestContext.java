package com.qa.opencart.selfhealing;

/**
 * Holds the currently-running TestNG test method name so HealingEngine can tag report rows
 * with it, without every page object having to pass it through explicitly. Set/cleared by
 * SelfHealingListener's onTestStart/onTestFinish.
 */
public class HealingTestContext {

	private static final ThreadLocal<String> CURRENT_TEST_NAME = new ThreadLocal<>();

	private HealingTestContext() {
	}

	public static void setCurrentTestName(String name) {
		CURRENT_TEST_NAME.set(name);
	}

	public static String getCurrentTestName() {
		String name = CURRENT_TEST_NAME.get();
		return name != null ? name : "unknown";
	}

	public static void clear() {
		CURRENT_TEST_NAME.remove();
	}
}
