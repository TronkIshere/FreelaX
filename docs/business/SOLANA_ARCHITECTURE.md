# Solana trong FreelaX

## Vì sao có Solana

Client và Freelancer cần một nơi **giữ tiền trung lập**: tiền đã được nộp thật, Freelancer yên tâm làm việc, và không bên nào tự ý rút được. FreelaX dùng một **chương trình (smart contract) trên Solana** làm nơi giữ đó. Tiền được giữ dưới dạng USDC (stablecoin, 1 USDC ≈ 1 USD). Ai cũng kiểm tra được tiền đang nằm ở đâu, và chỉ đúng quy tắc mới mở được.

Solana **chỉ giữ và chuyển USDC**. Phần nhận USD và chi VND do đối tác ngoài chain làm; Solana không xác nhận tiền đã vào ngân hàng.

Trên môi trường local: dùng **Mock USDC** trên **local validator** (`127.0.0.1:9123`); program ID `2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb`. Không có tiền thật, không dùng devnet.

## Bốn khái niệm cần biết

| Khái niệm | Hiểu đơn giản | Trong FreelaX |
| --- | --- | --- |
| **Ví** (wallet) | Tài khoản của người dùng trên Solana, ký giao dịch bằng khóa riêng | Local: mỗi tài khoản được cấp **ví demo tự động** khi đăng nhập, backend giữ khóa (mã hóa) và ký thay. Ngoài localnet: người dùng liên kết ví trình duyệt (Phantom…) bằng cách ký thông điệp xác minh |
| **ATA** | "Ngăn" chứa USDC của một ví | USDC on-ramp vào ATA của Client; release vào ATA của Freelancer |
| **Vault / Escrow PDA** | Két sắt do chương trình quản lý, riêng cho từng Milestone | Giữ USDC của Job từ lúc Client ký quỹ cho tới khi release hoặc refund |
| **WithdrawalRecord** | Biên nhận rút USDC sang treasury để đổi VND | Gắn với withdrawal ID và tỷ giá đã khóa của flow |

## Luồng trên chain

```text
Ví Client ──(mock_onramp: đối tác chuyển USDC sau khi nhận USD)──► ATA Client
ATA Client ──(fund_milestone_escrow: Client + FreelaX cùng ký)───► Vault của Milestone
Vault ──(settle: Client duyệt / hết hạn review / Admin quyết định)──► ATA Freelancer
Vault ──(refund_mutual: hai bên cùng ký / Admin hoàn)─────────────► ATA Client
ATA Freelancer ──(request_offramp: Freelancer ký)──────────────────► Treasury + WithdrawalRecord
                                                         đối tác chi VND ngoài chain
```

## Chương trình kiểm tra gì

- **Ký quỹ:** đúng mint USDC, đúng số tiền, Client không trùng Freelancer; lưu hạn ký quỹ, hạn giao, thời hạn review 72 giờ (cờ `high_value_review_grace=false` cho luồng unified), số lần sửa. Mỗi Milestone chỉ có một vault, không ký quỹ được hai lần.
- **Bàn giao:** Freelancer ký kèm mã băm bằng chứng; hệ thống tính hạn review từ lúc này.
- **Gia hạn:** Freelancer xin một lần, tối đa hạn gốc + 7 ngày, trước hạn gốc; chỉ có hiệu lực khi Client duyệt.
- **Release:** Client duyệt, **hoặc** bất kỳ ai gọi sau khi hết hạn review (đây là "tự duyệt"). Đang tranh chấp thì chỉ Admin được quyết định.
- **Refund:** cần cả hai bên ký (hoàn theo thỏa thuận), hoặc Admin quyết định khi có tranh chấp.
- **Chốt một lần:** release và refund chuyển **toàn bộ** số tiền trong vault vào đúng ATA của người nhận, trong cùng lệnh ghi trạng thái cuối. Nhờ vậy chỉ cần thấy "trạng thái cuối + vault bằng 0 + đúng người nhận" là biết người nhận đã có tiền, kể cả khi lịch sử giao dịch bị cắt.
- **Rút tiền:** dùng tỷ giá do rate authority công bố và còn hạn; số VND được tính sẵn vào WithdrawalRecord. Phí 3% và thuế 10% được đối tác trừ ngoài chain khi chi VND.

## Các thành phần

| Thành phần | Vai trò |
| --- | --- |
| Frontend | Đưa giao dịch đi ký (local: qua ví demo tự động; ngoài localnet: ví trình duyệt); không tự kết luận tiền đã chuyển |
| Marketplace | Quyết định nghiệp vụ, kiểm tra quyền, đối chiếu dữ liệu chain trước khi ghi một bước là xác nhận |
| Solana Gateway (`solana-integration`) | Dựng và gửi giao dịch, đọc tài khoản và PDA qua RPC; không phải nơi quyết định nghiệp vụ |
| Anchor program (`solana-stablecoin-payout`) | Thực thi quy tắc giữ và chuyển USDC |

Trình duyệt chỉ gọi Marketplace. Gateway và RPC nằm phía sau.

## Hồi phục khi có sự cố

- **RPC mất kết nối:** Admin thấy `UNKNOWN`, các bước tiền bị chặn; khi RPC trở lại thì tiếp tục với cùng giao dịch đã ký.
- **Giao dịch on-ramp bị rơi:** sau 300 giây, nếu chưa có receipt thì gửi lại với cùng purchase ID. Receipt PDA bảo đảm không thể cấp USDC hai lần.
- **Phiên ký hết hạn** (quá 120 giây): giao dịch cũ không được gửi; người dùng chuẩn bị lại với cùng withdrawal ID.

## Giới hạn local

Ví demo tự động chỉ bật ở profile `dev` + `localnet`: khóa riêng lưu mã hóa trong bảng `wallets` (khóa mã hóa dẫn xuất từ JWT secret, đổi secret thì không đọc lại được ví), và endpoint ký thay ký mọi giao dịch có ví của tài khoản làm signer. Đây chưa phải cơ chế cấp phát, bảo vệ và khôi phục khóa cho người dùng thật. Ví cũ (liên kết bằng tiện ích) chưa ký quỹ được tự chuyển sang ví demo; USDC on-ramp được cấp lại cho ví mới. Validator test chỉ có một node: không nên kill nó khi đang chạy (có thể làm chain đứng). Muốn giả lập RPC sập thì dùng `solana-stablecoin-payout/scripts/local-rpc-proxy.sh`.
