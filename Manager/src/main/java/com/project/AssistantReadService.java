package com.project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AssistantReadService {
  private static final Pattern NON_ALPHANUMERIC=Pattern.compile("[^0-9A-Za-z]");
  private final ObjectMapper mapper;
  private final HttpClient client=HttpClient.newHttpClient();

  public AssistantReadService(ObjectMapper mapper){this.mapper=mapper;}

  public Optional<String> tryDirect(String question,String auth){
    String text=question==null?"":question.toLowerCase(Locale.ROOT);
    if(isClearlyOutOfScope(text)) return Optional.of("I can only assist with Credence credit-card-management operations and data: customers, cards, merchants, transactions, repayments, renewals, due dates, alerts, and portfolio analytics.");
    try{
      if(text.contains("customer")&&text.contains("name")&&(text.contains("list")||text.contains("show")||text.contains("all"))&&!text.contains("account")&&!text.contains("phone")&&!text.contains("location")&&!text.contains("id")) return Optional.of(customerNames(auth));
      if(text.contains("customer")&&text.contains("outstanding")&&(text.contains("highest")||text.contains("largest")||text.contains("maximum")||text.contains("most")))
        return Optional.of(outstandingRanking(Map.of("order","DESC","limit",1),auth));
      if(text.contains("customer")&&text.contains("outstanding")&&(text.contains("lowest")||text.contains("smallest")||text.contains("minimum")||text.contains("least")))
        return Optional.of(outstandingRanking(Map.of("order","ASC","limit",1),auth));
      if((text.contains("portfolio")||text.contains("dashboard"))&&(text.contains("summary")||text.contains("overview")))
        return Optional.of(portfolioSummary(auth));
      return Optional.empty();
    }catch(Exception exception){return Optional.of("I could not read the live services: "+cleanMessage(exception)+".");}
  }

  public String execute(String operation,Map<String,Object> payload,String auth){
    try{
      Map<String,Object> input=payload==null?Map.of():payload;
      return switch(operation){
        case "highest_transaction" -> highestTransaction(input,auth);
        case "aggregate_records" -> aggregateRecords(input,auth);
        case "customer_outstanding_ranking" -> outstandingRanking(input,auth);
        case "portfolio_summary" -> portfolioSummary(auth);
        case "list_customers" -> customerList(input,auth);
        case "list_cards" -> cardList(input,auth);
        case "list_merchants" -> merchantList(input,auth);
        case "list_transactions" -> transactionList(input,auth);
        case "get_customer" -> customerDetails(input,auth);
        case "get_card" -> cardDetails(input,auth);
        case "get_merchant" -> merchantDetails(input,auth);
        case "get_transaction" -> transactionDetails(input,auth);
        default -> "That read operation is not supported.";
      };
    }catch(IllegalArgumentException exception){return exception.getMessage();}
    catch(Exception exception){return "I could not read the live services: "+cleanMessage(exception)+".";}
  }

  private String aggregateRecords(Map<String,Object> payload,String auth)throws Exception{
    String entity=string(payload.get("entity")).toLowerCase(Locale.ROOT),metric=string(payload.get("metric")).toUpperCase(Locale.ROOT),field=string(payload.get("field")),groupBy=string(payload.get("groupBy"));
    String url;List<String> searchable;
    switch(entity){
      case "customer","customers"->{entity="customer";url="http://localhost:8081/customer";searchable=List.of("custId","accountNumber","phoneNumber","firstName","lastName","location");}
      case "card","cards"->{entity="card";url="http://localhost:8082/card";searchable=List.of("creditId","cardNumber","customerId","cardHolderName","cardName","cardType","status");}
      case "merchant","merchants"->{entity="merchant";url="http://localhost:8084/merchants";searchable=List.of("merchantId","merchantAccountNmber","firstName","lastName","merchantCategory","status");}
      case "transaction","transactions"->{entity="transaction";url="http://localhost:8083/transactions";searchable=List.of("transactionId","cardNumber","cardHolderName","merchantName","transactionType","paymentMethod","status","amount");}
      default->throw new IllegalArgumentException("Choose customers, cards, merchants, or transactions for this calculation.");
    }
    if(!Set.of("COUNT","SUM","AVG","MAX","MIN").contains(metric))throw new IllegalArgumentException("Choose COUNT, SUM, AVG, MAX, or MIN.");
    if(!"COUNT".equals(metric)&&field.isBlank())throw new IllegalArgumentException("Choose the field to calculate.");
    final String targetEntity=entity;
    List<Map<String,Object>> rows=filter(fetch(url,auth),payload,searchable);
    if(groupBy.isBlank())return aggregateLabel(metric,field)+": **"+aggregateDisplay(metric,field,aggregateValue(rows,targetEntity,metric,field))+"**";
    Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<>();for(Map<String,Object> row:rows){String key=string(fieldValue(targetEntity,row,groupBy));groups.computeIfAbsent(key,ignored->new ArrayList<>()).add(row);}
    List<Map.Entry<String,Double>> values=groups.entrySet().stream().map(entry->Map.entry(entry.getKey(),aggregateValue(entry.getValue(),targetEntity,metric,field))).sorted(Map.Entry.comparingByValue()).toList();
    if("DESC".equalsIgnoreCase(string(payload.get("order")))){List<Map.Entry<String,Double>> descending=new ArrayList<>(values);Collections.reverse(descending);values=descending;}
    values=values.stream().limit(boundedInt(payload.get("limit"),10,1,50)).toList();if(values.isEmpty())return "No records match that request.";
    if(values.size()==1)return "**"+label(groupBy)+":** "+values.get(0).getKey()+"\n**"+aggregateLabel(metric,field)+":** "+aggregateDisplay(metric,field,values.get(0).getValue());
    StringBuilder out=new StringBuilder("|").append(label(groupBy)).append('|').append(aggregateLabel(metric,field)).append("|\n|---|---:|\n");for(Map.Entry<String,Double> value:values)out.append('|').append(value.getKey()).append('|').append(aggregateDisplay(metric,field,value.getValue())).append("|\n");return out.toString().trim();
  }
  private double aggregateValue(List<Map<String,Object>> rows,String entity,String metric,String field){if("COUNT".equals(metric))return rows.size();java.util.stream.DoubleStream values=rows.stream().mapToDouble(row->number(fieldValue(entity,row,field)));return switch(metric){case "SUM"->values.sum();case "AVG"->values.average().orElse(0d);case "MAX"->values.max().orElse(0d);case "MIN"->values.min().orElse(0d);default->0d;};}
  private String aggregateLabel(String metric,String field){return switch(metric){case "COUNT"->"Count";case "SUM"->"Total "+label(field);case "AVG"->"Average "+label(field);case "MAX"->"Highest "+label(field);case "MIN"->"Lowest "+label(field);default->label(field);};}
  private String aggregateDisplay(String metric,String field,double value){if("COUNT".equals(metric))return String.valueOf((long)value);String key=plain(field).toLowerCase(Locale.ROOT);return key.contains("amount")||key.contains("credit")||key.contains("limit")||key.contains("fee")?money(value):String.valueOf(value);}
  private String highestTransaction(Map<String,Object> payload,String auth)throws Exception{
    boolean includeRepayments=Boolean.parseBoolean(String.valueOf(payload.getOrDefault("includeRepayments",false)));
    List<Map<String,Object>> completed=fetch("http://localhost:8083/transactions",auth).stream()
      .filter(row->"COMPLETED".equalsIgnoreCase(string(first(row,"status","transactionStatus"))))
      .filter(row->includeRepayments||!"PAYMENT".equalsIgnoreCase(string(first(row,"transactionType","type")))).toList();
    Map<String,Object> transaction=completed.stream().max(Comparator.comparingDouble(row->number(first(row,"amount")))).orElseThrow(()->new IllegalArgumentException("No completed transactions are available."));
    String payer=string(first(transaction,"cardHolderName"));if(payer.isBlank())payer="Unknown cardholder";
    String recipient="PAYMENT".equalsIgnoreCase(string(first(transaction,"transactionType","type")))?"Credence card repayment":string(first(transaction,"merchantName"));if(recipient.isBlank())recipient="Unknown merchant";
    return "**"+money(number(first(transaction,"amount")))+"** — **"+payer+"** paid **"+recipient+"** on **"+indiaTime(first(transaction,"timestamp"))+"**.";
  }

  private String indiaTime(Object value){
    String timestamp=string(value);if(timestamp.isBlank())return "Unknown time";
    DateTimeFormatter output=DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a",Locale.ENGLISH);
    try{return Instant.parse(timestamp).atZone(ZoneId.of("Asia/Kolkata")).format(output).toLowerCase(Locale.ENGLISH);}
    catch(Exception ignored){try{return OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.of("Asia/Kolkata")).format(output).toLowerCase(Locale.ENGLISH);}catch(Exception ignoredAgain){try{return LocalDateTime.parse(timestamp).format(output).toLowerCase(Locale.ENGLISH);}catch(Exception finalIgnored){return timestamp;}}}
  }
  private String customerNames(String auth)throws Exception{List<String> names=fetch("http://localhost:8081/customer",auth).stream().map(this::name).filter(value->!value.isBlank()).toList();return names.isEmpty()?"No customers are available.":String.join("\n",names);}

  private String outstandingRanking(Map<String,Object> payload,String auth)throws Exception{
    List<Map<String,Object>> customers=fetch("http://localhost:8081/customer",auth);
    List<Map<String,Object>> cards=fetch("http://localhost:8082/card",auth);
    List<Map<String,Object>> rows=new ArrayList<>();
    for(Map<String,Object> customer:customers){
      Object id=first(customer,"custId","customerId","id");
      List<Map<String,Object>> current=cards.stream().filter(card->same(first(card,"customerId","custId"),id)).filter(card->first(card,"replacedByCreditId")==null).toList();
      double limit=current.stream().mapToDouble(card->number(first(card,"cardLimit","creditLimit"))).max().orElse(0d);
      double available=current.stream().mapToDouble(card->number(first(card,"availableCredit","availableBalance"))).min().orElse(limit);
      Map<String,Object> row=new LinkedHashMap<>();row.put("name",name(customer));row.put("customerId",id);row.put("accountNumber",first(customer,"accountNumber"));row.put("outstanding",Math.max(0d,limit-available));rows.add(row);
    }
    boolean ascending="ASC".equalsIgnoreCase(string(payload.get("order")));
    rows.sort((a,b)->Double.compare(number(b.get("outstanding")),number(a.get("outstanding")))*(ascending?-1:1));
    int limit=boundedInt(payload.get("limit"),10,1,50);rows=rows.stream().limit(limit).toList();
    if(rows.isEmpty())return "No customers are available.";
    if(limit==1){Map<String,Object> top=rows.get(0);return "**"+top.get("name")+"** has the "+(ascending?"lowest":"highest")+" outstanding amount: **"+money(number(top.get("outstanding")))+"** (Customer ID "+top.get("customerId")+").";}
    StringBuilder result=new StringBuilder("| Customer | Customer ID | Account number | Outstanding |\n|---|---:|---:|---:|\n");
    for(Map<String,Object> row:rows)result.append('|').append(row.get("name")).append('|').append(row.get("customerId")).append('|').append(row.get("accountNumber")).append('|').append(money(number(row.get("outstanding")))).append("|\n");
    return result.toString().trim();
  }

  private String portfolioSummary(String auth)throws Exception{
    List<Map<String,Object>> customers=fetch("http://localhost:8081/customer",auth),cards=fetch("http://localhost:8082/card",auth),merchants=fetch("http://localhost:8084/merchants",auth),transactions=fetch("http://localhost:8083/transactions",auth);
    long active=cards.stream().filter(card->"ACTIVE".equalsIgnoreCase(string(first(card,"status","cardStatus")))).count();
    double purchases=transactions.stream().filter(tx->"PURCHASE".equalsIgnoreCase(string(first(tx,"transactionType","type")))).filter(tx->"COMPLETED".equalsIgnoreCase(string(first(tx,"status","transactionStatus")))).mapToDouble(tx->number(first(tx,"amount"))).sum();
    return "**Live portfolio summary**\n\n- Customers: **"+customers.size()+"**\n- Cards: **"+cards.size()+"** (active: **"+active+"**)\n- Merchants: **"+merchants.size()+"**\n- Transactions: **"+transactions.size()+"**\n- Completed purchases: **"+money(purchases)+"**";
  }

  private String customerList(Map<String,Object> payload,String auth)throws Exception{
    List<Map<String,Object>> rows=sortRows("customer",filter(fetch("http://localhost:8081/customer",auth),payload,List.of("custId","accountNumber","aadharNumber","phoneNumber","firstName","lastName","location")),payload);
    String selected=projection("customer",rows,payload);if(selected!=null)return selected;
    StringBuilder out=new StringBuilder("| Customer | ID | Account number | Phone | Location |\n|---|---:|---:|---|---|\n");
    for(Map<String,Object> row:limit(rows,payload))out.append('|').append(name(row)).append('|').append(first(row,"custId","id")).append('|').append(first(row,"accountNumber")).append('|').append(first(row,"phoneNumber")).append('|').append(first(row,"location")).append("|\n");
    return tableOrEmpty(out,rows,"No customers match that request.");
  }

  private String cardList(Map<String,Object> payload,String auth)throws Exception{
    List<Map<String,Object>> rows=sortRows("card",filter(fetch("http://localhost:8082/card",auth),payload,List.of("creditId","cardNumber","customerId","cardHolderName","cardName","cardType","status","expiryDate","dueDate")),payload);
    String selected=projection("card",rows,payload);if(selected!=null)return selected;
    StringBuilder out=new StringBuilder("| Cardholder | Card | Tier/type | Status | Available | Due | Expiry |\n|---|---|---|---|---:|---|---|\n");
    for(Map<String,Object> row:limit(rows,payload))out.append('|').append(first(row,"cardHolderName")).append('|').append(mask(first(row,"cardNumber"))).append('|').append(first(row,"cardName")).append(" / ").append(first(row,"cardType")).append('|').append(first(row,"status","cardStatus")).append('|').append(money(number(first(row,"availableCredit","availableBalance")))).append('|').append(first(row,"dueDate")).append('|').append(first(row,"expiryDate")).append("|\n");
    return tableOrEmpty(out,rows,"No cards match that request.");
  }

  private String merchantList(Map<String,Object> payload,String auth)throws Exception{
    List<Map<String,Object>> rows=sortRows("merchant",filter(fetch("http://localhost:8084/merchants",auth),payload,List.of("merchantId","mid","merchantAccountNmber","firstName","lastName","merchantCategory","status","bankName")),payload);
    String selected=projection("merchant",rows,payload);if(selected!=null)return selected;
    StringBuilder out=new StringBuilder("| Merchant | ID | Account number | Category | Status | Bank |\n|---|---:|---:|---|---|---|\n");
    for(Map<String,Object> row:limit(rows,payload))out.append('|').append(name(row)).append('|').append(first(row,"merchantId","id")).append('|').append(first(row,"merchantAccountNmber","accountNumber")).append('|').append(first(row,"merchantCategory","category")).append('|').append(first(row,"status")).append('|').append(first(row,"bankName")).append("|\n");
    return tableOrEmpty(out,rows,"No merchants match that request.");
  }

  private String transactionList(Map<String,Object> payload,String auth)throws Exception{
    List<Map<String,Object>> rows=sortRows("transaction",filter(fetch("http://localhost:8083/transactions",auth),payload,List.of("transactionId","referenceNumber","cardNumber","cardHolderName","merchantName","transactionType","paymentMethod","status","timestamp","amount")),payload);
    String selected=projection("transaction",rows,payload);if(selected!=null)return selected;
    StringBuilder out=new StringBuilder("| Transaction | Card | Merchant | Type/method | Amount | Status | Time |\n|---:|---|---|---|---:|---|---|\n");
    for(Map<String,Object> row:limit(rows,payload))out.append('|').append(first(row,"transactionId","referenceNumber")).append('|').append(mask(first(row,"cardNumber"))).append('|').append(first(row,"merchantName")).append('|').append(first(row,"transactionType","type")).append(" / ").append(first(row,"paymentMethod")).append('|').append(money(number(first(row,"amount")))).append('|').append(first(row,"status")).append('|').append(first(row,"timestamp")).append("|\n");
    return tableOrEmpty(out,rows,"No transactions match that request.");
  }

  private String customerDetails(Map<String,Object> payload,String auth)throws Exception{return detail("customer",find(fetch("http://localhost:8081/customer",auth),reference(payload),List.of("custId","accountNumber","aadharNumber","phoneNumber","firstName","lastName")),Set.of("aadharNumber"),payload);}
  private String cardDetails(Map<String,Object> payload,String auth)throws Exception{return detail("card",find(fetch("http://localhost:8082/card",auth),reference(payload),List.of("creditId","cardNumber")),Set.of(),payload);}
  private String merchantDetails(Map<String,Object> payload,String auth)throws Exception{return detail("merchant",find(fetch("http://localhost:8084/merchants",auth),reference(payload),List.of("merchantId","mid","merchantAccountNmber","firstName","lastName")),Set.of(),payload);}
  private String transactionDetails(Map<String,Object> payload,String auth)throws Exception{return detail("transaction",find(fetch("http://localhost:8083/transactions",auth),reference(payload),List.of("transactionId","referenceNumber")),Set.of(),payload);}

  private String detail(String entity,Map<String,Object> record,Set<String> hidden,Map<String,Object> payload){String projected=projection(entity,List.of(record),payload);if(projected!=null)return projected;StringBuilder out=new StringBuilder("**Live "+entity+" details**\n\n");record.forEach((key,value)->{if(!hidden.contains(key))out.append("- ").append(label(key)).append(": **").append(value).append("**\n");});return out.toString().trim();}
  private String projection(String entity,List<Map<String,Object>> rows,Map<String,Object> payload){
    List<String> fields=requestedFields(payload);if(fields.isEmpty())return null;
    List<Map<String,Object>> selected=limit(rows,payload);if(selected.isEmpty())return "No records match that request.";
    if(fields.size()==1){String field=fields.get(0);return selected.stream().map(row->display(fieldValue(entity,row,field),field)).collect(java.util.stream.Collectors.joining("\n"));}
    if("FACTS".equalsIgnoreCase(string(payload.get("format")))&&selected.size()==1){Map<String,Object> row=selected.get(0);StringBuilder facts=new StringBuilder();for(String field:fields)facts.append("**").append(label(field)).append(":** ").append(display(fieldValue(entity,row,field),field)).append("\n");return facts.toString().trim();}
    StringBuilder out=new StringBuilder("|");for(String field:fields)out.append(label(field)).append('|');out.append("\n|");for(int index=0;index<fields.size();index++)out.append("---|");out.append('\n');
    for(Map<String,Object> row:selected){out.append('|');for(String field:fields)out.append(display(fieldValue(entity,row,field),field)).append('|');out.append('\n');}return out.toString().trim();
  }
  private List<String> requestedFields(Map<String,Object> payload){Object value=payload.get("fields");if(value instanceof Collection<?> collection)return collection.stream().map(String::valueOf).map(String::trim).filter(item->!item.isBlank()).distinct().toList();if(value instanceof String text&&!text.isBlank())return Arrays.stream(text.split(",")).map(String::trim).filter(item->!item.isBlank()).distinct().toList();return List.of();}
  private Object fieldValue(String entity,Map<String,Object> row,String requested){String field=plain(requested).toLowerCase(Locale.ROOT);return switch(field){case "name","customername","merchantname"->name(row);case "id"->first(row,entity+"Id","custId","creditId","transactionId","id");case "accountnumber"->first(row,"accountNumber","merchantAccountNmber");case "cardnumber"->first(row,"cardNumber");case "phone","phonenumber","mobile"->first(row,"phoneNumber");case "category"->first(row,"merchantCategory","category");case "type"->first(row,"transactionType","cardType","type");case "status"->first(row,"status","cardStatus","transactionStatus");case "date","datetime","timestamp"->first(row,"timestamp","createdDate","createDate");case "available","availablecredit"->first(row,"availableCredit","availableBalance");case "limit","creditlimit","cardlimit"->first(row,"cardLimit","creditLimit");default->row.entrySet().stream().filter(entry->plain(entry.getKey()).equalsIgnoreCase(plain(requested))).map(Map.Entry::getValue).findFirst().orElse("");};}
  private String display(Object value,String field){if(value==null)return "";String key=plain(field).toLowerCase(Locale.ROOT);if(key.contains("amount")||key.contains("credit")||key.equals("limit")||key.equals("available"))return money(number(value));if(key.equals("timestamp")||key.equals("datetime")||key.equals("when"))return indiaTime(value);return String.valueOf(value);}
  private List<Map<String,Object>> fetch(String url,String auth)throws Exception{HttpRequest request=HttpRequest.newBuilder(URI.create(url)).header("Authorization",auth).GET().build();HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalArgumentException("The "+service(url)+" service returned HTTP "+response.statusCode());JsonNode root=mapper.readTree(response.body());JsonNode rows=root.isArray()?root:root.path("content");if(!rows.isArray())rows=root.path("data");if(!rows.isArray())throw new IllegalArgumentException("The "+service(url)+" service returned an unsupported response");List<Map<String,Object>> result=new ArrayList<>();for(JsonNode row:rows)result.add(mapper.convertValue(row,new TypeReference<Map<String,Object>>(){}));return result;}
  private Map<String,Object> find(List<Map<String,Object>> rows,Object reference,List<String> keys){String wanted=plain(reference);if(wanted.isBlank())throw new IllegalArgumentException("Provide a unique record reference.");List<Map<String,Object>> matches=rows.stream().filter(row->keys.stream().anyMatch(key->same(row.get(key),reference))||name(row).equalsIgnoreCase(wanted)).toList();if(matches.isEmpty())throw new IllegalArgumentException("No matching record was found.");if(matches.size()>1)throw new IllegalArgumentException("More than one record matches; use a unique ID or account number.");return matches.get(0);}
  private List<Map<String,Object>> filter(List<Map<String,Object>> rows,Map<String,Object> payload,List<String> searchable){Object query=first(payload,"query","reference","search");String status=string(first(payload,"status")),type=string(first(payload,"type","cardType","transactionType")),method=string(first(payload,"paymentMethod"));return rows.stream().filter(row->query==null||searchable.stream().anyMatch(key->contains(row.get(key),query))).filter(row->status.isBlank()||status.equalsIgnoreCase(string(first(row,"status","cardStatus")))).filter(row->type.isBlank()||type.equalsIgnoreCase(string(first(row,"cardType","transactionType","type")))).filter(row->method.isBlank()||method.equalsIgnoreCase(string(first(row,"paymentMethod")))).toList();}
  private List<Map<String,Object>> sortRows(String entity,List<Map<String,Object>> rows,Map<String,Object> payload){
    String sortBy=string(payload.get("sortBy"));if(sortBy.isBlank())return rows;
    boolean descending="DESC".equalsIgnoreCase(string(payload.get("order")));
    Comparator<Map<String,Object>> comparator=(left,right)->compareValues(fieldValue(entity,left,sortBy),fieldValue(entity,right,sortBy));
    if(descending)comparator=comparator.reversed();return rows.stream().sorted(comparator).toList();
  }
  private int compareValues(Object left,Object right){
    if(left==null&&right==null)return 0;if(left==null)return -1;if(right==null)return 1;
    String a=string(left),b=string(right);try{return Double.compare(Double.parseDouble(a),Double.parseDouble(b));}catch(Exception ignored){return a.compareToIgnoreCase(b);}
  }
  private List<Map<String,Object>> limit(List<Map<String,Object>> rows,Map<String,Object> payload){return rows.stream().limit(boundedInt(payload.get("limit"),10,1,50)).toList();}
  private String tableOrEmpty(StringBuilder table,List<Map<String,Object>> rows,String empty){return rows.isEmpty()?empty:table.toString().trim();}
  private Object reference(Map<String,Object> payload){return first(payload,"reference","id","customerReference","cardReference","merchantReference","transactionReference","accountNumber","cardNumber");}
  private Object first(Map<String,Object> source,String...keys){for(String key:keys)if(source.containsKey(key)&&source.get(key)!=null)return source.get(key);return null;}
  private boolean same(Object left,Object right){return left!=null&&right!=null&&plain(left).equalsIgnoreCase(plain(right));}
  private boolean contains(Object left,Object right){return left!=null&&plain(left).toLowerCase(Locale.ROOT).contains(plain(right).toLowerCase(Locale.ROOT));}
  private String plain(Object value){return value==null?"":NON_ALPHANUMERIC.matcher(String.valueOf(value)).replaceAll("");}
  private String string(Object value){return value==null?"":String.valueOf(value).trim();}
  private double number(Object value){try{return value==null?0d:Double.parseDouble(String.valueOf(value));}catch(Exception exception){return 0d;}}
  private int boundedInt(Object value,int fallback,int min,int max){try{return Math.max(min,Math.min(max,Integer.parseInt(String.valueOf(value))));}catch(Exception exception){return fallback;}}
  private String name(Map<String,Object> row){String first=string(first(row,"firstName","custFirstName","cardHolderName")),last=string(first(row,"lastName","custLastName"));return (first+" "+last).trim();}
  private String mask(Object value){String digits=String.valueOf(value==null?"":value).replaceAll("\\D","");return digits.length()<4?digits:"**** "+digits.substring(digits.length()-4);}
  private String money(double value){NumberFormat format=NumberFormat.getCurrencyInstance(new Locale("en","IN"));format.setMaximumFractionDigits(value%1==0?0:2);return format.format(value);}
  private String label(String key){return key.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ');}
  private String service(String url){if(url.contains("8081"))return "customer";if(url.contains("8082"))return "card";if(url.contains("8083"))return "transaction";return "merchant";}
  private boolean isClearlyOutOfScope(String text){
    if(text.isBlank())return false;
    boolean asksForCode=text.matches("(?s).*(write|create|generate|show|give|provide|build|make|implement|explain|debug|fix).*(python|java|javascript|typescript|c\\+\\+|c#|html|css|sql|program|code|script|algorithm|function).*")
      || text.matches("(?s).*(python|java|javascript|typescript|c\\+\\+|c#).*(code|program|script|function).*");
    boolean unrelatedTask=text.matches("(?s).*(write|compose|generate|tell me).*(poem|story|essay|song|joke|recipe).*")
      || text.matches("(?s).*(weather|sports score|stock price|capital of|translate this).*");
    return asksForCode||unrelatedTask;
  }
  private String cleanMessage(Exception exception){String message=exception.getMessage();return message==null||message.isBlank()?"service unavailable":message.replaceAll("[\\r\\n]+"," ");}
}