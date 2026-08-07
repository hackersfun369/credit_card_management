package com.project;

import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

@RestController @CrossOrigin(origins={"http://localhost:4200","http://localhost:5173"}) @RequestMapping("/api/manager-portal")
public class ManagerPortalController {
 private final ObjectMapper json; private final ManagedCustomerRepository owners; private final CardRequestRepository requests; private final SecretKey key; private final HttpClient http=HttpClient.newHttpClient();
 public ManagerPortalController(ObjectMapper json,ManagedCustomerRepository owners,CardRequestRepository requests,@Value("${app.jwt.secret}")String secret){this.json=json;this.owners=owners;this.requests=requests;this.key=Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @GetMapping("/dashboard") public Map<String,Object> dashboard(@RequestHeader("Authorization")String auth)throws Exception{
  String manager=manager(auth); migrateLegacy(manager,auth); Set<String> ids=ownedIds(manager);
  List<Map<String,Object>> customers=list("http://localhost:8081/customer",auth).stream().filter(c->ids.contains(String.valueOf(customerId(c)))).toList();
  List<Map<String,Object>> cards=list("http://localhost:8082/card",auth).stream().filter(c->ids.contains(String.valueOf(c.get("customerId")))).toList();
  Set<String> numbers=new HashSet<>(); cards.forEach(c->numbers.add(String.valueOf(c.get("cardNumber"))));
  List<Map<String,Object>> transactions=list("http://localhost:8083/transactions",auth).stream().filter(t->numbers.contains(String.valueOf(t.get("cardNumber")))).toList();
  return new LinkedHashMap<>(Map.of("customers",customers,"cards",cards,"transactions",transactions,"cardRequests",requests.findByManagerUsernameOrderByIdDesc(manager),"merchants",list("http://localhost:8084/merchants",auth)));
 }
 @PostMapping("/customers") @Transactional public ResponseEntity<?> createCustomer(@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object> input)throws Exception{
  String manager=manager(auth); Object result=send("POST","http://localhost:8081/customer",input,auth); Map<String,Object> customer=json.convertValue(result,new TypeReference<>(){}); Integer id=Integer.valueOf(String.valueOf(customerId(customer)));
  ManagedCustomer owner=new ManagedCustomer();owner.setCustomerId(id);owner.setManagerUsername(manager);owners.save(owner);return ResponseEntity.status(HttpStatus.CREATED).body(customer);
 }
 @PatchMapping("/card-requests/{id}") @Transactional public CardRequest decide(@RequestHeader("Authorization")String auth,@PathVariable Long id,@RequestBody Decision input)throws Exception{
  String manager=manager(auth);CardRequest request=requests.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Card request not found."));
  if(!manager.equalsIgnoreCase(request.getManagerUsername()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This request belongs to another manager.");
  String status=normalize(input.status());if(!Set.of("APPROVED","REJECTED","ON_HOLD").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose APPROVED, REJECTED, or ON_HOLD.");
  if("APPROVED".equals(status)){
   if(input.expiryDate()==null||input.dueDate()==null||!input.dueDate().isBefore(input.expiryDate()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Approval requires a due date before the expiry date.");
   Map<String,Object> customer=get("http://localhost:8081/customer/"+request.getCustomerId(),auth);Map<String,Object> card=new LinkedHashMap<>();card.put("customerId",request.getCustomerId());card.put("cardHolderName",fullName(customer));card.put("cardName",request.getCardName());card.put("cardType",request.getCardType());card.put("expiryDate",input.expiryDate());card.put("dueDate",input.dueDate());send("POST","http://localhost:8082/card",card,auth);
  }
  request.setStatus(status);request.setNote(input.note()==null?request.getNote():input.note());return requests.save(request);
 }
 private void migrateLegacy(String manager,String auth)throws Exception{if(owners.count()==0){for(Map<String,Object> c:list("http://localhost:8081/customer",auth)){Object value=customerId(c);if(value!=null){ManagedCustomer o=new ManagedCustomer();o.setCustomerId(Integer.valueOf(String.valueOf(value)));o.setManagerUsername(manager);owners.save(o);}}}for(CardRequest request:requests.findAll()){if(request.getManagerUsername()==null||request.getManagerUsername().isBlank()){owners.findByCustomerId(request.getCustomerId()).ifPresent(owner->{request.setManagerUsername(owner.getManagerUsername());requests.save(request);});}}}
 private Set<String> ownedIds(String manager){Set<String> result=new HashSet<>();owners.findByManagerUsername(manager).forEach(o->result.add(String.valueOf(o.getCustomerId())));return result;}
 private Object customerId(Map<String,Object> c){return c.get("custId")!=null?c.get("custId"):c.get("customerId")!=null?c.get("customerId"):c.get("id");}
 private String fullName(Map<String,Object> c){return (String.valueOf(c.getOrDefault("firstName",c.getOrDefault("custFirstName","")))+" "+String.valueOf(c.getOrDefault("lastName",c.getOrDefault("custLastName","")))).trim();}
 private String normalize(String v){return v==null?"":v.trim().toUpperCase().replace('-','_').replace(' ','_');}
 private String manager(String auth){try{Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.substring(7)).getPayload();if(!"MANAGER".equals(c.get("role",String.class)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Manager access is required.");return c.getSubject();}catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Your session has expired. Please sign in again.");}}
 private Map<String,Object> get(String url,String auth)throws Exception{return json.readValue(response(url,auth).body(),new TypeReference<>(){});} private List<Map<String,Object>> list(String url,String auth)throws Exception{return json.readValue(response(url,auth).body(),new TypeReference<>(){});}
 private HttpResponse<String> response(String url,String auth)throws Exception{HttpResponse<String> r=http.send(java.net.http.HttpRequest.newBuilder(URI.create(url)).header("Authorization",auth).GET().build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()/100!=2)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"A required service is unavailable.");return r;}
 private Object send(String method,String url,Map<String,Object> body,String auth)throws Exception{HttpResponse<String> r=http.send(java.net.http.HttpRequest.newBuilder(URI.create(url)).header("Authorization",auth).header("Content-Type","application/json").method(method,java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()/100!=2){String message="The service could not complete this request.";try{Map<String,Object> e=json.readValue(r.body(),new TypeReference<>(){});if(e.get("message")!=null)message=String.valueOf(e.get("message"));}catch(Exception ignored){}throw new ResponseStatusException(HttpStatus.valueOf(r.statusCode()),message);}return r.body().isBlank()?Map.of("message","Completed"):json.readValue(r.body(),Object.class);}
 public record Decision(String status,LocalDate expiryDate,LocalDate dueDate,String note){}
}


