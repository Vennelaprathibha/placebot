package com.placement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.tika.Tika;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class Api {
  private final R.Users users;
  private final R.Msgs msgs;
  private final R.Jobs jobs;
  private final R.Documents documents;

  @Value("${llm.api-key:}") private String key;
  @Value("${llm.model:gemini-2.5-flash}") private String model;

  public Api(R.Users users, R.Msgs msgs, R.Jobs jobs, R.Documents documents) {
    this.users = users;
    this.msgs = msgs;
    this.jobs = jobs;
    this.documents = documents;
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@RequestBody Map<String, Object> b) {
    String name = text(b, "name");
    String email = text(b, "email").toLowerCase();
    String password = text(b, "password");

    if (name.isBlank()) return error(HttpStatus.BAD_REQUEST, "Please enter your full name.");
    if (email.isBlank() || !email.contains("@")) return error(HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
    if (password.length() < 6) return error(HttpStatus.BAD_REQUEST, "Password must contain at least 6 characters.");
    if (users.findByEmail(email).isPresent()) return error(HttpStatus.BAD_REQUEST, "This email is already registered. Please use another email.");

    M.User u = new M.User();
    u.name = name;
    u.email = email;
    u.password = hash(password);
    u.age = integer(b, "age");
    u.college = text(b, "college");
    u.branch = text(b, "branch");
    u.state = text(b, "state");
    u.address = text(b, "address");
    u.cgpa = decimal(b, "cgpa");
    u.backlogs = integer(b, "backlogs") == null ? 0 : integer(b, "backlogs");
    u.skills = text(b, "skills");
    u.photoUrl = text(b, "photoUrl");

    M.User saved = users.save(u);
    return ResponseEntity.ok(publicUser(saved));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody Map<String, Object> b) {
    String email = text(b, "email").toLowerCase(Locale.ROOT);
    String password = text(b, "password");
    M.User u = users.findByEmail(email).orElse(null);
    if (u == null || !constantTimeEquals(u.password, hash(password)))
      return error(HttpStatus.UNAUTHORIZED, "Email or password is incorrect. Please try again or create an account.");
    return ResponseEntity.ok(publicUser(u));
  }

  @PutMapping("/student/{id}")
  public ResponseEntity<?> updateStudent(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    M.User u = users.findById(id).orElse(null);
    if (u == null) return error(HttpStatus.NOT_FOUND, "Student profile was not found.");
    String name = text(b, "name");
    if (!name.isBlank()) u.name = name;
    u.age = integer(b, "age"); u.college = text(b, "college"); u.branch = text(b, "branch");
    u.state = text(b, "state"); u.address = text(b, "address"); u.cgpa = decimal(b, "cgpa");
    u.backlogs = integer(b, "backlogs") == null ? 0 : integer(b, "backlogs"); u.skills = text(b, "skills");
    String photo = text(b, "photoUrl"); if (!photo.isBlank()) u.photoUrl = photo;
    return ResponseEntity.ok(publicUser(users.save(u)));
  }

  @GetMapping("/student/{id}")
  public ResponseEntity<?> student(@PathVariable Long id) {
    return users.findById(id).<ResponseEntity<?>>map(u -> ResponseEntity.ok(publicUser(u)))
        .orElseGet(() -> error(HttpStatus.NOT_FOUND, "Student was not found."));
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> uploadDocument(@RequestParam Long userId, @RequestParam("file") MultipartFile file) {
    M.User u = users.findById(userId).orElse(null);
    if (u == null) return error(HttpStatus.NOT_FOUND, "Student profile was not found. Please register again.");
    if (file == null || file.isEmpty()) return error(HttpStatus.BAD_REQUEST, "Please choose a document first.");
    String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
    String lower = name.toLowerCase(Locale.ROOT);
    Set<String> allowed = Set.of(".pdf", ".doc", ".docx", ".txt", ".rtf", ".ppt", ".pptx", ".jpg", ".jpeg", ".png", ".webp", ".gif");
    boolean ok = allowed.stream().anyMatch(lower::endsWith);
    if (!ok) return error(HttpStatus.BAD_REQUEST, "Supported files: PDF, DOC, DOCX, TXT, RTF, PPT, PPTX and images.");
    if (file.getSize() > 8_000_000) return error(HttpStatus.BAD_REQUEST, "Please upload a file smaller than 8 MB.");
    try {
      String extracted = new Tika().parseToString(file.getInputStream());
      if (extracted == null) extracted = "";
      extracted = extracted.replaceAll("\\s+", " ").trim();
      if (extracted.length() > 11000) extracted = extracted.substring(0, 11000);
      M.Document d = new M.Document();
      d.userId = userId; d.fileName = name; d.contentType = file.getContentType(); d.extractedText = extracted;
      if (lower.matches(".*\\.(jpg|jpeg|png|webp|gif)$")) d.imageData = Base64.getEncoder().encodeToString(file.getBytes());
      documents.save(d);
      Map<String,Object> out = new LinkedHashMap<>();
      out.put("id", d.id); out.put("fileName", name); out.put("message", extracted.isBlank() && lower.matches(".*\\.(jpg|jpeg|png|webp)$")
          ? "Image uploaded. Ask a question about it in the message box and send it together with your text."
          : "Document uploaded successfully. Ask me to review, summarize or use it for your placement training.");
      return ResponseEntity.ok(out);
    } catch (Exception e) {
      return error(HttpStatus.BAD_REQUEST, "I could not read this file. Please try a PDF, DOCX or TXT document.");
    }
  }

  @GetMapping("/documents/{userId}")
  public ResponseEntity<?> documents(@PathVariable Long userId) {
    List<Map<String,Object>> out = new ArrayList<>();
    for (M.Document d : documents.findTop5ByUserIdOrderByUploadedAtDesc(userId)) {
      out.add(Map.of("id", d.id, "fileName", d.fileName, "contentType", d.contentType == null ? "" : d.contentType));
    }
    return ResponseEntity.ok(out);
  }

  @GetMapping("/chat/{userId}")
  public ResponseEntity<?> chatHistory(@PathVariable Long userId) {
    if (!users.existsById(userId)) return error(HttpStatus.NOT_FOUND, "Student profile was not found.");
    List<Map<String, Object>> history = new ArrayList<>();
    List<M.Msg> saved = msgs.findTop40ByUserIdOrderByIdDesc(userId);
    Collections.reverse(saved);
    for (M.Msg m : saved) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("sender", m.sender); item.put("message", m.message);
      history.add(item);
    }
    return ResponseEntity.ok(history);
  }

  @PostMapping("/chat")
  public ResponseEntity<?> chat(@RequestBody Map<String, Object> b) {
    Long uid;
    try { uid = Long.valueOf(String.valueOf(b.get("userId"))); }
    catch (Exception e) { return error(HttpStatus.BAD_REQUEST, "Please register before using the chatbot."); }

    M.User u = users.findById(uid).orElse(null);
    if (u == null) return error(HttpStatus.NOT_FOUND, "Student profile was not found. Please register again.");

    String message = text(b, "message").trim();
    Long attachedDocumentId = null;
    try { if (b.get("documentId") != null) attachedDocumentId = Long.valueOf(String.valueOf(b.get("documentId"))); } catch (Exception ignored) {}
    if (message.isBlank()) return error(HttpStatus.BAD_REQUEST, "Please enter a question.");

    saveMessage(uid, "USER", message);
    String docContext = documentContext(uid, attachedDocumentId);
    String reply = key == null || key.isBlank() ? ruleBasedReply(u, message, docContext) : aiReply(u, message, docContext);
    saveMessage(uid, "BOT", reply);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("reply", reply);
    result.put("student", publicUser(u));
    return ResponseEntity.ok(result);
  }

  private String ruleBasedReply(M.User u, String message) {
    return ruleBasedReply(u, message, documentContext(u.id));
  }

  private String ruleBasedReply(M.User u, String message, String docContext) {
    String q = message.toLowerCase(Locale.ROOT);
    String skills = u.skills == null || u.skills.isBlank() ? "your current skills" : u.skills;

    if (!docContext.isBlank() && containsAny(q, "summarize my document", "summarize document", "summary of my document"))
      return "Here is the useful placement summary from your latest uploaded document: " + docContext.substring(0, Math.min(2500, docContext.length())) + (docContext.length() > 2500 ? "…" : "");
    if (!docContext.isBlank() && containsAny(q, "eligibility", "eligible from document", "criteria from document"))
      return "I found information in your uploaded document. Check the qualification, branch, CGPA, backlog, graduation-year and other company-specific conditions. Ask me 'summarize my document' if you want the extracted content first.";
    if (containsAny(q, "mock interview", "practice interview", "interview me"))
      return "Mock interview started 🎤. Question 1: Tell me about yourself in 60–90 seconds. Send your answer and I will suggest improvements before giving you the next question.";
    if (containsAny(q, "daily plan", "weekly plan", "study plan"))
      return "Here is a simple weekly plan: Mon–Tue: coding/DSA; Wed: aptitude; Thu: CS fundamentals; Fri: resume/project preparation; Sat: mock interview; Sun: revise mistakes and apply to suitable roles. I can customize it if you tell me your target role and available hours.";
    String arithmetic = solveArithmetic(message);
    if (arithmetic != null) return "Answer: " + arithmetic;
    if (containsAny(q, "hello", "hi", "hey"))
      return "Hi " + u.name + "! I am your Placement Assistant. Ask me about jobs, resumes, interviews, aptitude, skills or placement preparation.";
    if (containsAny(q, "resume", "cv"))
      return "For your resume, keep it to 1 page if possible. Highlight your skills (" + skills + "), projects, internships, education and measurable achievements. Tailor the resume to each job role.";
    if (containsAny(q, "interview", "technical", "hr"))
      return "For interviews, prepare Core Java/OOP or your branch fundamentals, SQL/DBMS, basic DSA, projects, resume questions and common HR questions such as Tell me about yourself and Why should we hire you?";
    if (containsAny(q, "aptitude", "reasoning", "quant"))
      return "For aptitude, practise percentages, ratios, averages, time and work, profit and loss, number systems, logical reasoning and data interpretation. Start with timed practice.";
    if (containsAny(q, "java", "python", "coding", "dsa", "programming"))
      return "For coding placements, build strong fundamentals in one language, then practise arrays, strings, searching, sorting, hashing, recursion and basic data structures. Solve easy problems first and increase difficulty gradually.";
    if (containsAny(q, "job", "jobs", "opening", "vacancy", "career", "company"))
      return jobReply(q);
    if (containsAny(q, "cgpa", "backlog", "eligib", "eligible"))
      return "Placement eligibility depends on the company. Your registered profile has CGPA " + (u.cgpa == null ? "not provided" : u.cgpa) + " and " + (u.backlogs == null ? 0 : u.backlogs) + " active backlog(s). Always verify the exact eligibility criteria in the official placement notice.";
    if (containsAny(q, "roadmap", "prepare", "preparation", "plan"))
      return "A simple placement plan: Month 1 - aptitude + coding fundamentals; Month 2 - DSA + CS fundamentals; Month 3 - projects + resume + mock interviews; then continue company-specific practice and applications.";
    if (containsAny(q, "skill", "skills", "improve"))
      return "Based on your profile, your current skills are " + skills + ". Pick one target role and compare its required skills with yours. Then choose 2-3 missing skills to learn instead of trying to learn everything at once.";
    if (containsAny(q, "document", "uploaded", "file", "resume", "notice", "pdf", "docx") && !docContext.isBlank())
      return "I found your uploaded document. Here is a useful way to use it for placement training: identify the required skills and eligibility first, then turn the topics into a practice checklist. If you ask me 'summarize my document', 'find eligibility' or 'make interview questions from my document', I can work from the uploaded text.";

    return "I cannot answer this question reliably because PlaceBot's AI service is not configured. Add your Google Gemini API key to GEMINI_API_KEY in the .env file beside docker-compose.yml, then rebuild with docker compose up --build -d. This is a setup issue, not a problem with your question.";
  }

  private String jobReply(String q) {
    List<M.Job> matches = new ArrayList<>();
    for (String word : q.split("\\W+")) {
      if (word.length() > 2) {
        List<M.Job> found = jobs.findTop10ByTitleContainingIgnoreCaseOrCompanyContainingIgnoreCase(word, word);
        for (M.Job j : found) if (matches.stream().noneMatch(x -> Objects.equals(x.id, j.id))) matches.add(j);
      }
    }
    if (matches.isEmpty()) {
      return "I can help you identify suitable roles such as Java Developer, Python Developer, Web Developer, Data Analyst, Software Engineer and QA Engineer. Tell me the role, location or skills you are interested in.";
    }
    StringBuilder s = new StringBuilder("Here are some matching sample opportunities: ");
    matches.stream().limit(5).forEach(j -> s.append(j.title).append(" at ").append(j.company).append(" (" ).append(j.location).append("); "));
    s.append("Please verify current openings on the company's official careers page before applying.");
    return s.toString();
  }

  private String aiReply(M.User u, String message) {
    return aiReply(u, message, documentContext(u.id));
  }

  private String aiReply(M.User u, String message, String docContext) {
    try {
      RestTemplate rt = new RestTemplate();
      HttpHeaders h = new HttpHeaders();
      h.set("x-goog-api-key", key);
      h.setContentType(MediaType.APPLICATION_JSON);

      String profile = "Student name: " + u.name + ", college: " + u.college + ", branch: " + u.branch + ", state: " + u.state + ", CGPA: " + u.cgpa + ", backlogs: " + u.backlogs + ", skills: " + u.skills;
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("systemInstruction", Map.of("parts", List.of(Map.of("text",
          "You are PlaceBot, a helpful general-purpose conversational AI. Answer the user's actual question across academic, technical, creative, everyday and career topics; do not force unrelated questions into placement training. Use conversation history to resolve follow-ups. Give clear, useful, step-by-step solutions when appropriate. For career questions, personalize advice using the student profile. Never invent live vacancies or company eligibility rules. Student profile: " + profile + "\nContext from uploaded document(s): " + docContext))));

      List<Map<String, Object>> conversation = new ArrayList<>();
      List<M.Msg> recent = msgs.findTop40ByUserIdOrderByIdDesc(u.id);
      Collections.reverse(recent);
      Long latestUserMessageId = null;
      for (int i = recent.size() - 1; i >= 0; i--) {
        if ("USER".equals(recent.get(i).sender)) { latestUserMessageId = recent.get(i).id; break; }
      }
      for (M.Msg item : recent) {
        if (item.message == null || item.message.isBlank()) continue;
        String role = "USER".equals(item.sender) ? "user" : "model";
        List<Map<String, Object>> parts = new ArrayList<>();
        if ("user".equals(role) && Objects.equals(item.id, latestUserMessageId)) {
          M.Document imageDoc = documents.findTop5ByUserIdOrderByUploadedAtDesc(u.id).stream()
              .filter(d -> d.imageData != null && !d.imageData.isBlank() && item.message.toLowerCase(Locale.ROOT).contains(d.fileName.toLowerCase(Locale.ROOT)))
              .findFirst().orElse(null);
          if (imageDoc != null) {
            String media = imageDoc.contentType == null ? "image/png" : imageDoc.contentType;
            if (!Set.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(media)) media = "image/png";
            parts.add(Map.of("inlineData", Map.of("mimeType", media, "data", imageDoc.imageData)));
          }
        }
        parts.add(Map.of("text", item.message));
        // Gemini accepts a list of parts for multimodal user content and text history.
        conversation.add(Map.of("role", role, "parts", parts));
      }
      if (conversation.isEmpty() || !"user".equals(conversation.get(conversation.size() - 1).get("role"))) {
        conversation.add(Map.of("role", "user", "parts", List.of(Map.of("text", message))));
      }
      // Gemini expects alternating user/model turns. Merge adjacent turns if history contains duplicates.
      List<Map<String, Object>> normalized = new ArrayList<>();
      for (Map<String, Object> turn : conversation) {
        String role = String.valueOf(turn.get("role"));
        if (!normalized.isEmpty() && role.equals(normalized.get(normalized.size()-1).get("role"))) {
          Map<String,Object> prev = normalized.get(normalized.size()-1);
          List<Map<String,Object>> merged = new ArrayList<>((List<Map<String,Object>>) prev.get("parts"));
          merged.addAll((List<Map<String,Object>>) turn.get("parts"));
          prev.put("parts", merged);
        } else normalized.add(new LinkedHashMap<>(turn));
      }
      if (normalized.size() > 40) normalized = new ArrayList<>(normalized.subList(normalized.size()-40, normalized.size()));
      body.put("contents", normalized);
      body.put("generationConfig", Map.of("maxOutputTokens", 1200, "temperature", 0.7));

      String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
      Map<?, ?> response = rt.postForObject(url, new HttpEntity<>(body, h), Map.class);
      if (response != null && response.get("candidates") instanceof List<?> candidates && !candidates.isEmpty()
          && candidates.get(0) instanceof Map<?, ?> candidate
          && candidate.get("content") instanceof Map<?, ?> content
          && content.get("parts") instanceof List<?> responseParts) {
        StringBuilder answer = new StringBuilder();
        for (Object part : responseParts) if (part instanceof Map<?, ?> partMap && partMap.get("text") != null) answer.append(partMap.get("text"));
        if (!answer.isEmpty()) return answer.toString();
      }
      return "Gemini returned an empty response. Please try again.";
    } catch (Exception ex) {
      return "I couldn't reach Gemini just now (" + safeError(ex) + "). Check that GEMINI_API_KEY in your .env file is valid, that the Gemini API is enabled for its project, and that your internet connection works. Then run docker compose up --build -d again. Your message is saved.";
    }
  }

  private String documentContext(Long userId) {
    return documentContext(userId, null);
  }

  private String documentContext(Long userId, Long documentId) {
    StringBuilder out = new StringBuilder();
    List<M.Document> selected;
    if (documentId != null) {
      M.Document d = documents.findById(documentId).filter(x -> Objects.equals(x.userId, userId)).orElse(null);
      selected = d == null ? List.of() : List.of(d);
    } else {
      selected = documents.findTop5ByUserIdOrderByUploadedAtDesc(userId);
    }
    for (M.Document d : selected) {
      if (d.extractedText != null && !d.extractedText.isBlank()) {
        out.append("[Attached file: ").append(d.fileName).append("] ").append(d.extractedText).append("\n");
      } else if (d.imageData != null && !d.imageData.isBlank()) {
        out.append("[Attached image: ").append(d.fileName).append(". The image is attached separately to the AI request when AI is configured.]\n");
      } else {
        out.append("[Attached file: ").append(d.fileName).append(". No readable text could be extracted from this file.]\n");
      }
    }
    return out.length() > 11000 ? out.substring(0, 11000) : out.toString();
  }

  private void saveMessage(Long userId, String sender, String message) {
    M.Msg m = new M.Msg();
    m.userId = userId;
    m.sender = sender;
    m.message = message;
    msgs.save(m);
  }

  private static Map<String, Object> publicUser(M.User u) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("id", u.id); p.put("name", u.name); p.put("email", u.email); p.put("age", u.age);
    p.put("college", u.college); p.put("branch", u.branch); p.put("state", u.state); p.put("address", u.address);
    p.put("cgpa", u.cgpa); p.put("backlogs", u.backlogs); p.put("skills", u.skills); p.put("photoUrl", u.photoUrl);
    return p;
  }

  private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("error", message));
  }

  private static String text(Map<String, Object> b, String key) {
    Object value = b.get(key);
    return value == null ? "" : value.toString().trim();
  }
  private static Integer integer(Map<String, Object> b, String key) {
    try { return text(b, key).isBlank() ? null : Integer.valueOf(text(b, key)); } catch (Exception e) { return null; }
  }
  private static Double decimal(Map<String, Object> b, String key) {
    try { return text(b, key).isBlank() ? null : Double.valueOf(text(b, key)); } catch (Exception e) { return null; }
  }
  private static boolean containsAny(String text, String... words) {
    for (String word : words) if (text.contains(word)) return true;
    return false;
  }
  private static String safeError(Exception ex) {
    Throwable cause = ex;
    while (cause.getCause() != null) cause = cause.getCause();
    String m = cause.getMessage();
    if (m == null || m.isBlank()) return ex.getClass().getSimpleName();
    return m.length() > 180 ? m.substring(0, 180) : m;
  }
  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
  private static String solveArithmetic(String input) {
    String expression = input.trim().replace("×", "*").replace("÷", "/").replace("−", "-");
    if (!expression.matches("[0-9\\s+*/().%\\-]+") || !expression.matches(".*[+*/%\\-].*")) return null;
    try {
      ArithmeticParser parser = new ArithmeticParser(expression);
      double value = parser.parse();
      if (!Double.isFinite(value)) return null;
      if (Math.abs(value - Math.rint(value)) < 1e-10) return Long.toString((long)Math.rint(value));
      return String.format(Locale.ROOT, "%.6f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    } catch (Exception ignored) { return null; }
  }
  private static final class ArithmeticParser {
    private final String s; private int i;
    ArithmeticParser(String s) { this.s=s; }
    double parse() { double v=expr(); ws(); if(i!=s.length()) throw new IllegalArgumentException(); return v; }
    double expr() { double v=term(); while(true){ws(); if(take('+'))v+=term();else if(take('-'))v-=term();else return v;} }
    double term() { double v=factor(); while(true){ws(); if(take('*'))v*=factor();else if(take('/'))v/=factor();else if(take('%'))v%=factor();else return v;} }
    double factor() { ws(); if(take('+'))return factor();if(take('-'))return -factor();if(take('(')){double v=expr();ws();if(!take(')'))throw new IllegalArgumentException();return v;}int start=i;while(i<s.length()&&(Character.isDigit(s.charAt(i))||s.charAt(i)=='.'))i++;if(start==i)throw new IllegalArgumentException();return Double.parseDouble(s.substring(start,i)); }
    void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;} boolean take(char c){if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}
  }
  private static String hash(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte b : bytes) out.append(String.format("%02x", b));
      return out.toString();
    } catch (Exception e) { throw new IllegalStateException("Password hashing failed", e); }
  }
}
