package com.qa.opencart.factory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public class PlaywrightFactory {

	Playwright playwright;
	Browser browser;
	BrowserContext browserContext;
	Page page;
	Properties prop;

	private static ThreadLocal<Browser> tlBrowser = new ThreadLocal<>();
	private static ThreadLocal<BrowserContext> tlBrowserContext = new ThreadLocal<>();
	private static ThreadLocal<Page> tlPage = new ThreadLocal<>();
	private static ThreadLocal<Playwright> tlPlaywright = new ThreadLocal<>();

	public static Playwright getPlaywright() {
		return tlPlaywright.get();
	}

	public static Browser getBrowser() {
		return tlBrowser.get();
	}

	public static BrowserContext getBrowserContext() {
		return tlBrowserContext.get();
	}

	public static Page getPage() {
		return tlPage.get();
	}

	public Page initBrowser(Properties prop) {

		String browserName = prop.getProperty("browser").trim();
		System.out.println("browser name is: " + browserName);

		// playwright = Playwright.create();
		tlPlaywright.set(Playwright.create());

		switch (browserName.toLowerCase()) {
		case "chromium":
//			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
			tlBrowser.set(getPlaywright().chromium().launch(new BrowserType.LaunchOptions().setHeadless(Boolean.parseBoolean(prop.getProperty("headless")))));
			break;
		case "firefox":
//			browser = playwright.firefox().launch(new BrowserType.LaunchOptions().setHeadless(false));
			tlBrowser.set(getPlaywright().firefox().launch(new BrowserType.LaunchOptions().setHeadless(Boolean.parseBoolean(prop.getProperty("headless")))));
			break;
		case "safari":
//			browser = playwright.webkit().launch(new BrowserType.LaunchOptions().setHeadless(false));
			tlBrowser.set(getPlaywright().webkit().launch(new BrowserType.LaunchOptions().setHeadless(Boolean.parseBoolean(prop.getProperty("headless")))));
			break;
		case "chrome":
//			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome").setHeadless(false));
			tlBrowser.set(getPlaywright().chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome").setHeadless(Boolean.parseBoolean(prop.getProperty("headless")))));
			break;

		default:
			System.out.println("please pass the right browser name....");
			break;
		}

//		browserContext = browser.newContext();
//		page = browserContext.newPage();
//		page.navigate(prop.getProperty("url").trim());
//		return page;
		tlBrowserContext.set(getBrowser().newContext());
		
		tlPage.set(getBrowserContext().newPage());
		//for CI
		getPage().setDefaultTimeout(60000);
		getPage().setDefaultNavigationTimeout(60000);
		
//		getPage().navigate(prop.getProperty("url").trim());
		
		//for CI
		getPage().navigate(
			    prop.getProperty("url").trim(),
			    new Page.NavigateOptions()
			        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
			        .setTimeout(60000)
			);
		return getPage();

	}

	/**
	 * initial
	 */

//	public Properties init_prop() {
//
//		try {
//			FileInputStream ip = new FileInputStream("./src/test/resources/config/config.properties");
//			prop = new Properties();
//			prop.load(ip);
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//
//		return prop;
//
//	}
	
	//github and local working for 1
//	public Properties init_prop() {
//
//	    try {
//
//	        // Load config.properties
//	        FileInputStream configIp =
//	                new FileInputStream("./src/test/resources/config/config.properties");
//
//	        prop = new Properties();
//	        prop.load(configIp);
//
//	        // Load credentials.properties if present
//	        Properties credProp = new Properties();
//
//	        try {
//	            FileInputStream credIp =
//	                    new FileInputStream("./src/test/resources/config/credentials.properties");
//
//	            credProp.load(credIp);
//
//	            prop.setProperty("username",
//	                    credProp.getProperty("username"));
//
//	            prop.setProperty("password",
//	                    credProp.getProperty("password"));
//
//	            System.out.println("Using credentials from credentials.properties");
//
//	        } catch (FileNotFoundException e) {
//	            System.out.println("credentials.properties not found. Checking environment variables...");
//	        }
//
//	        // GitHub Secrets override everything
//	        String githubUser = System.getenv("OPENCART_USERNAME");
//	        String githubPassword = System.getenv("OPENCART_PASSWORD");
//
//	        if (githubUser != null && !githubUser.isBlank()) {
//	            prop.setProperty("username", githubUser);
//	            System.out.println("Using username from GitHub Secret");
//	        }
//
//	        if (githubPassword != null && !githubPassword.isBlank()) {
//	            prop.setProperty("password", githubPassword);
//	            System.out.println("Using password from GitHub Secret");
//	        }
//
//	    } catch (Exception e) {
//	        e.printStackTrace();
//	    }
//
//	    return prop;
//	}
	
	//final
//	public Properties init_prop() {
//
//		try {
//
//			// Load config.properties
//			FileInputStream configIp =
//					new FileInputStream("./src/test/resources/config/config.properties");
//
//			prop = new Properties();
//			prop.load(configIp);
//
//			// Default role = user
////			String role = System.getProperty("role", "user");
//			String role = System.getProperty("role");
//
//			if(role == null || role.isBlank()) {
//			    role = prop.getProperty("role", "user");
//			}
//
//			System.out.println("Executing tests with role: " + role);
//
//			// Load local credentials.properties
//			try {
//
//				FileInputStream credIp =
//						new FileInputStream("./src/test/resources/config/credentials.properties");
//
//				Properties credProp = new Properties();
//				credProp.load(credIp);
//
//				prop.setProperty("username",
//						credProp.getProperty(role + ".username"));
//
//				prop.setProperty("password",
//						credProp.getProperty(role + ".password"));
//
//				System.out.println("Using local credentials.properties");
//				System.out.println("Username selected: " + prop.getProperty("username"));
//
//			} catch (FileNotFoundException e) {
//
//				System.out.println("credentials.properties not found");
//			}
//
//			// GitHub Secrets override local credentials
//			String githubUser = System.getenv(role.toUpperCase() + "_USERNAME");
//			String githubPassword = System.getenv(role.toUpperCase() + "_PASSWORD");
//
//			if (githubUser != null && !githubUser.isBlank()) {
//
//				prop.setProperty("username", githubUser);
//
//				System.out.println("Using GitHub username for role: " + role);
//			}
//
//			if (githubPassword != null && !githubPassword.isBlank()) {
//
//				prop.setProperty("password", githubPassword);
//
//				System.out.println("Using GitHub password for role: " + role);
//			}
//
//			// Validation
//			if (prop.getProperty("username") == null ||
//				prop.getProperty("password") == null) {
//
//				throw new RuntimeException(
//					"Credentials not found for role: " + role);
//			}
//
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//
//		return prop;
//	}
	 //print which user
	public Properties init_prop() {

		try {

			// Load config.properties
			FileInputStream configIp =
					new FileInputStream("./src/test/resources/config/config.properties");

			prop = new Properties();
			prop.load(configIp);

			// Determine role
			String role = System.getProperty("role");

			if (role == null || role.isBlank()) {
				role = prop.getProperty("role", "user");
			}

			// Load local credentials.properties
			try {

				FileInputStream credIp =
						new FileInputStream("./src/test/resources/config/credentials.properties");

				Properties credProp = new Properties();
				credProp.load(credIp);

				prop.setProperty("username",
						credProp.getProperty(role + ".username"));

				prop.setProperty("password",
						credProp.getProperty(role + ".password"));

				System.out.println("Using local credentials.properties");

			} catch (FileNotFoundException e) {

				System.out.println("credentials.properties not found");
			}

			// GitHub Secrets override local credentials
			String githubUser = System.getenv(role.toUpperCase() + "_USERNAME");
			String githubPassword = System.getenv(role.toUpperCase() + "_PASSWORD");

			if (githubUser != null && !githubUser.isBlank()) {

				prop.setProperty("username", githubUser);

				System.out.println("Using username from GitHub Secret");
			}

			if (githubPassword != null && !githubPassword.isBlank()) {

				prop.setProperty("password", githubPassword);

				System.out.println("Using password from GitHub Secret");
			}

			// Validation
			if (prop.getProperty("username") == null ||
				prop.getProperty("password") == null) {

				throw new RuntimeException(
						"Credentials not found for role: " + role);
			}

			// Final logging
			System.out.println("========================================");
			System.out.println("Executing tests with role: " + role);
			System.out.println("Username selected: " + prop.getProperty("username"));
			System.out.println("========================================");

		} catch (Exception e) {
			e.printStackTrace();
		}

		return prop;
	}
	
	
	/**
	 * take screenshot
	 * 
	 */

	public static String takeScreenshot() {
		String path = System.getProperty("user.dir") + "/screenshot/" + System.currentTimeMillis() + ".png";
		//getPage().screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)).setFullPage(true));
		
		byte[] buffer = getPage().screenshot(new Page.ScreenshotOptions().setPath(Paths.get(path)).setFullPage(true).setTimeout(10000));
		String base64Path = Base64.getEncoder().encodeToString(buffer);
		
		return base64Path;
	}

}
