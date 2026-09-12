# ProxyVault .MR Security Spec

## Encryption stack

1. **Scrypt KDF** — N=2^18 (262144), r=8, p=1 → 256-bit key  
2. **AES-256-GCM** — 96-bit nonce, 128-bit tag  
3. **HMAC-SHA256** — covers salt + metadata + ciphertext  

## Import flow

1. Parse salt / version / ciphertext / HMAC  
2. Verify HMAC before decrypt  
3. Derive key via Scrypt  
4. AES-GCM decrypt  
5. Verify magic `PROXYVAULT_MR_V2`  

Any failure aborts. No partial state.

## Recommendations

- Use a strong unique password (passphrase ≥ 4 words)  
- Treat `.MR` files like private keys  
- Night mode + local-only storage by default  
