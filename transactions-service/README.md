# Transactions Service

Transaction = append-only event record (immutable)

We keep transactions as immutable financial records. So no changes and also keeping details like amount, etc.

## Create transaction (immutable event)

```
POST /transactions
{
    "payment_id": "pay_123",
    "type": "authorization",
    "status": "pending",
    "provider": "mock-bank",
    "amount": 1000,
    "currency": "EUR",
    "merchant_id": "m_1",
    "provider_reference": "visa_abc123"
}
Response:
{
    "transaction_id": "tx_1",
    "status": "pending"
}
```
