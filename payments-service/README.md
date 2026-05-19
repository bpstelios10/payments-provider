# Payments Service

This service will be the front tier of the PSP, accepting the requests for payments from merchants

## API

| Step      | Who calls it?                    | Visible to merchant? |
|-----------|----------------------------------|----------------------|
| Create    | Webshop backend                  | ✅ Yes                |
| Authorize | PSP internally                   | ❌ No (but possible)  |
| Capture   | PSP internally OR merchant later | ⚠️ Sometimes         |

## Real-world flow

1. Webshop → PSP (server-side)

   When user clicks “Pay”:
    ```
    POST /payments   (or PaymentIntent / Order)
   {
   "id": "pay_1",
   "amount": 1000,
   "currency": "EUR",
   "merchant_id": "m_1",
   "idempotency_key": "abc-123"
   }
    ```
   PSP creates a payment object and returns something like payment_id, client_secret (important for frontend)

2. PSP Frontend → PSP (direct, secure)

   The checkout form: collects card details and sends them directly to the PSP
   ⚠️ The webshop server never sees raw card data. This is critical for PCI compliance.

3. “Confirm payment” (frontend action)

   Frontend calls PSP:
   confirm payment (e.g. confirmCardPayment)
   This is the moment the real processing starts.

4. PSP internal flow (hidden from merchant)

   Now the PSP does:

   a. Authorization Send request via Visa / Mastercard and Issuing bank approves/declines

   b. (Optional) 3D Secure: Redirect user to bank auth page if needed

   c. Capture, either immediately (most ecommerce) or later (hotels, shipping, etc.)

## Event Flow

```
createPayment()
  → Payment(INITIATED)
  → outbox: PAYMENT_INITIATED

executePayment()
  → Payment(PROCESSING)                ← internal lock, no event
  → gateway call (idempotent)
      ├── success → Payment(CAPTURED)
      │             outbox: PAYMENT_CAPTURED
      │
      └── failure → Payment(FAILED)
                    outbox: PAYMENT_FAILED
```

> If the gateway succeeds but the status update fails, the payment stays PROCESSING.
> The client retries `executePayment` — the gateway call is idempotent so only the
> status update and outbox insert run. Both are committed atomically in one transaction.

