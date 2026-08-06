package com.project;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Integer> {
    boolean existsByAadharNumber(Long aadharNumber);
    boolean existsByAccountNumber(Long accountNumber);
    boolean existsByPhoneNumber(Long phoneNumber);

	@Query(value = """
		    SELECT TO_CHAR(created_date, 'YYYY-MM') AS period,
		           COUNT(*) AS total
		    FROM customer
		    GROUP BY TO_CHAR(created_date, 'YYYY-MM')
		    ORDER BY period
		    """, nativeQuery = true)
			List<Object[]> countGroupedByMonth();

		@Query(value = """
			    SELECT TO_CHAR(created_date, 'IYYY-IW') AS period,
			           COUNT(*) AS total
			    FROM customer
			    GROUP BY TO_CHAR(created_date, 'IYYY-IW')
			    ORDER BY period
			    """, nativeQuery = true)
			List<Object[]> countGroupedByWeek();

		    @Query(value = """
		    		    SELECT TO_CHAR(created_date, 'YYYY') AS period,
		    		           COUNT(*) AS total
		    		    FROM customer
		    		    GROUP BY TO_CHAR(created_date, 'YYYY')
		    		    ORDER BY period
		    		    """, nativeQuery = true)
		    		List<Object[]> countGroupedByYear();
	
}
