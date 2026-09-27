-- Lifetime net payments are derived, never a separately maintained member balance.
-- USD is the application's supported payment currency. Source snapshots use cents;
-- normalized integration tables and the ledger use dollars.

-- A snapshot may contain historical refunds/disputes that have never produced lifecycle
-- rows. Prefer the latest snapshot for an external loss ID, falling back to retained
-- lifecycle rows when that ID is absent. Never count the two representations twice.
CREATE VIEW zeffy_payment_loss AS
SELECT losses.payment_id, SUM(losses.amount) AS amount
FROM (
    SELECT p.id AS payment_id, r.external_id, MAX(r.amount_cents) / 100 AS amount
    FROM zeffy_payment p
    JOIN JSON_TABLE(IF(JSON_VALID(p.latest_payload), p.latest_payload, '{}'), '$.refunds[*]'
        COLUMNS (
            external_id VARCHAR(100) PATH '$.id',
            amount_cents DECIMAL(20, 2) PATH '$.amount' NULL ON ERROR,
            currency VARCHAR(10) PATH '$.currency',
            status VARCHAR(30) PATH '$.status'
        )) r ON TRUE
    WHERE r.external_id IS NOT NULL AND LOWER(r.status) = 'succeeded'
      AND UPPER(r.currency) = UPPER(p.currency)
      AND r.amount_cents >= 0 AND r.amount_cents = FLOOR(r.amount_cents)
    GROUP BY p.id, r.external_id

    UNION ALL

    SELECT r.zeffy_payment_record_id, r.zeffy_refund_id, r.amount
    FROM zeffy_refund r
    JOIN zeffy_payment p ON p.id = r.zeffy_payment_record_id
    WHERE LOWER(r.status) = 'succeeded' AND r.amount >= 0
      AND UPPER(r.currency) = UPPER(p.currency)
      AND NOT EXISTS (
          SELECT 1
          FROM JSON_TABLE(IF(JSON_VALID(p.latest_payload), p.latest_payload, '{}'), '$.refunds[*]'
              COLUMNS (external_id VARCHAR(100) PATH '$.id')) snapshot
          WHERE snapshot.external_id = r.zeffy_refund_id
      )

    UNION ALL

    SELECT p.id, d.external_id, d.amount_cents / 100
    FROM zeffy_payment p
    JOIN JSON_TABLE(IF(JSON_VALID(p.latest_payload), p.latest_payload, '{}'), '$.dispute'
        COLUMNS (
            external_id VARCHAR(100) PATH '$.id',
            amount_cents DECIMAL(20, 2) PATH '$.amount' NULL ON ERROR,
            currency VARCHAR(10) PATH '$.currency',
            status VARCHAR(30) PATH '$.status'
        )) d ON TRUE
    WHERE d.external_id IS NOT NULL AND LOWER(d.status) = 'lost'
      AND UPPER(d.currency) = UPPER(p.currency)
      AND d.amount_cents >= 0 AND d.amount_cents = FLOOR(d.amount_cents)

    UNION ALL

    SELECT d.zeffy_payment_record_id, d.zeffy_dispute_id, d.amount
    FROM zeffy_dispute d
    JOIN zeffy_payment p ON p.id = d.zeffy_payment_record_id
    WHERE LOWER(d.status) = 'lost' AND d.amount >= 0
      AND UPPER(d.currency) = UPPER(p.currency)
      AND NOT EXISTS (
          SELECT 1
          FROM JSON_TABLE(IF(JSON_VALID(p.latest_payload), p.latest_payload, '{}'), '$.dispute'
              COLUMNS (external_id VARCHAR(100) PATH '$.id')) snapshot
          WHERE snapshot.external_id = d.zeffy_dispute_id
      )
) losses
GROUP BY losses.payment_id;

-- Resolve historical/ignored Zeffy payments without changing campaign policy or creating
-- accounting records. Existing links win; email is used only when exactly one person matches.
CREATE VIEW person_payment_amount AS
SELECT COALESCE(p.person_id, m.person_id, pm.person_id, c.person_id, j.payer_id,
                ref.person_id, email.person_id) AS person_id,
       CASE WHEN LOWER(p.refund_status) = 'full' THEN 0
            ELSE GREATEST(p.amount - COALESCE(loss.amount, 0), 0) END AS amount
FROM zeffy_payment p
LEFT JOIN member m ON m.id = p.member_id
LEFT JOIN member_payment mp ON mp.id = p.member_payment_id
LEFT JOIN member pm ON pm.id = mp.member_id
LEFT JOIN zeffy_contact c ON c.zeffy_contact_id = p.contact_id
LEFT JOIN journal_entry j ON j.id = p.journal_entry_id
LEFT JOIN (
    SELECT mp.transaction_ref, MIN(m.person_id) AS person_id
    FROM member_payment mp JOIN member m ON m.id = mp.member_id
    WHERE mp.payment_method = 'zeffy' AND mp.transaction_ref IS NOT NULL
    GROUP BY mp.transaction_ref
    HAVING COUNT(DISTINCT m.person_id) = 1
) ref ON ref.transaction_ref = p.zeffy_payment_id
LEFT JOIN (
    SELECT LOWER(TRIM(email)) AS normalized_email, MIN(id) AS person_id
    FROM person
    WHERE email IS NOT NULL AND TRIM(email) <> ''
    GROUP BY LOWER(TRIM(email))
    HAVING COUNT(*) = 1
) email ON email.normalized_email = LOWER(TRIM(p.buyer_email))
LEFT JOIN zeffy_payment_loss loss ON loss.payment_id = p.id
WHERE LOWER(p.status) = 'succeeded' AND UPPER(p.currency) = 'USD' AND p.amount >= 0
-- Deletion of an external record is not a refund; retained money still counts.

UNION ALL

-- The ledger covers Stripe and manual income (cash, check, Zelle, etc.), regardless of
-- revenue category. Credit minus debit includes recorded refunds, before processing fees.
-- Zeffy originals and automatic corrections are already represented by the source above.
SELECT j.payer_id, l.credit_amount - l.debit_amount
FROM journal_entry j
JOIN journal_line l ON l.journal_entry_id = j.id
JOIN account a ON a.id = l.account_id
WHERE j.status = 'posted' AND j.payer_id IS NOT NULL AND a.account_type = 'revenue'
  AND NOT EXISTS (SELECT 1 FROM zeffy_payment p WHERE p.journal_entry_id = j.id)
  AND NOT EXISTS (SELECT 1 FROM zeffy_refund r WHERE r.correction_journal_entry_id = j.id)
  AND NOT EXISTS (SELECT 1 FROM zeffy_dispute d WHERE d.correction_journal_entry_id = j.id)
  AND NOT EXISTS (SELECT 1 FROM zeffy_payment_change c WHERE c.correction_journal_entry_id = j.id)

UNION ALL

-- Successful Stripe events can await product mapping and have no ledger entry yet.
-- The webhook parser only populates amount for actionable successful payment events;
-- unpaid checkouts and duplicate companion events are marked ignored with no amount.
SELECT COALESCE(s.person_id, m.person_id, pm.person_id, ref.person_id, email.person_id), s.amount
FROM stripe_webhook_event s
LEFT JOIN member m ON m.id = s.member_id
LEFT JOIN member_payment mp ON mp.id = s.member_payment_id
LEFT JOIN member pm ON pm.id = mp.member_id
LEFT JOIN (
    SELECT mp.transaction_ref, MIN(m.person_id) AS person_id
    FROM member_payment mp JOIN member m ON m.id = mp.member_id
    WHERE mp.payment_method = 'stripe' AND mp.transaction_ref IS NOT NULL
    GROUP BY mp.transaction_ref
    HAVING COUNT(DISTINCT m.person_id) = 1
) ref ON ref.transaction_ref = s.stripe_event_id
LEFT JOIN (
    SELECT LOWER(TRIM(email)) AS normalized_email, MIN(id) AS person_id
    FROM person
    WHERE email IS NOT NULL AND TRIM(email) <> ''
    GROUP BY LOWER(TRIM(email))
    HAVING COUNT(*) = 1
) email ON email.normalized_email = LOWER(TRIM(s.email))
WHERE s.journal_entry_id IS NULL AND s.amount >= 0 AND s.status <> 'ignored'
  AND s.event_type IN ('checkout.session.completed', 'invoice.payment_succeeded', 'payment_intent.succeeded')

UNION ALL

-- Standalone membership payments do not create ledger entries. Exclude integration copies
-- by both durable links and external IDs (rebuilds can repair one before the other).
-- Two independently entered manual records have no shared identity and remain distinct.
SELECT m.person_id, mp.amount
FROM member_payment mp
JOIN member m ON m.id = mp.member_id
WHERE mp.status = 'completed'
  AND NOT EXISTS (
      SELECT 1 FROM zeffy_payment p
      WHERE p.member_payment_id = mp.id
         OR (mp.payment_method = 'zeffy' AND mp.transaction_ref = p.zeffy_payment_id)
  )
  AND NOT EXISTS (
      SELECT 1 FROM stripe_webhook_event s
      WHERE s.member_payment_id = mp.id
         OR (mp.payment_method = 'stripe' AND mp.transaction_ref = s.stripe_event_id)
  );

CREATE VIEW person_payment_total AS
SELECT person_id, CAST(SUM(amount) AS DECIMAL(19, 2)) AS total_paid
FROM person_payment_amount
WHERE person_id IS NOT NULL
GROUP BY person_id;
