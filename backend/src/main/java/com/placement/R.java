package com.placement;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface R {
  interface Users extends JpaRepository<M.User, Long> {
    Optional<M.User> findByEmail(String email);
  }
  interface Msgs extends JpaRepository<M.Msg, Long> {
    List<M.Msg> findTop40ByUserIdOrderByIdDesc(Long userId);
  }
  interface Documents extends JpaRepository<M.Document, Long> {
    List<M.Document> findTop5ByUserIdOrderByUploadedAtDesc(Long userId);
  }
  interface Jobs extends JpaRepository<M.Job, Long> {
    List<M.Job> findTop10ByTitleContainingIgnoreCaseOrCompanyContainingIgnoreCase(String title, String company);
    boolean existsByApplyLink(String link);
  }
}
