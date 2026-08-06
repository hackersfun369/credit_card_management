package com.project;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transactions, Long>, JpaSpecificationExecutor<Transactions> {

    List<Transactions> findByCardNumber(Long cardNumber);

    List<Transactions> findByMerchantId(Long merchantId);

    List<Transactions> findByTimestamp(java.time.LocalDateTime timestamp);

    List<Transactions> findByMerchantIdAndTimestamp(Long merchantId, java.time.LocalDateTime timestamp);

    @Query(value = """
        SELECT TO_CHAR(TRANSACTION_TIMESTAMP, 'YYYY-MM') AS period, COUNT(*) AS total
        FROM TRANSACTIONS
        GROUP BY TO_CHAR(TRANSACTION_TIMESTAMP, 'YYYY-MM')
        ORDER BY period
        """, nativeQuery = true)
    List<Object[]> countGroupedByMonth();

    @Query(value = """
        SELECT TO_CHAR(TRANSACTION_TIMESTAMP, 'IYYY-IW') AS period, COUNT(*) AS total
        FROM TRANSACTIONS
        GROUP BY TO_CHAR(TRANSACTION_TIMESTAMP, 'IYYY-IW')
        ORDER BY period
        """, nativeQuery = true)
    List<Object[]> countGroupedByWeek();

    @Query(value = """
        SELECT TO_CHAR(TRANSACTION_TIMESTAMP, 'YYYY') AS period, COUNT(*) AS total
        FROM TRANSACTIONS
        GROUP BY TO_CHAR(TRANSACTION_TIMESTAMP, 'YYYY')
        ORDER BY period
        """, nativeQuery = true)
    List<Object[]> countGroupedByYear();
}