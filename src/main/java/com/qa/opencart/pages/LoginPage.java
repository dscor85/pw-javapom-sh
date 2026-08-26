package com.qa.opencart.pages;

import com.microsoft.playwright.Page;
import com.qa.opencart.selfhealing.HealingPage;

public class LoginPage {

	private Page page;
	private HealingPage healingPage;

//	private String emailId = "//input[@id='input-email']";
	private String emailId = "//input[@id='input-email-BROKEN']";
	private String password = "//input[@id='input-password']";
	private String loginBtn = "//input[@value='Login']";
	private String forgotPwdLink = "//div[@class='form-group']//a[normalize-space()='Forgotten Password']";
	private String logoutLink = "//a[@class='list-group-item'][normalize-space()='Logout']";


	// 2. page constructor:
	public LoginPage(Page page) {
		this.page = page;
		this.healingPage = new HealingPage(page, LoginPage.class);
	}


	public String getLoginPageTitle() {
		return page.title();
	}

	public boolean isForgotPwdLink() {
		return healingPage.isVisible("login.forgotPwdLink", forgotPwdLink);
	}

	public boolean doLogin(String appUserName, String appPassword) {
		System.out.println("App creds: " + appUserName + ":" + appPassword);
		healingPage.fill("login.emailId", emailId, appUserName);
		healingPage.fill("login.password", password, appPassword);
		healingPage.click("login.loginBtn", loginBtn);

		if (healingPage.isVisible("login.logoutLink", logoutLink)) {
			System.out.println("user logged in successfully.");
			return true;
		}
		return false;
	}

}
