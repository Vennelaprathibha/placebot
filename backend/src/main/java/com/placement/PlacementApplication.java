package com.placement;

import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableScheduling
public class PlacementApplication {
  public static void main(String[] args) {
    SpringApplication.run(PlacementApplication.class, args);
  }

  @Bean
  CommandLineRunner seed(R.Jobs jobs) {
    return args -> {
      if (jobs.count() > 0) return;
      String[][] sample = {
          {"Java Developer", "TechNova", "Hyderabad"},
          {"Python Developer", "DataNest", "Bengaluru"},
          {"Web Developer", "PixelWorks", "Chennai"},
          {"Data Analyst", "Insight Labs", "Pune"},
          {"Software Engineer", "NextGen Systems", "Hyderabad"},
          {"QA Engineer", "QualityHub", "Remote"}
      };
      for (int i = 0; i < sample.length; i++) {
        M.Job j = new M.Job();
        j.title = sample[i][0];
        j.company = sample[i][1];
        j.location = sample[i][2];
        j.applyLink = "https://example.com/placement-demo/" + (i + 1);
        jobs.save(j);
      }
    };
  }
}
