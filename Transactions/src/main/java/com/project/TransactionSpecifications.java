package com.project;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.jpa.domain.Specification;

public class TransactionSpecifications {

    public static Specification<Transactions> hasFieldValue(String fieldName, String value) {

        return (root, query, cb) -> {

            switch (fieldName) {

                case "merchantId":
                case "cardNumber":
                case "referenceNumber":
                    return cb.equal(root.get(fieldName), Long.valueOf(value));

                case "amount":
                case "fee":
                    return cb.equal(root.get(fieldName), new BigDecimal(value));

                case "timestamp":
                    return cb.equal(root.get(fieldName), LocalDateTime.parse(value));

                case "international":
                    return cb.equal(root.get(fieldName), Boolean.valueOf(value));

                case "transactionType":
                    return cb.equal(root.get(fieldName),
                            TransactionType.valueOf(value.toUpperCase()));

                case "status":
                    return cb.equal(root.get(fieldName),
                            TransactionStatus.valueOf(value.toUpperCase()));

                case "paymentMethod":
                    return cb.equal(root.get(fieldName),
                            PaymentMethod.valueOf(value.toUpperCase()));

                default:
                    return cb.equal(root.get(fieldName), value);
            }
        };
    }
}