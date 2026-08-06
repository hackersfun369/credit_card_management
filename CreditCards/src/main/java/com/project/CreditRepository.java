package com.project;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface CreditRepository extends JpaRepository<CreditCards,Integer> {
	List<CreditCards> findByCustomerId(Integer customerId);
    java.util.Optional<CreditCards> findByCardNumber(Long cardNumber);
    boolean existsByCardNumber(Long cardNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select card from CreditCards card where card.cardNumber = :cardNumber")
    java.util.Optional<CreditCards> findByCardNumberForUpdate(@Param("cardNumber") Long cardNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select card from CreditCards card where card.customerId = :customerId")
    List<CreditCards> findByCustomerIdForUpdate(@Param("customerId") Integer customerId);
    boolean existsByCustomerIdAndCardType(Integer customerId, CardType cardType);
	
	@Query(value = """
		    SELECT TO_CHAR(created_date, 'YYYY-MM') AS period,
		           COUNT(*) AS total
		    FROM credit_cards
		    GROUP BY TO_CHAR(created_date, 'YYYY-MM')
		    ORDER BY period
		    """, nativeQuery = true)
			List<Object[]> countGroupedByMonth();

		@Query(value = """
			    SELECT TO_CHAR(created_date, 'IYYY-IW') AS period,
			           COUNT(*) AS total
			    FROM credit_cards
			    GROUP BY TO_CHAR(created_date, 'IYYY-IW')
			    ORDER BY period
			    """, nativeQuery = true)
			List<Object[]> countGroupedByWeek();

		    @Query(value = """
		    		    SELECT TO_CHAR(created_date, 'YYYY') AS period,
		    		           COUNT(*) AS total
		    		    FROM credit_cards
		    		    GROUP BY TO_CHAR(created_date, 'YYYY')
		    		    ORDER BY period
		    		    """, nativeQuery = true)
		    		List<Object[]> countGroupedByYear();
}
