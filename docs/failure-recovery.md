# Failure and recovery cases

| Failure point | Stored state | Recovery behavior |
|---|---|---|
| Account service times out after applying a debit/credit | Transfer remains `PENDING`; account service has the transfer ID in `transfer_operations` | Retry the same `Idempotency-Key`; the same transfer ID is resumed and account service treats the repeated command as a no-op |
| Ledger service is unavailable after balance application | Transfer remains `PENDING`; account command is already idempotent | Retry the same key after ledger recovery; ledger posting is unique by transfer ID and resumes without another balance change |
| Kafka is unavailable | Outbox row remains unpublished because publisher waits for broker acknowledgement before marking it | Publisher retries on its next scheduled run; notification consumer deduplicates by topic and outbox ID |
| Fraud model is unavailable | Transfer scoring uses the conservative local heuristic | Fraud API returns a heuristic score and transactions remain available; review the affected deployment before relying on scores |
| Scheduled transfer receives a transient downstream error | Schedule remains `PENDING`; the transfer reservation is committed independently | Admin calls `POST /api/v1/scheduled-payments/run-due` again; stable `sched-{id}` key resumes the existing transfer |
| Refresh token is reused or expired | Token family is revoked | Customer signs in again; refresh token values are stored hashed in the identity database |
| PostgreSQL is unavailable during startup | Service cannot pass datasource/Flyway startup | Restore database connectivity and restart service; Flyway applies pending migrations. Back up before destructive schema or volume operations |

The HTTP workflows still span separate service databases. The account and ledger operations are idempotent and retryable, but operational recovery must monitor `PENDING` transfers and compare account balances with journal totals. This demo does not implement automated compensation or financial settlement.
