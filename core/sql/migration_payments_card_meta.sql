-- Migration: Add card_brand and card_last4 metadata columns to payments table
-- Phase B: payments card metadata for transaction records (PCI compliant, no PAN or CVV stored)

ALTER TABLE payments ADD COLUMN IF NOT EXISTS card_brand VARCHAR(20);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS card_last4 CHAR(4);
