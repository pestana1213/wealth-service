package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListTransactions {

    private static final Logger log = LoggerFactory.getLogger(ListTransactions.class);

    private final TransactionRepository transactionRepo;

    private final UserRepository userRepository;

    public ListTransactions(TransactionRepository transactionRepo, UserRepository userRepository) {
        this.transactionRepo = transactionRepo;
        this.userRepository = userRepository;
    }

    public List<Transaction> listTransactions(UUID userId) {
        if (userRepository.findById(userId).isEmpty()) {
            log.warn("List transactions received invalid user id");
            throw new IllegalArgumentException("User id is not valid");
        }
        return transactionRepo.findByUserIdOrderByOccurredAtDesc(userId);
    }

}
