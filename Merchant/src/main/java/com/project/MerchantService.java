package com.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@Service
@RestController

@CrossOrigin(origins = {
	    "http://localhost:4200",
	    "http://localhost:5173"
	})

public class MerchantService {

	private final MerchantRepository mr;
	private final RestTemplate rtMerch;
	
	public MerchantService(MerchantRepository mr, @Qualifier("restMerchantTemplate") RestTemplate rtMerch) {
		this.mr = mr;
		this.rtMerch = rtMerch;
	}
	
	@GetMapping("/merchants")
	public List<Merchant> getAllMerchants(){
		List<Merchant> allMerchants = new ArrayList<>();
		mr.findAll().forEach(allMerchants::add);
		return allMerchants;
	}
	
	    private void ensureUnique(Merchant merchant, Long ignoredId) {
        boolean duplicateMid = mr.findAll().stream().anyMatch(item -> !item.getMerchantId().equals(ignoredId) && java.util.Objects.equals(item.getMid(), merchant.getMid()));
        boolean duplicateAccount = mr.findAll().stream().anyMatch(item -> !item.getMerchantId().equals(ignoredId) && item.getMerchantAccountNmber() == merchant.getMerchantAccountNmber());
        if (duplicateMid) throw new IllegalArgumentException("A merchant with this MID already exists.");
        if (duplicateAccount) throw new IllegalArgumentException("A merchant with this account number already exists.");
    }
@PostMapping("/merchant")
	public Merchant addMerchant(@RequestBody Merchant merc) {
        ensureUnique(merc, null);
		return mr.save(merc);
	}
	
	@PutMapping("/merchant/{id}")
	public Merchant putMerchant(@RequestBody Merchant merchant,
	                            @PathVariable Long id) {

	    Merchant existingMerchant = mr.findById(id)
	            .orElseThrow(() -> new RuntimeException("Merchant not found"));

	    existingMerchant.setFirstName(merchant.getFirstName());
	    existingMerchant.setLastName(merchant.getLastName());
	    existingMerchant.setMid(merchant.getMid());
	    existingMerchant.setMerchantCategory(merchant.getMerchantCategory());
	    existingMerchant.setMerchantAccountNmber(merchant.getMerchantAccountNmber());
	    existingMerchant.setStatus(merchant.getStatus());
	    existingMerchant.setBankName(merchant.getBankName());
	    existingMerchant.setIfscCode(merchant.getIfscCode());

	    ensureUnique(existingMerchant, id);
    return mr.save(existingMerchant);
	}
	
	@PatchMapping("/merchant/{id}")
	public Merchant patchMerchant(@RequestBody Map<String, String> merchant,
	                              @PathVariable Long id) {

	    Merchant existingMerchant = mr.findById(id)
	            .orElseThrow(() -> new RuntimeException("Merchant not found"));

	    if (merchant.containsKey("firstName")) {
	        existingMerchant.setFirstName(merchant.get("firstName"));
	    }

	    if (merchant.containsKey("lastName")) {
	        existingMerchant.setLastName(merchant.get("lastName"));
	    }

	    if (merchant.containsKey("MID")) {
	        existingMerchant.setMid(merchant.get("MID"));
	    }

	    if (merchant.containsKey("merchantCategory")) {
	        existingMerchant.setMerchantCategory(
	                MerchantCategory.valueOf(merchant.get("merchantCategory").toUpperCase()));
	    }

	    if (merchant.containsKey("merchantAccountNmber")) {
	        existingMerchant.setMerchantAccountNmber(
	                Long.parseLong(merchant.get("merchantAccountNmber")));
	    }

	    if (merchant.containsKey("status")) {
	        existingMerchant.setStatus(
	                MerchantStatus.valueOf(merchant.get("status").toUpperCase()));
	    }

	    if (merchant.containsKey("bankName")) {
	        existingMerchant.setBankName(merchant.get("bankName"));
	    }

	    if (merchant.containsKey("ifscCode")) {
	        existingMerchant.setIfscCode(merchant.get("ifscCode"));
	    }

	    ensureUnique(existingMerchant, id);
    return mr.save(existingMerchant);
	}
	
	@DeleteMapping("/merchant/{id}")
	public String deleteMerchant(@PathVariable Long id) {

	    mr.findById(id)
	      .orElseThrow(() -> new RuntimeException("Merchant not found"));

	    mr.deleteById(id);

	    return "Merchant with id " + id + " deleted successfully.";
	}
	
}
