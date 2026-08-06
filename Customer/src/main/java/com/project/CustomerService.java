package com.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;

import com.project.DTO.PeriodCountDTO;

@Service
public class CustomerService {

	private final CustomerRepository cr;
	private final RestTemplate rt;
	

	    public CustomerService(CustomerRepository cr, @Qualifier("restTemplate") RestTemplate rt) {
	        this.rt = rt;
	        this.cr = cr;
	    }
	
	 private void validateCustomer(Customer cust) {
        if (cust.getPhoneNumber() == null || cust.getPhoneNumber() < 1_000_000_000L || cust.getPhoneNumber() > 9_999_999_999L) throw new IllegalArgumentException("Phone number must be exactly 10 digits");
        if (cust.getAadharNumber() == null || cust.getAadharNumber() < 100_000_000_000L || cust.getAadharNumber() > 999_999_999_999L) throw new IllegalArgumentException("Aadhaar number must be exactly 12 digits");
        if (cust.getAccountNumber() == null || cust.getAccountNumber() < 100_000_000_000L || cust.getAccountNumber() > 999_999_999_999L) throw new IllegalArgumentException("Account number must be exactly 12 digits");
    }
    private void ensureUnique(Customer customer, Integer ignoredId) {
        boolean duplicateAadhaar = cr.findAll().stream().anyMatch(item -> !item.getCustId().equals(ignoredId) && java.util.Objects.equals(item.getAadharNumber(), customer.getAadharNumber()));
        boolean duplicateAccount = cr.findAll().stream().anyMatch(item -> !item.getCustId().equals(ignoredId) && java.util.Objects.equals(item.getAccountNumber(), customer.getAccountNumber()));
        boolean duplicatePhone = cr.findAll().stream().anyMatch(item -> !item.getCustId().equals(ignoredId) && java.util.Objects.equals(item.getPhoneNumber(), customer.getPhoneNumber()));
        if (duplicateAadhaar) throw new IllegalArgumentException("A customer with this Aadhaar number already exists.");
        if (duplicateAccount) throw new IllegalArgumentException("A customer with this account number already exists.");
        if (duplicatePhone) throw new IllegalArgumentException("A customer with this phone number already exists.");
    }

public Customer addCustomer(Customer cust) {
        if (cust.getAccountNumber() == null) cust.setAccountNumber(generateAccountNumber());
        validateCustomer(cust);
        ensureUnique(cust, null);
        if (cust.getCreatedDate() == null) cust.setCreatedDate(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        return cr.save(cust);
	    }
	 
	    private Long generateAccountNumber() {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        Long accountNumber;
        do {
            accountNumber = random.nextLong(100_000_000_000L, 1_000_000_000_000L);
        } while (cr.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
 public Customer getCustomerById(Integer id) {
	        return cr.findById(id).orElse(null);
	    }
	 
	 public List<Customer> getAllCustomers() {
	        List<Customer> allCustomers = new ArrayList<>();
	        cr.findAll().forEach(customer -> { if (customer.getCreatedDate() == null) { customer.setCreatedDate(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)); cr.save(customer); } allCustomers.add(customer); });
	        return allCustomers;
	    }
	 
	 public Page<Customer> getAllCustomersByPagination(int page, int size) {
		   Pageable pageable = PageRequest.of(
		            page,
		            size,
		            Sort.by("custId").ascending()
		    );

		    return cr.findAll(pageable);
	    }
	 
	 public Customer putCustomer(Customer cust, Integer id) {
         validateCustomer(cust);
		 Customer existingCustomer = cr.findById(id)
		            .orElseThrow(() -> new RuntimeException("Customer not found"));
		 existingCustomer.setFirstName(cust.getFirstName());
		 existingCustomer.setLastName(cust.getLastName());
		 existingCustomer.setPhoneNumber(cust.getPhoneNumber());
		 existingCustomer.setLocation(cust.getLocation());
		 existingCustomer.setAadharNumber(cust.getAadharNumber());
		 return cr.save(existingCustomer);
	 }
	 
	 public Customer patchCustomer(Map<String, String> cust, Integer id) {
		    Customer existingCustomer = cr.findById(id)
		            .orElseThrow(() -> new RuntimeException("Customer not found"));

		    if (cust.containsKey("firstName")) {
		        existingCustomer.setFirstName(cust.get("firstName"));
		    }

		    if (cust.containsKey("lastName")) {
		        existingCustomer.setLastName(cust.get("lastName"));
		    }

		    if (cust.containsKey("phoneNumber")) {
		        existingCustomer.setPhoneNumber(Long.parseLong(cust.get("phoneNumber")));
		    }

		    if (cust.containsKey("location")) {
		        existingCustomer.setLocation(cust.get("location"));
		    }

		    if (cust.containsKey("aadharNumber")) {
		        existingCustomer.setAadharNumber(Long.parseLong(cust.get("aadharNumber")));
		    }

            validateCustomer(existingCustomer);
            ensureUnique(existingCustomer, id);
		    return cr.save(existingCustomer);
		}
	 
	 public String deleteCustomer(Integer custId) {
         Object[] cards = rt.getForObject("http://CREDITCARDS/cards/customer/{customerId}", Object[].class, custId);
         if (cards != null) for (Object item : cards) {
             java.util.Map<?, ?> card = (java.util.Map<?, ?>) item;
             Object creditId = card.get("creditId");
             if (creditId != null) {
                 Object result = rt.patchForObject("http://CREDITCARDS/patchCard/{id}", java.util.Map.of("status", "INACTIVE"), Object.class, creditId);
                 if (!(result instanceof java.util.Map<?, ?> updated) || !"INACTIVE".equals(String.valueOf(updated.get("status")))) {
                     throw new IllegalStateException("Card " + creditId + " was not marked INACTIVE; customer deletion was cancelled.");
                 }
             }
         }
		 cr.deleteById(custId);
		 return "The cutsomer with id:"+custId+" is deleted successfully";
	 }
	 
	 public List<PeriodCountDTO> getCardsCountByMonth() {
		    return mapPeriodCounts(cr.countGroupedByMonth());
		}

		public List<PeriodCountDTO> getCardsCountByWeek() {
		    return mapPeriodCounts(cr.countGroupedByWeek());
		}

		public List<PeriodCountDTO> getCardsCountByYear() {
		    return mapPeriodCounts(cr.countGroupedByYear());
		}

		
		public List<PeriodCountDTO> getCardsCountByPeriod(String type) {
		    List<Object[]> rows;

		    switch (type.toLowerCase()) {
		        case "month" -> rows = cr.countGroupedByMonth();
		        case "week"  -> rows = cr.countGroupedByWeek();
		        case "year"  -> rows = cr.countGroupedByYear();
		        default -> throw new IllegalArgumentException("Invalid type. Use month, week, or year.");
		    }

		    return mapPeriodCounts(rows);
		}
		private List<PeriodCountDTO> mapPeriodCounts(List<Object[]> rows) {
		    List<PeriodCountDTO> result = new ArrayList<>();

		    for (Object[] row : rows) {
		        result.add(new PeriodCountDTO(
		                (String) row[0],
		                ((Number) row[1]).longValue()
		        ));
		    }

		    return result;
		}
	

    @jakarta.annotation.PostConstruct
    public void backfillMissingCreatedDates() {
        List<Customer> customers = cr.findAll();
        customers.stream().filter(customer -> customer.getCreatedDate() == null).forEach(customer -> customer.setCreatedDate(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)));
        cr.saveAll(customers);
    }
}
