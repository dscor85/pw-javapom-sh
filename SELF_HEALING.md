# Self-Healing Locator Framework

## Important note on this build

I could not run `mvn package` inside the sandbox this was built in — outbound network access
there is limited to a fixed allowlist (GitHub, npm, PyPI, Ubuntu archives, crates.io) and does
**not** include Maven Central, so dependency resolution fails immediately with a 403 before
compilation even starts. Every file here was written and re-checked by hand (import usage,
brace/paren balance, Playwright/Jackson API signatures against their real method shapes), but
it has **not been compiled**. Run this yourself before trusting it:

```bash
mvn -DskipTests package
```

(Note: the flag is `-DskipTests`, capital T. `-Dskiptests`, as lowercase, is silently ignored by
Maven and won't actually skip tests — worth knowing generally, not just for this project.)

If it doesn't compile cleanly, tell me the exact error and I'll fix it directly.

## What this is

Every existing page object's raw `page.click()` / `page.fill()` / `page.textContent()` /
`page.isVisible()` calls now go through a `HealingPage` wrapper instead. When one of those
calls can't find its element at all, the framework:

1. Captures the page URL, DOM, the locator that failed, the operation, the page object, and
   the exception — automatically, with no change needed in test code.
2. Calls a configured AI provider, asking for a replacement CSS/XPath locator.
3. Retries the same action with that replacement.
4. If it works, the test keeps running and passes.
5. The replacement is saved to `self-healing-reports/healed-locators.json` (never into your
   `.java` files) and reused automatically on future runs for the same element.
6. After the full TestNG run finishes, `self-healing-reports/SelfHealingReport.html` is
   generated, listing every locator failure analyzed this run.

## Package layout (`src/main/java/com/qa/opencart/selfhealing/`)

> **Naming note:** the requirement specified package `com.ga.opencart.selfhealing`. The actual
> codebase uses `com.qa.opencart` everywhere (constants, factory, pages, listeners) — `ga`
> appears to be a typo for `qa`. Everything here is built under `com.qa.opencart.selfhealing`
> to stay consistent with the rest of the project. If you genuinely meant a different base
> package, it's a rename away.

| Class | Responsibility |
|---|---|
| `HealingPage` | Drop-in wrapper: `click()`, `fill()`, `textContent()`, `isVisible()` |
| `HealingEngine` | Core orchestration: try original/healed locator → detect failure → call AI → retry → log |
| `SelfHealingConfig` | Loads `self-healing.properties`, exposes typed config |
| `HealedLocatorStore` | Persists successful replacements, reused across runs |
| `HealingAttemptLog` | Per-run log of every attempt (healed or failed), feeds the HTML report |
| `HealingContext` | Everything captured at failure time, passed to the AI provider |
| `HealingAttemptEntry` | One report row |
| `SelfHealingEngineFactory` | Builds one shared engine per run |
| `HealingTestContext` | ThreadLocal current-test-name, set by the listener |
| `SelfHealingListener` | TestNG listener: tags test name, generates the HTML report in `onFinish` |
| `ai/AiLocatorProvider` | The vendor-neutral interface — this is the only thing the engine depends on |
| `ai/AiLocatorSuggestion` | What a provider returns: locator type/value, confidence, reasoning |
| `ai/OpenAiCompatibleProvider` | Built-in HTTP provider for any OpenAI-compatible chat-completions endpoint |
| `ai/AiProviderFactory` | Instantiates the configured provider, built-in or custom, via reflection |

## One deliberate API choice worth knowing

The spec describes `HealingPage.click()` / `.fill()` / `.textContent()` / `.isVisible()`
mirroring the raw `Page` methods. I added one parameter to each: a short **logical element
name** you choose (`"login.emailId"`, `"home.searchIcon"`, ...), alongside the actual locator:

```java
healingPage.fill("login.emailId", emailId, appUserName);
healingPage.click("login.loginBtn", loginBtn);
healingPage.textContent("home.searchPageHeader", searchPageHeader);
healingPage.isVisible("login.forgotPwdLink", forgotPwdLink);
```

Without this, `healed-locators.json` and the HTML report would have to key everything off the
raw selector string, which breaks the moment that string is exactly what needs healing, and
makes the report far less readable. This is the only signature deviation from the spec.

## Configuration

`src/test/resources/config/self-healing.properties`:

```properties
self.healing.enabled=true
self.healing.output.dir=./self-healing-reports
self.healing.max.attempts=1

self.healing.ai.endpoint=
self.healing.ai.model=
self.healing.ai.api.key.env=

self.healing.ai.provider.class=
```

> **Naming note:** the requirement's config block used `self.healing.ai-provider.class`
> (hyphen) in one place and `self.healing.ai.provider.class` (dot) everywhere else. I used the
> dot form consistently, matching the majority of the spec and the other `self.healing.ai.*`
> keys.

- **`self.healing.enabled`** — master switch. `false` makes every `HealingPage` call behave
  exactly like the original raw `page.*` call: no interception, no AI calls, and — critically —
  `isVisible()` still never throws.
- **`self.healing.output.dir`** — where `healed-locators.json`, `healing-attempts.json`, and
  `SelfHealingReport.html` are written.
- **`self.healing.max.attempts`** — how many times to re-query the AI provider for a *new*
  suggestion if its previous suggestion also fails to resolve, before giving up on that element
  for this run.
- **`self.healing.ai.endpoint` / `.model` / `.api.key.env`** — only used by the built-in
  provider. `api.key.env` is the **name** of an environment variable, e.g. `OPENAI_API_KEY` —
  never put the key itself here.
- **`self.healing.ai.provider.class`** — leave blank to use the built-in provider. Set to a
  fully-qualified class name to use your own.

Any of these can be overridden at the command line without editing the file, e.g.:

```bash
mvn test -Dself.healing.enabled=false
```

### API keys

Never in source, never in a properties file. Set the environment variable named by
`self.healing.ai.api.key.env` before running tests:

```bash
export OPENAI_API_KEY=sk-...
mvn test
```

If `self.healing.ai.api.key.env` is left blank, the built-in provider sends no `Authorization`
header at all — useful for self-hosted OpenAI-compatible servers that don't require one.

## Using a custom AI provider

Implement the interface:

```java
package com.example;

import java.util.Map;
import com.qa.opencart.selfhealing.HealingContext;
import com.qa.opencart.selfhealing.ai.AiLocatorProvider;
import com.qa.opencart.selfhealing.ai.AiLocatorSuggestion;

public class MyProvider implements AiLocatorProvider {

    public MyProvider() { } // must have a public no-arg constructor -- instantiated via reflection

    @Override
    public void configure(Map<String, String> settings) {
        // settings contains endpoint/model/apiKeyEnvVar/outputDir/maxAttempts as strings,
        // in case your provider wants any of them
    }

    @Override
    public AiLocatorSuggestion suggestLocator(HealingContext context) throws Exception {
        // context.domSnapshot, context.originalLocator, context.failedLocator,
        // context.operation, context.pageUrl, context.elementKey, etc. are all available
        return new AiLocatorSuggestion("CSS", "#some-selector", 82, "matched by id + type");
    }
}
```

Then point at it:

```properties
self.healing.ai.provider.class=com.example.MyProvider
```

No other code changes needed — `AiProviderFactory` instantiates it via reflection and calls
`configure()` automatically.

## Refactoring more page objects

Only `HomePage` and `LoginPage` exist in this project today, and both are already refactored.
For any new page object:

```java
public class SomePage {
    private Page page;
    private HealingPage healingPage;

    private String someField = "#some-id";

    public SomePage(Page page) {
        this.page = page;
        this.healingPage = new HealingPage(page, SomePage.class);
    }

    public void doThing() {
        healingPage.click("somePage.someField", someField);
    }
}
```

## Running it

```bash
mvn -DskipTests package        # confirm it compiles
mvn test                        # runs the suite; testng_regressions.xml already has both listeners wired in
```

After a run, check:

```
self-healing-reports/healed-locators.json     # persists across runs
self-healing-reports/healing-attempts.json    # this run only
self-healing-reports/SelfHealingReport.html   # open this in a browser
```

If no locator ever failed, none of these three files are (re)written with content and the
listener logs a line saying so — there's nothing to review.

## Important limitation

**Self-healing only works for calls routed through `HealingPage`.** Any direct
`page.locator()`, `page.click()`, `page.fill()`, `page.textContent()`, `page.isVisible()`, or
similar call anywhere in a page object completely bypasses this mechanism — `HealingEngine`
never sees that call, so a broken locator there just fails normally, with no healing attempt
and no report entry. If you add a new page object or a new method to an existing one, route it
through `HealingPage` or it isn't covered.

## Other known limitations

- **`isVisible()` healing is a judgment call, not a certainty.** Playwright's real
  `isVisible()` never throws — a locator matching zero elements just returns `false`. To make
  such cases heal-able at all, this framework treats "matches zero elements" as a *candidate*
  locator failure and attempts healing before falling back to `false`. This means a genuinely
  and correctly absent element (e.g., checking a logged-out-only link while logged in) will
  briefly trigger a healing attempt every time, which costs one AI call and a few seconds. It
  will simply fail to find a better match and log a FAILED entry — it won't lie about
  visibility — but it isn't free. If this becomes noisy for a particular `isVisible()` check
  that's supposed to correctly return `false` often, don't route that specific check through
  `HealingPage`.
- **DOM sent to the AI provider is truncated** (20,000 characters) to keep requests
  reasonably sized. On very large pages, the relevant element could theoretically fall outside
  that window.
- **No promotion/review gate for `healed-locators.json`.** A healed locator is trusted and
  reused immediately on the very next run once it works once. There's no "N successful runs
  before it's trusted" mechanism in this version. If you want that, it's a reasonable
  follow-up.
- **Confidence, reasoning, and even the suggested locator's correctness are only as good as
  the AI provider.** Nothing here validates that a "HEALED" entry with high confidence is
  actually semantically correct — it just means the retried action didn't throw. Review
  `SelfHealingReport.html` periodically rather than assuming HEALED entries are always right.
- **No pipeline/CI wiring included.** This is deliberately local-execution-only, matching the
  requirements as given — nothing here pushes changes back to a repository automatically.
