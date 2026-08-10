package com.project;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@CrossOrigin(origins = {
	    "http://localhost:4200",
	    "http://localhost:5173"
	})
@RestController
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transaction")
    public Transactions addTransaction(@RequestBody Transactions transaction) {
        return transactionService.addTransaction(transaction);
    }

    @GetMapping("/transactions")
    public List<Transactions> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @GetMapping("/transactions/{pageNumber}/{size}")
    public Page<Transactions> getTransactionsByPagination(
            @PathVariable("pageNumber") int pageNumber,
            @PathVariable("size") int size) {
        return transactionService.getTransactionsByPagination(pageNumber, size);
    }

    @DeleteMapping("/transaction/{transactionId}")
    public String deleteTransaction(@PathVariable("transactionId") Long transactionId) {
        return transactionService.deleteTransaction(transactionId);
    }

    @GetMapping("/transactions/card/{cardNumber}")
    public List<Transactions> getTransactionsByCardNumber(@PathVariable("cardNumber") Long cardNumber) {
        return transactionService.getTransactionsByCardNumber(cardNumber);
    }

    @GetMapping("/transactions/merchant/{merchantId}")
    public List<Transactions> getTransactionsByMerchantId(@PathVariable Long merchantId) {
        return transactionService.getTransactionsByMerchantId(merchantId);
    }

    @GetMapping("/transactions/date")
    public List<Transactions> getTransactionsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp) {
        return transactionService.getTransactionsByDate(timestamp);
    }

    @GetMapping("/transactions/merchant/{merchantId}/date")
    public List<Transactions> getTransactionsByMerchantIdAndTimestamp(
            @PathVariable Long merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp) {
        return transactionService.getTransactionsByMerchantIdAndTimestamp(merchantId, timestamp);
    }

    @GetMapping("/search")
    public List<Transactions> getByAnyField(
            @RequestParam String fieldName,
            @RequestParam String value) {
        return transactionService.getByAnyField(fieldName, value);
    }

    @GetMapping("/transactions/group/{type}")
    public List<com.project.DTO.PeriodCountDTO> getTransactionsGroupedByType(@PathVariable String type) {
        return transactionService.getTransactionsCountByPeriod(type);
    }
    @PutMapping("/transaction/{transactionId}/status")
    public Transactions updateTransactionStatus(@PathVariable Long transactionId, @RequestBody java.util.Map<String, String> body) { return transactionService.updateStatus(transactionId, body.get("status")); }
}