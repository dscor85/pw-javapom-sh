package com.qa.opencart.selfhealing;

import com.microsoft.playwright.Page;

/**
 * Drop-in replacement for calling page.click() / page.fill() / page.textContent() /
 * page.isVisible() directly from a page object. Every call is routed through the
 * HealingEngine, so a locator-related failure is caught, healed (if self-healing is
 * enabled), and retried automatically -- with zero changes to the calling test code.
 *
 * IMPORTANT LIMITATION: self-healing only applies to calls made through THIS class. Any
 * direct page.locator()/page.click()/page.fill()/... call in a page object bypasses healing
 * entirely, because HealingEngine never sees that call. See SELF_HEALING.md.
 *
 * Usage in a page object (mirrors the raw Page methods it replaces):
 *   private HealingPage healingPage = new HealingPage(page, LoginPage.class);
 *   ...
 *   healingPage.fill("login.emailId", emailId, appUserName);
 *   healingPage.click("login.loginBtn", loginBtn);
 *   healingPage.textContent("home.searchPageHeader", searchPageHeader);
 *   healingPage.isVisible("login.forgotPwdLink", forgotPwdLink);
 *
 * The elementKey ("login.emailId") is a short logical name YOU choose for that element --
 * it's what identifies the element in healed-locators.json and in the HTML report, so pick
 * something stable and readable rather than reusing the raw selector string.
 */
public class HealingPage {

	private final Page page;
	private final Class<?> pageObjectClass;
	private final HealingEngine engine;

	public HealingPage(Page page, Class<?> pageObjectClass) {
		this(page, pageObjectClass, SelfHealingEngineFactory.getDefault());
	}

	/** Overload for tests/demos that want to inject their own engine instance. */
	public HealingPage(Page page, Class<?> pageObjectClass, HealingEngine engine) {
		this.page = page;
		this.pageObjectClass = pageObjectClass;
		this.engine = engine;
	}

	public void click(String elementKey, String locator) {
		engine.performClick(page, pageObjectClass, elementKey, locator);
	}

	public void fill(String elementKey, String locator, String text) {
		engine.performFill(page, pageObjectClass, elementKey, locator, text);
	}

	public String textContent(String elementKey, String locator) {
		return engine.performTextContent(page, pageObjectClass, elementKey, locator);
	}

	public boolean isVisible(String elementKey, String locator) {
		return engine.performIsVisible(page, pageObjectClass, elementKey, locator);
	}
}
