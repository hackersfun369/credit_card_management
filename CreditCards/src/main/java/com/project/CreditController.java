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

public class CreditController {

	@Autowired
	CreditService cs;
	
	@GetMapping("/card")
	public List<CreditCards> getAllCards(){
		return cs.getAllCards();
	}
	
	@PostMapping("/card")
	public CreditCards addCard(@RequestBody CreditCards card){
		return cs.addCard(card);
	}
	
	@GetMapping("/cards/{pageNumber}/{size}")
	public Page<CreditCards> getCardsBypagination(
	        @PathVariable int pageNumber,
	        @PathVariable int size) {

	    return cs.getAllCardsByPagination(pageNumber, size);
	}
	
	@GetMapping("/card/{id}")
	public CreditCards get(@PathVariable("id") int id) {
		return cs.getCardById(id);
	}
	
	@PutMapping("/putCard/{id}")
	public CreditCards putCards(@RequestBody CreditCards card, @PathVariable("id") int id) {
		return cs.putCard(card, id);
	}
	
	@PatchMapping("/patchCard/{id}")
	public CreditCards patchCustomer(@RequestBody Map<String,String> card, @PathVariable("id") int id) {
		return cs.patchCard(card, id);
	}
    @PostMapping("/cards/{id}/renew")
    public List<CreditCards> renewCard(@PathVariable Integer id, @RequestBody Map<String, String> request) {
        return cs.renewCard(id, java.time.LocalDate.parse(request.get("expiryDate")), java.time.LocalDate.parse(request.get("dueDate")));
    }
    @DeleteMapping("/card/{id}")
	public String deleteCustomer(@PathVariable("id") int id) {
		return cs.deleteCard(id);
	}
	
	@GetMapping("/cards/customer/{customerId}")
	public List<CreditCards> getAllCardsOfCustomerById(@PathVariable Integer customerId) {
	    return cs.getAllCardsOfCustomerById(customerId);
	}
	
	@GetMapping("/cards/group/month")
    public List<PeriodCountDTO> getCardsGroupedByMonth() {
        return cs.getCardsCountByMonth();
    }

    @GetMapping("/cards/group/week")
    public List<PeriodCountDTO> getCardsGroupedByWeek() {
        return cs.getCardsCountByWeek();
    }

    @GetMapping("/cards/group/year")
    public List<PeriodCountDTO> getCardsGroupedByYear() {
        return cs.getCardsCountByYear();
    }
    
    @GetMapping("/cards/group/{type}")
    public List<PeriodCountDTO> getCardsGroupedByType(@PathVariable String type) {
        return cs.getCardsCountByPeriod(type);
    }
    @PutMapping("/cards/customer/{customerId}/deactivate")
    public void deactivateCardsForCustomer(@PathVariable Integer customerId) { cs.deactivateCardsForCustomer(customerId); }
    @PutMapping("/card/number/{cardNumber}/advance-due-date")
    public CreditCards advanceDueDate(@PathVariable Long cardNumber) { return cs.advanceDueDate(cardNumber); }
    @PutMapping("/card/number/{cardNumber}/available-credit")
    public CreditCards adjustAvailableCredit(@PathVariable Long cardNumber, @org.springframework.web.bind.annotation.RequestParam Double delta) { return cs.adjustAvailableCredit(cardNumber, delta); }
}
