CREATE FUNCTION reject_journal_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Journal records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER journal_entries_immutable
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();

CREATE TRIGGER journal_lines_immutable
    BEFORE UPDATE OR DELETE ON journal_lines
    FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();

CREATE FUNCTION enforce_balanced_journal() RETURNS trigger AS $$
DECLARE
    target_entry BIGINT;
    line_count BIGINT;
    debit_total NUMERIC(18,2);
    credit_total NUMERIC(18,2);
BEGIN
    IF TG_TABLE_NAME = 'journal_entries' THEN
        target_entry := NEW.id;
    ELSE
        target_entry := NEW.entry_id;
    END IF;
    SELECT COUNT(*), COALESCE(SUM(debit), 0), COALESCE(SUM(credit), 0)
      INTO line_count, debit_total, credit_total
      FROM journal_lines WHERE entry_id = target_entry;
    IF line_count <> 2 OR debit_total <> credit_total OR debit_total <= 0 THEN
        RAISE EXCEPTION 'Journal entry % must contain exactly two balanced lines', target_entry;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_entry_balanced
    AFTER INSERT ON journal_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_balanced_journal();

CREATE CONSTRAINT TRIGGER journal_lines_balanced
    AFTER INSERT ON journal_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_balanced_journal();
