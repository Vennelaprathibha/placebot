package com.placement;

import java.net.URL;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Selenium job fetcher. Targets a public practice site; change URL + selectors for a real portal (check its terms first). */
@Component
public class Scraper {
  @Value("${selenium.url:}") String remote;
  final R.Jobs jobs;
  public Scraper(R.Jobs jobs) { this.jobs = jobs; }

  @Scheduled(cron = "0 0 */6 * * *")
  public int run() {
    ChromeOptions o = new ChromeOptions();
    o.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
    WebDriver d = null; int added = 0;
    try {
      d = remote.isBlank() ? new ChromeDriver(o) : new RemoteWebDriver(new URL(remote), o);
      d.get("https://realpython.github.io/fake-jobs/");
      for (WebElement c : d.findElements(By.cssSelector(".card-content"))) {
        String link = c.findElements(By.tagName("a")).get(1).getAttribute("href");
        if (jobs.existsByApplyLink(link)) continue;
        M.Job j = new M.Job();
        j.title = c.findElement(By.cssSelector(".title")).getText();
        j.company = c.findElement(By.cssSelector(".company")).getText();
        j.location = c.findElement(By.cssSelector(".location")).getText();
        j.applyLink = link; jobs.save(j); added++;
      }
    } catch (Exception e) { System.err.println("Scrape failed: " + e.getMessage()); }
    finally { if (d != null) d.quit(); }
    return added;
  }
}
