package com.project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:5173"})
@RequestMapping("/api/customer-portal")
public class CustomerPortalController {
  private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
  private static final Set<String> TRANSACTION_TYPES = Set.of("PURCHASE", "REFUND", "AUTHORIZATION", "REVERSAL", "CHARGEBACK");
  private static final Set<String> PAYMENT_METHODS = Set.of("CHIP", "SWIPE", "CONTACTLESS", "ONLINE", "MOBILE_WALLET");
  private final ObjectMapper json;
  private final CardRequestRepository requests;
  private final ManagedCustomerRepository owners;
  private final SecretKey key;
  private final HttpClient http = HttpClient.newHttpClient();

  public CustomerPortalController(ObjectMapper json, CardRequestRepository requests, ManagedCustomerRepository owners, @Value("${app.jwt.secret}") String secret) {
    this.json = json;
    this.requests = requests;
    this.owners = owners;
    this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(@RequestHeader("Authorization") String auth) throws Exception {
    int customerId = customerId(auth);
    List<Map<String, Object>> cards = cards(customerId, auth);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("customer", get("http://localhost:8081/customer/" + customerId, auth));
    result.put("cards", cards);
    result.put("transactions", transactions(cards, auth));
    result.put("cardRequests", requests.findByCustomerIdOrderByIdDesc(customerId));
    result.put("availableMerchants", activeMerchants(auth));
    return result;
  }

  @GetMapping("/cards")
  public List<Map<String, Object>> cardsEndpoint(@RequestHeader("Authorization") String auth) throws Exception {
    return cards(customerId(auth), auth);
  }

  @GetMapping("/transactions")
  public List<Map<String, Object>> transactionsEndpoint(@RequestHeader("Authorization") String auth) throws Exception {
    return transactions(cards(customerId(auth), auth), auth);
  }

  @GetMapping("/merchants")
  public List<Map<String, Object>> merchantsEndpoint(@RequestHeader("Authorization") String auth) throws Exception {
    customerId(auth);
    return activeMerchants(auth);
  }

  @PostMapping("/transactions")
  public Object createTransaction(@RequestHeader("Authorization") String auth, @RequestBody CustomerTransactionInput input) throws Exception {
    int customerId = customerId(auth);
    Map<String, Object> card = own(input.cardId(), auth);
    if (!String.valueOf(customerId).equals(String.valueOf(card.get("customerId")))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This card does not belong to the signed-in customer.");
    }
    if (card.get("replacedByCreditId") != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A replaced card cannot make transactions.");
    }
    if (!"ACTIVE".equalsIgnoreCase(String.valueOf(card.get("status")))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Transactions are allowed only for active cards.");
    }
    Object expiryValue = card.get("expiryDate");
    if (expiryValue != null && LocalDate.parse(String.valueOf(expiryValue).substring(0, 10)).isBefore(LocalDate.now(INDIA))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Expired cards cannot make transactions. Renew the card first.");
    }
    String transactionType = normalized(input.transactionType());
    String paymentMethod = normalized(input.paymentMethod());
    if (!TRANSACTION_TYPES.contains(transactionType)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid transaction type.");
    }
    if (!PAYMENT_METHODS.contains(paymentMethod)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid payment method.");
    }
    if (input.amount() == null || input.amount().signum() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction amount must be greater than zero.");
    }
    Map<String, Object> merchant = activeMerchants(auth).stream()
      .filter(item -> "ACTIVE".equalsIgnoreCase(String.valueOf(item.getOrDefault("status", "ACTIVE"))))
      .filter(item -> String.valueOf(merchantId(item)).equals(String.valueOf(input.merchantId())))
      .findFirst()
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active merchant."));
    if (Set.of("PURCHASE", "AUTHORIZATION").contains(transactionType)) {
      double available = number(card.get("availableCredit"));
      if (input.amount().doubleValue() > available) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Amount exceeds available credit.");
      }
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("cardNumber", card.get("cardNumber"));
    payload.put("cardHolderName", card.get("cardHolderName"));
    payload.put("amount", input.amount());
    payload.put("currency", "INR");
    payload.put("merchantId", merchantId(merchant));
    payload.put("merchantName", merchantName(merchant));
    payload.put("transactionType", transactionType);
    payload.put("status", "PENDING");
    payload.put("paymentMethod", paymentMethod);
    payload.put("timestamp", LocalDateTime.now(INDIA).toString());
    payload.put("international", false);
    payload.put("fee", 0);
    return send("POST", "http://localhost:8083/transaction", payload, auth, "transaction service");
  }

  @GetMapping("/requests")
  public List<CardRequest> requests(@RequestHeader("Authorization") String auth) {
    return requests.findByCustomerIdOrderByIdDesc(customerId(auth));
  }

  @PostMapping("/requests")
  public ResponseEntity<?> request(@RequestHeader("Authorization") String auth, @RequestBody CardRequestInput input) throws Exception {
    int customerId = customerId(auth);
    String tier = tier(input.cardName());
    String type = type(input.cardType());
    if (tier == null || type == null) return ResponseEntity.badRequest().body(Map.of("message", "Choose a valid card tier and card type."));
    List<Map<String, Object>> currentCards = cards(customerId, auth).stream().filter(card -> card.get("replacedByCreditId") == null).toList();
    List<CardRequest> pending = requests.findByCustomerIdOrderByIdDesc(customerId).stream().filter(item -> Set.of("PENDING", "ON_HOLD").contains(item.getStatus().toUpperCase())).toList();
    if (currentCards.size() + pending.size() >= 2) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "You can have a maximum of 2 cards. A new card cannot be requested."));
    boolean typeExists = currentCards.stream().anyMatch(card -> type.equalsIgnoreCase(String.valueOf(card.get("cardType")))) || pending.stream().anyMatch(item -> type.equalsIgnoreCase(item.getCardType()));
    if (typeExists) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", type.replace("_", "-") + " card already exists or is awaiting approval. Request the other card type."));
    if (!currentCards.isEmpty() && !tier.equalsIgnoreCase(String.valueOf(currentCards.get(0).get("cardName")))) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Primary and add-on cards must use the same card tier."));
    CardRequest request = new CardRequest();
    request.setCustomerId(customerId);
    request.setCardName(tier);
    request.setCardType(type);
    request.setNote(input.note());
    request.setManagerUsername(owners.findByCustomerId(customerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Your account is not assigned to a manager.")).getManagerUsername());
    requests.save(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(request);
  }

  @PostMapping("/cards/{id}/block")
  public Object block(@RequestHeader("Authorization") String auth, @PathVariable Integer id) throws Exception {
    own(id, auth);
    return send("PATCH", "http://localhost:8082/patchCard/" + id, Map.of("status", "BLOCKED"), auth, "card service");
  }

  @PostMapping("/cards/{id}/renew")
  public Object renew(@RequestHeader("Authorization") String auth, @PathVariable Integer id, @RequestBody RenewInput input) throws Exception {
    Map<String, Object> card = own(id, auth);
    if (card.get("replacedByCreditId") != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This card has already been replaced.");
    Object expiryValue = card.get("expiryDate");
    if (expiryValue == null || !LocalDate.parse(String.valueOf(expiryValue).substring(0, 10)).isBefore(LocalDate.now(INDIA))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Card renewal is available only after the current card has expired.");
    if (input.expiryDate() == null || input.dueDate() == null || !input.dueDate().isBefore(input.expiryDate())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date must be before expiry date.");
    return send("POST", "http://localhost:8082/cards/" + id + "/renew", Map.of("expiryDate", input.expiryDate().toString(), "dueDate", input.dueDate().toString()), auth, "card service");
  }

  private Map<String, Object> own(Integer id, String auth) throws Exception {
    int customerId = customerId(auth);
    Map<String, Object> card = get("http://localhost:8082/card/" + id, auth);
    if (!String.valueOf(customerId).equals(String.valueOf(card.get("customerId")))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This card does not belong to the signed-in customer.");
    return card;
  }

  private List<Map<String, Object>> cards(int id, String auth) throws Exception { return list("http://localhost:8082/cards/customer/" + id, auth); }

  private List<Map<String, Object>> transactions(List<Map<String, Object>> cards, String auth) throws Exception {
    Set<String> numbers = new HashSet<>();
    for (Map<String, Object> card : cards) numbers.add(String.valueOf(card.get("cardNumber")));
    return list("http://localhost:8083/transactions", auth).stream().filter(item -> numbers.contains(String.valueOf(item.get("cardNumber")))).toList();
  }

  private List<Map<String, Object>> activeMerchants(String auth) throws Exception {
    return list("http://localhost:8084/merchants", auth);
  }

  private int customerId(String auth) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.substring(7)).getPayload();
      if (!"CUSTOMER".equals(claims.get("role", String.class))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer access is required.");
      return claims.get("customerId", Integer.class);
    } catch (ResponseStatusException error) { throw error; }
    catch (Exception error) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again."); }
  }

  private Map<String, Object> get(String url, String auth) throws Exception { return json.readValue(response(url, auth).body(), new TypeReference<Map<String, Object>>() {}); }
  private List<Map<String, Object>> list(String url, String auth) throws Exception { return json.readValue(response(url, auth).body(), new TypeReference<List<Map<String, Object>>>() {}); }

  private Object send(String method, String url, Map<String, Object> body, String auth, String serviceName) throws Exception {
    HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", auth).header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      String message = serviceName + " could not complete this request.";
      try {
        Map<String, Object> error = json.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        if (error.get("message") != null) message = String.valueOf(error.get("message"));
      } catch (Exception ignored) { }
      throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), message);
    }
    return response.body().isBlank() ? Map.of("message", "Request completed.") : json.readValue(response.body(), Object.class);
  }

  private HttpResponse<String> response(String url, String auth) throws Exception {
    HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", auth).GET().build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A required service is unavailable.");
    return response;
  }

  private Object merchantId(Map<String, Object> merchant) { return merchant.get("merchantId") != null ? merchant.get("merchantId") : merchant.get("id"); }
  private String merchantName(Map<String, Object> merchant) { String name = (String.valueOf(merchant.getOrDefault("firstName", "")) + " " + String.valueOf(merchant.getOrDefault("lastName", ""))).trim(); return name.isBlank() ? String.valueOf(merchant.getOrDefault("merchantName", "Merchant")) : name; }
  private double number(Object value) { return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value)); }
  private String normalized(String value) { return value == null ? "" : value.trim().toUpperCase().replace('-', '_').replace(' ', '_'); }
  private String tier(String value) { String tier = normalized(value); return Set.of("SILVER", "GOLD", "PLATINUM", "ULTRA_PREMIUM").contains(tier) ? tier : null; }
  private String type(String value) { String type = normalized(value); return Set.of("PRIMARY", "ADD_ON").contains(type) ? type : null; }

  public record CardRequestInput(String cardName, String cardType, String note) { }
  public record RenewInput(LocalDate expiryDate, LocalDate dueDate) { }
  public record CustomerTransactionInput(Integer cardId, Long merchantId, String transactionType, java.math.BigDecimal amount, String paymentMethod) { }
}

