package com.project;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.project.DTO.PeriodCountDTO;

@RestController
@CrossOrigin(origins = {
	    "http://localhost:4200",
	    "http://localhost:5173"
	})
public class CustomerController {

	@Autowired
	CustomerService cs;
	
	@GetMapping("/customer")
	public List<Customer> getAllCustomers(){
		return cs.getAllCustomers();
	}
	
	@PostMapping("/customer")
	public Customer addCustomer(@RequestBody Customer cust){
		return cs.addCustomer(cust);
	}
	
	@GetMapping("/customers/{pageNumber}/{size}")
	public Page<Customer> getCustomersByPagination(
	        @PathVariable int pageNumber,
	        @PathVariable int size) {

	    return cs.getAllCustomersByPagination(pageNumber, size);
	}

	@GetMapping("/customer/{id}")
	public Customer getCustomerById(@PathVariable("id") int id) {
		return cs.getCustomerById(id);
	}
	
	@PutMapping("/putCustomer/{id}")
	public Customer putCustomer(@RequestBody Customer cust, @PathVariable("id") int id) {
		return cs.putCustomer(cust, id);
	}
	
	@PatchMapping("/patchCustomer/{id}")
	public Customer patchCustomer(@RequestBody Map<String,String> cust, @PathVariable("id") int id) {
		return cs.patchCustomer(cust, id);
	}
	
	@DeleteMapping("/customer/{id}")
	public String deleteCustomer(@PathVariable("id") int id) {
		return cs.deleteCustomer(id);
	}
	
	@GetMapping("/customers/group/month")
    public List<PeriodCountDTO> getCardsGroupedByMonth() {
        return cs.getCardsCountByMonth();
    }

    @GetMapping("/customers/group/week")
    public List<PeriodCountDTO> getCardsGroupedByWeek() {
        return cs.getCardsCountByWeek();
    }

    @GetMapping("/customers/group/year")
    public List<PeriodCountDTO> getCardsGroupedByYear() {
        return cs.getCardsCountByYear();
    }
    
    @GetMapping("/customers/group/{type}")
    public List<PeriodCountDTO> getCardsGroupedByType(@PathVariable String type) {
        return cs.getCardsCountByPeriod(type);
    }
	
}

