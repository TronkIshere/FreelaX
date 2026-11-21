-- Avalanche rail withholding certificate (owner decision 2026-11-21: a MISA certificate is created, simulated, when an Avalanche
-- escrow is released; the platform withholds on behalf of the individual provider under Decree 117/2025: PIT 2% + VAT 5%).
-- Additive and forward-only: new NULLABLE columns only. Existing rows keep NULL (legacy configured PIT rate, no VAT).
ALTER TABLE tax_certificate_records
  ADD COLUMN tax_scheme VARCHAR(40) NULL,
  ADD COLUMN vat_withheld_vnd DECIMAL(20,0) NULL;
