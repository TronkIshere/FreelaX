# Vietnamese UI glossary (2026-11-05)

Decided and applied by engineering (AI-assisted); **not approved by a native Vietnamese speaker**. The frontend guard test
`frontend/src/viGlossary.test.ts` fails if the retired role words come back.

## Terms in use
| Concept | Vietnamese text | Notes |
|---|---|---|
| Role of the payer | **Client** | Never "khách", "khách hàng". The same word as the sign-up option and the role label |
| Role of the worker | **Freelancer** | Never "người làm", "người thực hiện" |
| Operator role | **Admin** | The Admin who decides a dispute; the arbiter transaction is "Thực thi quyết định tranh chấp" |
| Contract, job | hợp đồng, công việc | |
| Milestone | Milestone | Kept as the product's technical noun |
| Escrow | ký quỹ ("Ký quỹ Avalanche", "Ký quỹ Solana") | "escrow" no longer appears in Vietnamese text |
| Fund the escrow | nạp tiền (ký quỹ) | "funding" no longer appears in Vietnamese text. Wallet button: "Nạp tiền ký quỹ bằng ví" |
| Release | giải ngân | "release" no longer appears in Vietnamese text |
| Refund | hoàn tiền | |
| Dispute | tranh chấp | |
| Mutual settlement of a Disputed escrow (R12) | tất toán tranh chấp theo thỏa thuận | Separate from the Admin decision and from the arbiter step |
| Review window | hạn nghiệm thu | Replaces "Hạn review" (3 screens). The Solana escrow summary still says "Hạn duyệt" (see item 4 below) |
| On the blockchain | trên chuỗi | "on-chain" no longer appears in Vietnamese text |
| Simulation scope | mô phỏng; "USDC mô phỏng"; "Testnet USDC"; prefix "Demonstration only" | The scope document prefers these English terms; they are kept |
| Transaction states | Đã chốt (FINALIZED), Đã vào khối (CONFIRMED), Bị hoàn tác (REVERTED), Thất bại (FAILED) | See `avalanchePresentation.ts` |

The transformation was applied to every Vietnamese string literal (a literal that contains at least one Vietnamese letter) in `frontend/src` and in the
Java sources of `marketplace-backend`, `payment-backend` and `misa-backend`; English-only literals, identifiers and wire values were not touched.

## Left for a native speaker or the product owner to confirm
1. Whether **Client / Freelancer** (English role names inside Vietnamese sentences) is the wanted style, or a plain-language pair should be chosen
   for the payment-journey screens; the previous mix was resolved in favour of the role names used by sign-up and the scope document.
2. "Bị hoàn tác" for REVERTED may read as a user action; "Bị chuỗi từ chối" is an alternative.
3. "Đã chốt" for FINALIZED and "Đã vào khối" for CONFIRMED (technical terms for end users).
4. "nghiệm thu" (acceptance) versus "duyệt" (approve) for the client's review step; only the window label was unified.
5. Grammar of sentences produced by the mechanical term replacement (for example "Nạp tiền hợp đồng" style headings, "Hoàn tiền" as a label next to a verb).
   The strings were read after the change, but a fluent reader should go through the dispute, payout, finance and wallet screens once.
6. "Demonstration only" stays English by the scope document's terminology; whether it should also carry a Vietnamese gloss is an editorial choice.
7. Strings that exist only in English (no Vietnamese letter) were not translated by this pass: for example some one-word labels and the signed wallet-link
   message, which is English on purpose because the user signs it in a wallet.
