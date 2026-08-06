package com.project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@RestController
@RequestMapping("/api/assistant/actions")
@CrossOrigin(origins={"http://localhost:4200","http://localhost:5173"})
public class AssistantActionController {
  private final ObjectMapper mapper;
  private final SecretKey jwtKey;
  private final HttpClient client=HttpClient.newHttpClient();
  private static final Map<String,Target> TARGETS=targets();

  public AssistantActionController(ObjectMapper mapper,@Value("${app.jwt.secret}") String secret) {
    this.mapper=mapper;
    this.jwtKey=Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @PostMapping("/execute")
  public ActionResult execute(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody ActionRequest request) {
    username(auth);
    if(request==null||request.operation()==null||request.operation().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported operation.");
    if(!request.confirmed()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Manager confirmation is required before this action can run.");
    Target target=TARGETS.get(request.operation());
    if(target==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This assistant operation is not supported.");

    try {
      Map<String,Object> payload=new LinkedHashMap<>(request.payload()==null?Map.of():request.payload());
      if("activate_and_create_add_on".equals(request.operation())) return activateAndCreateAddOn(payload,auth);
      normalize(request.operation(),payload,auth);
      String path=target.path();
      if(target.needsId()) {
        Object reference=first(payload,"id","customerId","customerReference","accountNumber","aadharNumber","phoneNumber","creditId","cardNumber","merchantId","mid","merchantAccountNmber","transactionId","referenceNumber");
        payload.remove("id");
        String id=resolvePathId(request.operation(),reference,auth);
        path=path.replace("{id}",id);
      }
      HttpResponse<String> response=send(target.method(),target.base()+path,payload,auth);
      if(response.statusCode()<200||response.statusCode()>=300) {
        return new ActionResult(request.operation(),false,response.statusCode(),message(response.body(),response.statusCode()),response.body());
      }
      return new ActionResult(request.operation(),true,response.statusCode(),successMessage(request.operation()),response.body());
    } catch(ResponseStatusException exception) {
      throw exception;
    } catch(IllegalArgumentException exception) {
      return new ActionResult(request.operation(),false,400,exception.getMessage(),"");
    } catch(Exception exception) {
      return new ActionResult(request.operation(),false,502,"The target service could not be reached.","");
    }
  }

  private void normalize(String operation,Map<String,Object> payload,String auth) throws Exception {
    if("create_card".equals(operation)) normalizeCreateCard(payload,auth);
    if("block_card".equals(operation)) normalizeBlockCard(payload);
    if("activate_card".equals(operation)) normalizeActivateCard(payload,auth);
    if("create_transaction".equals(operation)) normalizeCreateTransaction(payload,auth);
  }

  private void normalizeCreateCard(Map<String,Object> payload,String auth) throws Exception {
    Object customerReference=first(payload,"customerId","customerReference","accountNumber","aadharNumber","phoneNumber");
    Map<String,Object> customer=findUnique(fetchList("http://localhost:8081/customer",auth),customerReference,
      List.of("custId","accountNumber","aadharNumber","phoneNumber"),"customer");
    payload.put("customerId",customer.get("custId"));
    payload.put("cardHolderName",fullName(customer));

    List<Map<String,Object>> cards=fetchList("http://localhost:8082/card",auth).stream()
      .filter(card->same(card.get("customerId"),customer.get("custId")))
      .filter(card->card.get("replacedByCreditId")==null).toList();

    String tier=cardTier(payload.get("cardName"));
    if(tier==null) tier=cardTier(payload.get("cardType"));
    if(!cards.isEmpty()) tier=String.valueOf(cards.get(0).get("cardName")).toUpperCase(Locale.ROOT);
    if(tier==null) throw new IllegalArgumentException("Card name must be SILVER, GOLD, PLATINUM, or ULTRA_PREMIUM.");

    String cardType=cardType(payload.get("cardType"));
    if(cardType==null) {
      boolean hasPrimary=cards.stream().anyMatch(card->"PRIMARY".equalsIgnoreCase(String.valueOf(card.get("cardType"))));
      boolean hasAddOn=cards.stream().anyMatch(card->"ADD_ON".equalsIgnoreCase(String.valueOf(card.get("cardType"))));
      if(!hasPrimary) cardType="PRIMARY";
      else if(!hasAddOn) cardType="ADD_ON";
      else throw new IllegalArgumentException("This customer already has both a primary and an add-on card.");
    }

    Object expiryValue=cards.isEmpty()?payload.get("expiryDate"):cards.get(0).get("expiryDate");
    Object dueValue=cards.isEmpty()?payload.get("dueDate"):cards.get(0).get("dueDate");
    LocalDate expiry=parseDate(expiryValue,"Expiry date");
    LocalDate due=parseDate(dueValue,"Due date");
    if(!due.isBefore(expiry)) throw new IllegalArgumentException("Due date must be before expiry date.");

    payload.clear();
    payload.put("customerId",customer.get("custId"));
    payload.put("cardHolderName",fullName(customer));
    payload.put("cardName",tier);
    payload.put("cardType",cardType);
    payload.put("expiryDate",expiry.toString());
    payload.put("dueDate",due.toString());
  }

  private void normalizeActivateCard(Map<String,Object> payload,String auth) throws Exception {
    Object reference=first(payload,"id","creditId","cardNumber","cardReference");
    Map<String,Object> card=findUnique(fetchList("http://localhost:8082/card",auth),reference,List.of("creditId","cardNumber"),"card");
    ensureActivatable(card);
    payload.clear();
    payload.put("id",card.get("creditId"));
    payload.put("status","ACTIVE");
  }

  private ActionResult activateAndCreateAddOn(Map<String,Object> payload,String auth) throws Exception {
    Object reference=first(payload,"cardReference","id","creditId","cardNumber");
    Map<String,Object> selected=findUnique(fetchList("http://localhost:8082/card",auth),reference,List.of("creditId","cardNumber"),"card");
    ensureActivatable(selected);
    Object customerId=selected.get("customerId");
    List<Map<String,Object>> accountCards=fetchList("http://localhost:8082/card",auth).stream()
      .filter(card->same(card.get("customerId"),customerId))
      .filter(card->card.get("replacedByCreditId")==null).toList();
    if(accountCards.stream().anyMatch(card->"ADD_ON".equalsIgnoreCase(String.valueOf(card.get("cardType"))))) {
      throw new IllegalArgumentException("This customer already has an add-on card.");
    }

    if(!"ACTIVE".equalsIgnoreCase(String.valueOf(selected.get("status")))) {
      Map<String,Object> activate=new LinkedHashMap<>();
      activate.put("status","ACTIVE");
      HttpResponse<String> activated=send("PATCH","http://localhost:8082/patchCard/"+selected.get("creditId"),activate,auth);
      if(activated.statusCode()<200||activated.statusCode()>=300) {
        return new ActionResult("activate_and_create_add_on",false,activated.statusCode(),message(activated.body(),activated.statusCode()),activated.body());
      }
    }

    Map<String,Object> addOn=new LinkedHashMap<>();
    addOn.put("customerId",customerId);
    addOn.put("cardHolderName",selected.get("cardHolderName"));
    addOn.put("cardName",selected.get("cardName"));
    addOn.put("cardType","ADD_ON");
    addOn.put("expiryDate",selected.get("expiryDate"));
    addOn.put("dueDate",selected.get("dueDate"));
    HttpResponse<String> created=send("POST","http://localhost:8082/card",addOn,auth);
    if(created.statusCode()<200||created.statusCode()>=300) {
      return new ActionResult("activate_and_create_add_on",false,created.statusCode(),message(created.body(),created.statusCode()),created.body());
    }
    return new ActionResult("activate_and_create_add_on",true,created.statusCode(),"Card activated and matching add-on card issued successfully.",created.body());
  }

  private void ensureActivatable(Map<String,Object> card) {
    if(card.get("replacedByCreditId")!=null) throw new IllegalArgumentException("A replaced card cannot be activated. Use its replacement card.");
    LocalDate expiry=parseDate(card.get("expiryDate"),"Card expiry date");
    if(expiry.isBefore(LocalDate.now())) throw new IllegalArgumentException("An expired card cannot be activated. Renew it instead.");
  }
  private void normalizeBlockCard(Map<String,Object> payload) {
    Object reference=first(payload,"id","creditId","cardNumber","cardReference");
    if(reference==null) throw new IllegalArgumentException("Provide a card number or card ID to block.");
    payload.clear();
    payload.put("id",reference);
    payload.put("status","BLOCKED");
  }
  private void normalizeCreateTransaction(Map<String,Object> payload,String auth) throws Exception {
    Object cardReference=first(payload,"cardNumber","creditId","cardId");
    Map<String,Object> card=findUnique(fetchList("http://localhost:8082/card",auth),cardReference,
      List.of("creditId","cardNumber"),"card");
    payload.put("cardNumber",card.get("cardNumber"));
    payload.put("cardHolderName",card.get("cardHolderName"));

    String transactionType=String.valueOf(payload.getOrDefault("transactionType","PURCHASE")).toUpperCase(Locale.ROOT);
    payload.put("transactionType",transactionType);
    payload.putIfAbsent("currency","INR");
    payload.putIfAbsent("status","PENDING");
    payload.putIfAbsent("paymentMethod","ONLINE");
    payload.putIfAbsent("timestamp",java.time.Instant.now().toString());
    payload.putIfAbsent("international",false);
    payload.putIfAbsent("fee",0);

    if("PAYMENT".equals(transactionType)) {
      payload.remove("merchantId");
      payload.put("merchantName","Card repayment");
      payload.put("paymentMethod","ONLINE");
      return;
    }

    Object merchantReference=first(payload,"merchantId","mid","merchantAccountNmber","merchantName");
    Map<String,Object> merchant=findUnique(fetchList("http://localhost:8084/merchants",auth),merchantReference,
      List.of("merchantId","mid","merchantAccountNmber","firstName"),"merchant");
    if(!"ACTIVE".equalsIgnoreCase(String.valueOf(merchant.get("status")))) throw new IllegalArgumentException("Only an active merchant can receive a transaction.");
    payload.put("merchantId",merchant.get("merchantId"));
    payload.put("merchantName",fullName(merchant));
  }

  private String resolvePathId(String operation,Object reference,String auth) throws Exception {
    if(reference==null) throw new IllegalArgumentException("A record reference is required.");
    if(operation.contains("customer")) return String.valueOf(findUnique(fetchList("http://localhost:8081/customer",auth),reference,
      List.of("custId","accountNumber","aadharNumber","phoneNumber"),"customer").get("custId"));
    if(operation.contains("card")) return String.valueOf(findUnique(fetchList("http://localhost:8082/card",auth),reference,
      List.of("creditId","cardNumber"),"card").get("creditId"));
    if(operation.contains("merchant")) return String.valueOf(findUnique(fetchList("http://localhost:8084/merchants",auth),reference,
      List.of("merchantId","mid","merchantAccountNmber"),"merchant").get("merchantId"));
    if(operation.contains("transaction")) return String.valueOf(findUnique(fetchList("http://localhost:8083/transactions",auth),reference,
      List.of("transactionId","referenceNumber"),"transaction").get("transactionId"));
    throw new IllegalArgumentException("The supplied record reference is unsupported.");
  }

  private Map<String,Object> findUnique(List<Map<String,Object>> records,Object reference,List<String> keys,String label) {
    String expected=plain(reference);
    if(expected.isBlank()||"null".equalsIgnoreCase(expected)) throw new IllegalArgumentException("A "+label+" reference is required.");
    List<Map<String,Object>> matches=new ArrayList<>();
    for(Map<String,Object> record:records) {
      boolean matched=false;
      for(String key:keys) {
        if(same(record.get(key),reference)) { matched=true;break; }
      }
      if(!matched&&("customer".equals(label)||"merchant".equals(label))) {
        matched=fullName(record).equalsIgnoreCase(expected);
      }
      if(matched) matches.add(record);
    }
    if(matches.isEmpty()) throw new IllegalArgumentException("No "+label+" matches reference "+expected+".");
    if(matches.size()>1) throw new IllegalArgumentException("More than one "+label+" matches "+expected+"; use its unique ID or account number.");
    return matches.get(0);
  }

  private List<Map<String,Object>> fetchList(String url,String auth) throws Exception {
    HttpRequest request=HttpRequest.newBuilder(URI.create(url)).header("Authorization",auth).GET().build();
    HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()<200||response.statusCode()>=300) throw new IllegalArgumentException("Reference lookup failed: "+message(response.body(),response.statusCode()));
    return mapper.readValue(response.body(),new TypeReference<List<Map<String,Object>>>(){});
  }

  private HttpResponse<String> send(String method,String url,Map<String,Object> payload,String auth) throws Exception {
    String body="GET".equals(method)?"":mapper.writeValueAsString(payload);
    HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(url)).header("Authorization",auth).header("Content-Type","application/json");
    HttpRequest request=switch(method) {
      case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
      case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
      case "PATCH" -> builder.method("PATCH",HttpRequest.BodyPublishers.ofString(body)).build();
      case "DELETE" -> builder.DELETE().build();
      default -> builder.GET().build();
    };
    return client.send(request,HttpResponse.BodyHandlers.ofString());
  }

  private LocalDate parseDate(Object value,String label) {
    try{return LocalDate.parse(String.valueOf(value));}
    catch(DateTimeParseException|NullPointerException exception){throw new IllegalArgumentException(label+" must be a real calendar date in YYYY-MM-DD format.");}
  }

  private String cardTier(Object value) {
    if(value==null)return null;
    String normalized=String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace(' ','_').replace("-","_");
    if(normalized.endsWith("_CARD"))normalized=normalized.substring(0,normalized.length()-5);
    return switch(normalized){case "SILVER","GOLD","PLATINUM","ULTRA_PREMIUM"->normalized;default->null;};
  }

  private String cardType(Object value) {
    if(value==null)return null;
    String normalized=String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace('-','_').replace(' ','_');
    if("ADDON".equals(normalized))normalized="ADD_ON";
    return "PRIMARY".equals(normalized)||"ADD_ON".equals(normalized)?normalized:null;
  }

  private Object first(Map<String,Object> payload,String...keys) {
    for(String key:keys)if(payload.containsKey(key)&&payload.get(key)!=null&&!plain(payload.get(key)).isBlank())return payload.get(key);
    return null;
  }

  private boolean same(Object left,Object right){
    String first=plain(left), second=plain(right);
    if(first.equals(second))return true;
    return first.matches("[0-9\\s-]+")&&second.matches("[0-9\\s-]+")&&first.replaceAll("[\\s-]","").equals(second.replaceAll("[\\s-]",""));
  }
  private String plain(Object value){
    if(value==null)return "";
    String text=String.valueOf(value).trim();
    return text.endsWith(".0")?text.substring(0,text.length()-2):text;
  }
  private String fullName(Map<String,Object> record){
    return (plain(record.get("firstName"))+" "+plain(record.get("lastName"))).trim();
  }

  private String message(String body,int status) {
    if(body!=null&&!body.isBlank()) {
      try {
        Map<String,Object> parsed=mapper.readValue(body,new TypeReference<Map<String,Object>>(){});
        Object value=parsed.get("message");
        if(value!=null&&!plain(value).isBlank())return plain(value);
        value=parsed.get("error");
        if(value!=null&&!plain(value).isBlank())return plain(value);
      } catch(Exception ignored) {
        if(body.length()<300)return body;
      }
    }
    return "Request failed ("+status+").";
  }

  private String username(String auth) {
    if(auth==null||!auth.startsWith("Bearer ")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"A valid session token is required.");
    try{return Jwts.parser().verifyWith(jwtKey).build().parseSignedClaims(auth.substring(7)).getPayload().getSubject();}
    catch(Exception exception){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Your session has expired. Please sign in again.");}
  }

  private String successMessage(String operation) {
    return switch(operation) {
      case "create_customer" -> "Customer created successfully.";
      case "update_customer", "patch_customer" -> "Customer details updated successfully.";
      case "delete_customer" -> "Customer deleted successfully.";
      case "create_card" -> "Credit card issued successfully.";
      case "update_card", "patch_card" -> "Card details updated successfully.";
      case "block_card" -> "Card blocked successfully. Transactions are disabled.";
      case "activate_card" -> "Card activated successfully.";
      case "activate_and_create_add_on" -> "Card activated and matching add-on card issued successfully.";
      case "renew_card" -> "Card renewed successfully.";
      case "delete_card" -> "Credit card deleted successfully.";
      case "create_merchant" -> "Merchant created successfully.";
      case "update_merchant", "patch_merchant" -> "Merchant details updated successfully.";
      case "delete_merchant" -> "Merchant deleted successfully.";
      case "create_transaction" -> "Transaction recorded successfully.";
      case "update_transaction_status" -> "Transaction status updated successfully.";
      case "delete_transaction" -> "Transaction deleted successfully.";
      default -> "Operation completed successfully.";
    };
  }
  private static Map<String,Target> targets() {
    Map<String,Target> map=new LinkedHashMap<>();
    add(map,"create_customer","POST","http://localhost:8081","/customer",false);
    add(map,"update_customer","PUT","http://localhost:8081","/putCustomer/{id}",true);
    add(map,"patch_customer","PATCH","http://localhost:8081","/patchCustomer/{id}",true);
    add(map,"delete_customer","DELETE","http://localhost:8081","/customer/{id}",true);
    add(map,"create_card","POST","http://localhost:8082","/card",false);
    add(map,"update_card","PUT","http://localhost:8082","/putCard/{id}",true);
    add(map,"patch_card","PATCH","http://localhost:8082","/patchCard/{id}",true);
    add(map,"block_card","PATCH","http://localhost:8082","/patchCard/{id}",true);
    add(map,"activate_card","PATCH","http://localhost:8082","/patchCard/{id}",true);
    add(map,"activate_and_create_add_on","POST","http://localhost:8082","/card",false);
    add(map,"delete_card","DELETE","http://localhost:8082","/card/{id}",true);
    add(map,"renew_card","POST","http://localhost:8082","/cards/{id}/renew",true);
    add(map,"create_merchant","POST","http://localhost:8084","/merchant",false);
    add(map,"update_merchant","PUT","http://localhost:8084","/merchant/{id}",true);
    add(map,"patch_merchant","PATCH","http://localhost:8084","/merchant/{id}",true);
    add(map,"delete_merchant","DELETE","http://localhost:8084","/merchant/{id}",true);
    add(map,"create_transaction","POST","http://localhost:8083","/transaction",false);
    add(map,"update_transaction_status","PUT","http://localhost:8083","/transaction/{id}/status",true);
    add(map,"delete_transaction","DELETE","http://localhost:8083","/transaction/{id}",true);
    return Map.copyOf(map);
  }

  private static void add(Map<String,Target> map,String name,String method,String base,String path,boolean id){map.put(name,new Target(method,base,path,id));}
  private record Target(String method,String base,String path,boolean needsId){}
  public record ActionRequest(String operation,Map<String,Object> payload,boolean confirmed){}
  public record ActionResult(String operation,boolean success,int status,String message,String data){}
}
