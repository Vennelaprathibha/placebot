package com.placement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

public class M {
  @Entity @Table(name = "students")
  public static class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String name;
    @Column(unique = true, nullable = false) public String email;
    @JsonIgnore public String password;
    public Integer age;
    public String college;
    public String branch;
    public String state;
    @Column(length = 1000) public String address;
    public Double cgpa;
    public Integer backlogs = 0;
    @Column(length = 2000) public String skills;
    @Lob public String photoUrl;
    public LocalDateTime registeredAt = LocalDateTime.now();
  }

  @Entity @Table(name = "student_documents")
  public static class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long userId;
    public String fileName;
    public String contentType;
    @Column(length = 12000) public String extractedText;
    @Lob public String imageData;
    public LocalDateTime uploadedAt = LocalDateTime.now();
  }

  @Entity @Table(name = "chat_messages")
  public static class Msg {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long userId;
    public String sender;
    @Column(length = 4000) public String message;
    public LocalDateTime createdAt = LocalDateTime.now();
  }

  @Entity @Table(name = "jobs")
  public static class Job {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String title;
    public String company;
    public String location;
    @Column(unique = true, length = 500) public String applyLink;
    public LocalDateTime fetchedAt = LocalDateTime.now();
  }
}
