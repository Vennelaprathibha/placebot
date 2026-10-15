package com.placement;

import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Small operational endpoints: list jobs and trigger the Selenium scraper on demand. */
@RestController
@RequestMapping("/api/jobs")
@CrossOrigin
public class Ops {
  private final R.Jobs jobs;
  private final Scraper scraper;

  public Ops(R.Jobs jobs, Scraper scraper) { this.jobs = jobs; this.scraper = scraper; }

  @GetMapping
  public List<M.Job> list() { return jobs.findAll(); }

  /** POST /api/jobs/refresh runs the Selenium scraper now and returns how many new jobs were saved. */
  @PostMapping("/refresh")
  public Map<String, Object> refresh() {
    int added = scraper.run();
    return Map.of("added", added, "total", jobs.count());
  }
}
