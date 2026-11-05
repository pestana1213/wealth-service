package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.port.AccountPort;
import com.finance.wealth_service.account.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetAccounts implements AccountPort {

    private final AccountRepository accountRepo;

    public GetAccounts(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    @Override
    public List<Account> getAccountsFromIds(List<UUID> ids) {
        return accountRepo.findAllByIds(ids);
    }

    @Override
    public boolean isOwnedBy(UUID accountId, String subject, String issuer) {
        return accountRepo.findOwnedBy(accountId, subject, issuer).isPresent();
    }
}
