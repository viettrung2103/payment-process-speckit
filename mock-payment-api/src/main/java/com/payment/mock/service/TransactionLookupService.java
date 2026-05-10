package com.payment.mock.service;

import com.payment.mock.entity.Transaction;
import com.payment.mock.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TransactionLookupService {

    private final TransactionRepository transactionRepository;

    public TransactionLookupService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Optional<Transaction> findByTransactionId(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId);
    }

    public Page<Transaction> findAll(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    public Transaction findByTransactionIdOrThrow(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
    }

    public static class TransactionNotFoundException extends RuntimeException {
        public TransactionNotFoundException(String message) {
            super(message);
        }
    }
}
