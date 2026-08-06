package com.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long>{
    boolean existsByMid(String mid);
    boolean existsByMerchantAccountNmber(long merchantAccountNmber);
	
}
