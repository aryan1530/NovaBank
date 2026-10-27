package com.novabank.account.service;

import com.novabank.account.domain.Account;
import com.novabank.account.domain.FixedDeposit;
import com.novabank.account.domain.LoanApplication;
import com.novabank.account.repo.AccountRepository;
import com.novabank.account.repo.FixedDepositRepository;
import com.novabank.account.repo.LoanApplicationRepository;
import com.novabank.common.exception.BusinessException;
import com.novabank.common.exception.NotFoundException;
import com.novabank.common.money.Money;
import com.novabank.common.security.SecuritySupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final FixedDepositRepository fds;
    private final LoanApplicationRepository loans;
    private final JdbcTemplate jdbc;
    private final RestClient rest = RestClient.create();
    private final String customerUrl;
    private final String internalToken;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accounts, FixedDepositRepository fds, LoanApplicationRepository loans, JdbcTemplate jdbc,
                          @Value("${novabank.customer-service-url}") String customerUrl,
                          @Value("${novabank.internal-token}") String internalToken) {
        this.accounts = accounts;
        this.fds = fds;
        this.loans = loans;
        this.jdbc = jdbc;
        this.customerUrl = customerUrl;
        this.internalToken = internalToken;
    }

    public record AccountView(Long id, String accountNumber, Long userId, Long customerId, String type, String currency, BigDecimal balance, String status) {
        static AccountView of(Account a) {
            return new AccountView(a.getId(), a.getAccountNumber(), a.getUserId(), a.getCustomerId(), a.getType(), a.getCurrency(), a.getBalance(), a.getStatus());
        }
    }

    public record TransferApplyRequest(Long debitAccountId, Long creditAccountId, BigDecimal amount) {}
    public record FdRequest(Long accountId, BigDecimal principal, BigDecimal rate, int tenureMonths) {}
    public record LoanRequest(Long accountId, BigDecimal amount, int tenureMonths, String purpose) {}

    @Transactional
    public AccountView open(Long userId, String type) {
        Long customerId = fetchCustomerId(userId);
        Account a = new Account();
        a.setUserId(userId);
        a.setCustomerId(customerId);
        a.setType(type == null ? "SAVINGS" : type);
        a.setAccountNumber(generateNumber());
        a.setBalance(Money.of("0.00"));
        return AccountView.of(accounts.save(a));
    }

    public List<AccountView> mine(Long userId) {
        if (!SecuritySupport.hasRole("ADMIN") && !userId.equals(SecuritySupport.currentUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ISOLATION", "Cannot list other accounts");
        }
        return accounts.findByUserId(userId).stream().map(AccountView::of).toList();
    }

    public List<AccountView> internalAccountsForUser(Long userId) {
        return accounts.findByUserId(userId).stream().map(AccountView::of).toList();
    }

    public AccountView get(Long id) {
        Account a = accounts.findById(id).orElseThrow(() -> new NotFoundException("Account not found"));
        SecuritySupport.requireSelfOrAdmin(a.getUserId());
        return AccountView.of(a);
    }

    @Transactional
    public void applyTransfer(Long transferId, Long userId, Long debitId, Long creditId, BigDecimal amount) {
        int inserted = jdbc.update("INSERT INTO transfer_operations(transfer_id) VALUES (?) ON CONFLICT DO NOTHING", transferId);
        if (inserted == 0) return;
        BigDecimal amt = Money.normalize(amount);
        if (!Money.isPositive(amt)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Amount must be positive");
        }
        if (debitId.equals(creditId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT", "Debit and credit must differ");
        }
        Long first = debitId < creditId ? debitId : creditId;
        Long second = debitId < creditId ? creditId : debitId;
        Account a1 = accounts.findByIdForUpdate(first).orElseThrow(() -> new NotFoundException("Account not found"));
        Account a2 = accounts.findByIdForUpdate(second).orElseThrow(() -> new NotFoundException("Account not found"));
        Account debit = debitId.equals(a1.getId()) ? a1 : a2;
        Account credit = creditId.equals(a1.getId()) ? a1 : a2;
        if (!debit.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_OWNERSHIP", "Debit account does not belong to the authenticated customer");
        }
        if (!"ACTIVE".equals(debit.getStatus()) || !"ACTIVE".equals(credit.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "ACCOUNT_CLOSED", "Account is not active");
        }
        if (debit.getBalance().compareTo(amt) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient funds");
        }
        debit.setBalance(debit.getBalance().subtract(amt));
        credit.setBalance(credit.getBalance().add(amt));
    }

    @Transactional
    public Map<String, Object> openFd(Long userId, FdRequest req) {
        Account a = accounts.findByIdForUpdate(req.accountId()).orElseThrow(() -> new NotFoundException("Account not found"));
        SecuritySupport.requireSelfOrAdmin(a.getUserId());
        BigDecimal principal = Money.normalize(req.principal());
        if (a.getBalance().compareTo(principal) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "Insufficient funds");
        }
        a.setBalance(a.getBalance().subtract(principal));
        BigDecimal matured = principal.multiply(BigDecimal.ONE.add(req.rate().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_EVEN)
                .multiply(BigDecimal.valueOf(req.tenureMonths()).divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_EVEN))))
                .setScale(2, RoundingMode.HALF_EVEN);
        FixedDeposit fd = new FixedDeposit();
        fd.setAccountId(a.getId());
        fd.setPrincipal(principal);
        fd.setRate(req.rate());
        fd.setTenureMonths(req.tenureMonths());
        fd.setStartDate(LocalDate.now());
        fd.setMaturityDate(LocalDate.now().plusMonths(req.tenureMonths()));
        fd.setMaturedAmount(matured);
        fds.save(fd);
        return Map.of("id", fd.getId(), "maturedAmount", matured, "maturityDate", fd.getMaturityDate());
    }

    @Transactional
    public LoanApplication applyLoan(Long userId, LoanRequest req) {
        Account a = accounts.findById(req.accountId()).orElseThrow(() -> new NotFoundException("Account not found"));
        SecuritySupport.requireSelfOrAdmin(a.getUserId());
        LoanApplication loan = new LoanApplication();
        loan.setUserId(userId);
        loan.setAccountId(a.getId());
        loan.setAmount(Money.normalize(req.amount()));
        loan.setTenureMonths(req.tenureMonths());
        loan.setPurpose(req.purpose());
        return loans.save(loan);
    }

    public List<LoanApplication> loansFor(Long userId) {
        return loans.findByUserId(userId);
    }

    @Transactional
    public LoanApplication decideLoan(Long id, String decision) {
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "LOAN_DECISION", "Decision must be APPROVED or REJECTED");
        }
        LoanApplication loan = loans.findById(id).orElseThrow(() -> new NotFoundException("Loan application not found"));
        if (!"SUBMITTED".equals(loan.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "LOAN_ALREADY_DECIDED", "Loan application was already reviewed");
        }
        loan.setStatus(decision);
        return loans.save(loan);
    }

    public List<FixedDeposit> fdsForAccount(Long accountId) {
        Account a = accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Account not found"));
        SecuritySupport.requireSelfOrAdmin(a.getUserId());
        return fds.findByAccountId(accountId);
    }

    private Long fetchCustomerId(Long userId) {
        Map<?, ?> body = rest.get()
                .uri(customerUrl + "/internal/customers/by-user/" + userId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(Map.class);
        if (body == null || body.get("id") == null) {
            throw new BusinessException(HttpStatus.FAILED_DEPENDENCY, "NO_CUSTOMER", "Complete customer profile first");
        }
        return ((Number) body.get("id")).longValue();
    }

    private String generateNumber() {
        return "88" + String.format("%010d", Math.abs(random.nextInt(1_000_000_000)));
    }
}
