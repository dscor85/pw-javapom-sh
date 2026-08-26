package com.qa.opencart.pages;

import com.microsoft.playwright.Page;
import com.qa.opencart.selfhealing.HealingPage;

public class HomePage {

	private Page page;
	private HealingPage healingPage;

	private String search = "input[name='search']";
	private String searchIcon = "div#search button";
	private String searchPageHeader = "div#content h1";
	private String loginLink = "a:text('Login')";
	private String myAccountLink = "a[title='My Account']";

	// page constructor
	public HomePage(Page page) {
		this.page = page;
		this.healingPage = new HealingPage(page, HomePage.class);
	}

	public String getHomePageTitle() {
		String title = page.title();
		System.out.println("page title: " + title);
		return title;
	}

	public String getHomePageURL() {
		String url = page.url();
		System.out.println("page url: " + url);
		return page.url();
	}

	public String doSearch(String productName) {
		healingPage.fill("home.search", search, productName);
		healingPage.click("home.searchIcon", searchIcon);
		String header = healingPage.textContent("home.searchPageHeader", searchPageHeader);
		System.out.println("search header: " + header);
		return header;
	}

	public LoginPage navigateToLoginPage() {
		healingPage.click("home.myAccountLink", myAccountLink);
		healingPage.click("home.loginLink", loginLink);
		return new LoginPage(page);

	}

}
