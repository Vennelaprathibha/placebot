package com.placement;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Lightweight test that needs no database, so `mvn test` works anywhere. */
class ModelTest {
  @Test
  void userDefaultsAreSet() {
    M.User u = new M.User();
    assertEquals(0, u.backlogs);
    assertNotNull(u.registeredAt);
  }

  @Test
  void jobDefaultsAreSet() {
    M.Job j = new M.Job();
    assertNotNull(j.fetchedAt);
  }
}
