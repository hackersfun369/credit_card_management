package com.project;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.project.DTO.PeriodCountDTO;

@Service

public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RestTemplate rtTrans;

    public TransactionService(
            TransactionRepository transactionRepository,
            @Qualifier("restTransactionTemplate") RestTemplate rtTrans) {
        this.transactionRepository = transactionRepository;
        this.rtTrans = rtTrans;
    }

    @org.springframework.transaction.annotation.Transactional
    public Transactions addTransaction(Transactions transaction) {
        if (transaction.getAmount() == null || transaction.getAmount().signum() <= 0) throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        Object[] cards = rtTrans.getForObject("http://localhost:8082/card", Object[].class);
        java.util.Map<?, ?> card = cards == null ? null : java.util.Arrays.stream(cards).map(item -> (java.util.Map<?, ?>) item).filter(item -> String.valueOf(item.get("cardNumber")).equals(String.valueOf(transaction.getCardNumber()))).findFirst().orElse(null);
        if (card == null) throw new IllegalArgumentException("Card not found.");
        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(card.get("status")))) throw new IllegalStateException("Transactions are allowed only for active cards.");
        Object expiryDate = card.get("expiryDate");
        if (expiryDate != null && java.time.LocalDate.parse(String.valueOf(expiryDate)).isBefore(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))) throw new IllegalStateException("Expired cards cannot make transactions. Renew the card first.");
        if (transaction.getTransactionType() == null) transaction.setTransactionType(TransactionType.PURCHASE);
        if (transaction.getTransactionType() != TransactionType.PAYMENT) {
            Object[] merchants = rtTrans.getForObject("http://localhost:8084/merchants", Object[].class);
            java.util.Map<?, ?> merchant = merchants == null ? null : java.util.Arrays.stream(merchants)
                    .map(item -> (java.util.Map<?, ?>) item)
                    .filter(item -> String.valueOf(item.get("merchantId")).equals(String.valueOf(transaction.getMerchantId())))
                    .findFirst().orElse(null);
            if (merchant == null) throw new IllegalArgumentException("Merchant not found.");
            if (!"ACTIVE".equalsIgnoreCase(String.valueOf(merchant.get("status")))) {
                throw new IllegalStateException("Transactions are allowed only for active merchants.");
            }
        }
        if (transaction.getTimestamp() == null) transaction.setTimestamp(LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
        Number availableCredit = (Number) card.get("availableCredit");
        Number cardLimit = (Number) card.get("cardLimit");
        if (transaction.getTransactionType() == TransactionType.PAYMENT) {
            if (availableCredit == null || cardLimit == null) throw new IllegalStateException("Card credit information is unavailable.");
            java.math.BigDecimal outstanding = java.math.BigDecimal.valueOf(cardLimit.doubleValue()).subtract(java.math.BigDecimal.valueOf(availableCredit.doubleValue()));
            if (transaction.getAmount().compareTo(outstanding) > 0) throw new IllegalArgumentException("Repayment cannot exceed the outstanding card balance.");
            rtTrans.put("http://localhost:8082/card/number/{cardNumber}/available-credit?delta={delta}", null, transaction.getCardNumber(), transaction.getAmount());
            rtTrans.put("http://localhost:8082/card/number/{cardNumber}/advance-due-date", null, transaction.getCardNumber());
            transaction.setStatus(TransactionStatus.COMPLETED);
            return transactionRepository.save(transaction);
        }
        transaction.setStatus(transaction.getStatus() == null ? TransactionStatus.PENDING : transaction.getStatus());
        if (transaction.getStatus() == TransactionStatus.COMPLETED || transaction.getStatus() == TransactionStatus.FAILED || transaction.getStatus() == TransactionStatus.CANCELLED) throw new IllegalArgumentException("New transactions must start as PENDING or AUTHORIZED.");
        if (!isDebitTransaction(transaction)) return transactionRepository.save(transaction);
        rtTrans.put("http://localhost:8082/card/number/{cardNumber}/available-credit?delta={delta}", null, transaction.getCardNumber(), transaction.getAmount().negate());
        transaction.setCreditReserved(true);
        return transactionRepository.save(transaction);
    }
    public List<Transactions> getAllTransactions() {
        List<Transactions> allTransactions = new ArrayList<>();
        transactionRepository.findAll().forEach(allTransactions::add);
        return allTransactions;
    }

    public Page<Transactions> getTransactionsByPagination(int pageNumber, int size) {
        Pageable pageable = PageRequest.of(
                pageNumber,
                size,
                Sort.by("transactionId").ascending()
        );
        return transactionRepository.findAll(pageable);
    }

    @org.springframework.transaction.annotation.Transactional
    public String deleteTransaction(Long transactionId) {
        Transactions transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (isDebitTransaction(transaction) && transaction.isCreditReserved()) rtTrans.put("http://localhost:8082/card/number/{cardNumber}/available-credit?delta={delta}", null, transaction.getCardNumber(), transaction.getAmount());
        transactionRepository.delete(transaction);
        return "Transaction with id: " + transactionId + " is deleted successfully";
    }
    public List<Transactions> getTransactionsByCardNumber(Long cardNumber) {
        return transactionRepository.findByCardNumber(cardNumber);
    }

    public List<Transactions> getTransactionsByMerchantId(Long merchantId) {
        return transactionRepository.findByMerchantId(merchantId);
    }

    public List<Transactions> getTransactionsByDate(LocalDateTime timestamp) {
        return transactionRepository.findByTimestamp(timestamp);
    }

    public List<Transactions> getTransactionsByMerchantIdAndTimestamp(
            Long merchantId,
            LocalDateTime timestamp) {
        return transactionRepository.findByMerchantIdAndTimestamp(merchantId, timestamp);
    }

    public List<Transactions> getByAnyField(String fieldName, String value) {
        value = value.toUpperCase();
        return transactionRepository.findAll(
                TransactionSpecifications.hasFieldValue(fieldName, value)
        );
    }

    public List<PeriodCountDTO> getTransactionsCountByPeriod(String type) {
        List<Object[]> rows;

        switch (type.toLowerCase()) {
            case "month" -> rows = transactionRepository.countGroupedByMonth();
            case "week" -> rows = transactionRepository.countGroupedByWeek();
            case "year" -> rows = transactionRepository.countGroupedByYear();
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
    @org.springframework.transaction.annotation.Transactional
    public Transactions updateStatus(Long transactionId, String status) {
        Transactions transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new RuntimeException("Transaction not found"));
        TransactionStatus next = TransactionStatus.valueOf(status.toUpperCase());
        TransactionStatus current = transaction.getStatus();
        if (current == next) return transaction;
        if (current == TransactionStatus.COMPLETED || current == TransactionStatus.FAILED || current == TransactionStatus.CANCELLED) throw new IllegalStateException("A settled or cancelled transaction cannot be changed.");
        if (next == TransactionStatus.COMPLETED) {
            if (isDebitTransaction(transaction) && !transaction.isCreditReserved()) {
                rtTrans.put("http://localhost:8082/card/number/{cardNumber}/available-credit?delta={delta}", null, transaction.getCardNumber(), transaction.getAmount().negate());
                transaction.setCreditReserved(true);
            } else if (!isDebitTransaction(transaction)) {
                rtTrans.put("http://localhost:8082/card/number/{cardNumber}/available-credit?delta={delta}", null, transaction.getCardNumber(), transaction.getAmount());
            }
        }
        if ((next == TransactionStatus.FAILED || next == TransactionStatus.CANCELLED) && isDebitTransaction(transaction) && transaction.isCreditReserved()) {
            rtTrans.put("http://localhost:8082/card/number/{cardNumber}/available-credit?delta={delta}", null, transaction.getCardNumber(), transaction.getAmount());
            transaction.setCreditReserved(false);
        }
        transaction.setStatus(next);
        return transactionRepository.save(transaction);
    }

    private boolean isDebitTransaction(Transactions transaction) {
        return transaction.getTransactionType() == TransactionType.PURCHASE || transaction.getTransactionType() == TransactionType.AUTHORIZATION;
    }
}