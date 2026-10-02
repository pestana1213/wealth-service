package com.finance.wealth_service.account.port;

import com.finance.wealth_service.account.entity.Account;

import java.util.List;
import java.util.UUID;

public interface AccountPort {

    List<Account> getAccountsFromIds(List<UUID> ids);
}
