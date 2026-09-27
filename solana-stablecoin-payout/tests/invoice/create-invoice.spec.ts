import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import {
  Keypair,
  LAMPORTS_PER_SOL,
  PublicKey,
  SystemProgram,
} from "@solana/web3.js";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";

describe("Create Invoice", () => {
  let environment: TestEnvironment;

  /**
   * Config và Mock USDC đã được tạo bởi test-environment.ts.
   * File này chỉ lấy lại để sử dụng.
   */
  before(async () => {
    environment = await getTestEnvironment();
  });

  it("allows a freelancer to create an invoice", async () => {
    const {
      provider,
      program,
      configPda,
      mockUsdc,
    } = environment;

    // ---------------------------------------------------------
    // PHẦN 1: TẠO FREELANCER
    // ---------------------------------------------------------

    /**
     * Freelancer là người tạo hóa đơn.
     *
     * Đây là Keypair mới nên ban đầu chưa có SOL.
     * Freelancer phải có SOL vì họ sẽ trả rent để tạo Invoice PDA.
     */
    const freelancer = Keypair.generate();

    const airdropSignature =
      await provider.connection.requestAirdrop(
        freelancer.publicKey,
        LAMPORTS_PER_SOL,
      );

    await provider.connection.confirmTransaction(
      airdropSignature,
      "confirmed",
    );

    // Client là bên phải thanh toán hóa đơn sau này.
    const client = mockUsdc.client;

    // ---------------------------------------------------------
    // PHẦN 2: CHUẨN BỊ DỮ LIỆU HÓA ĐƠN
    // ---------------------------------------------------------

    /**
     * Đây là mã hóa đơn của riêng freelancer này.
     *
     * Rust sử dụng u64 nên TypeScript sử dụng anchor.BN.
     */
    const invoiceId = new anchor.BN(1);

    /**
     * Mock USDC có 6 decimals.
     *
     * 25 USDC = 25 × 1,000,000
     *         = 25,000,000 base units
     */
    const amount = new anchor.BN(25_000_000);

    // ---------------------------------------------------------
    // PHẦN 3: TÍNH INVOICE PDA
    // ---------------------------------------------------------

    /**
     * Phải dùng đúng seed giống Rust:
     *
     * [
     *   b"invoice",
     *   freelancer public key,
     *   invoice_id dưới dạng 8 byte little-endian
     * ]
     */
    const [invoicePda, invoiceBump] =
      PublicKey.findProgramAddressSync(
        [
          Buffer.from("invoice"),
          freelancer.publicKey.toBuffer(),
          invoiceId.toArrayLike(Buffer, "le", 8),
        ],
        program.programId,
      );

    // ---------------------------------------------------------
    // PHẦN 4: GỌI CREATE_INVOICE
    // ---------------------------------------------------------

    await program.methods
      .createInvoice(
        invoiceId,
        client.publicKey,
        amount,
        environment.defaultRateExpiresAt.subn(1),
      )
      .accountsStrict({
        /**
         * Freelancer phải ký và trả SOL tạo Invoice PDA.
         */
        freelancer: freelancer.publicKey,

        /**
         * Config dùng để:
         * - kiểm tra paused;
         * - lấy accepted_mint.
         */
        config: configPda,
        rateSnapshot: environment.defaultRateSnapshot,

        /**
         * Account chứa dữ liệu hóa đơn sẽ được tạo.
         */
        invoice: invoicePda,

        /**
         * System Program thực hiện việc tạo account.
         */
        systemProgram: SystemProgram.programId,
      })
      .signers([freelancer])
      .rpc();

    // ---------------------------------------------------------
    // PHẦN 5: ĐỌC INVOICE TỪ BLOCKCHAIN
    // ---------------------------------------------------------

    const invoice =
      await program.account.invoice.fetch(invoicePda);

    // ---------------------------------------------------------
    // PHẦN 6: KIỂM TRA DỮ LIỆU
    // ---------------------------------------------------------

    expect(invoice.invoiceId.eq(invoiceId)).to.equal(true);

    expect(
      invoice.freelancer.equals(freelancer.publicKey),
    ).to.equal(true);

    expect(
      invoice.client.equals(client.publicKey),
    ).to.equal(true);

    expect(invoice.amount.eq(amount)).to.equal(true);

    /**
     * Mint không được client tự truyền vào.
     * Rust lấy mint trực tiếp từ Config.accepted_mint.
     */
    expect(
      invoice.mint.equals(mockUsdc.mint),
    ).to.equal(true);
    expect(invoice.rateSnapshot.equals(environment.defaultRateSnapshot)).to.equal(true);
    expect(invoice.expiresAt.eq(environment.defaultRateExpiresAt.subn(1))).to.equal(true);

    /**
     * Hóa đơn vừa tạo phải ở trạng thái Pending.
     *
     * Enum Rust được Anchor giải mã thành object TypeScript.
     */
    expect(invoice.status).to.deep.equal({
      pending: {},
    });

    /**
     * created_at phải chứa Unix timestamp hợp lệ.
     */
    expect(invoice.createdAt.toNumber()).to.be.greaterThan(0);

    /**
     * Chưa thanh toán nên paid_at vẫn là None.
     * Rust Option::None được giải mã thành null.
     */
    expect(invoice.paidAt).to.equal(null);

    expect(invoice.bump).to.equal(invoiceBump);

    console.log("Invoice PDA:", invoicePda.toBase58());
    console.log("Invoice ID:", invoice.invoiceId.toString());
    console.log(
      "Freelancer:",
      invoice.freelancer.toBase58(),
    );
    console.log("Client:", invoice.client.toBase58());
    console.log(
      "Amount in base units:",
      invoice.amount.toString(),
    );
    console.log("Mint:", invoice.mint.toBase58());
    console.log("Status: Pending");
  });
  it("rejects an invoice whose amount is zero", async () => {
    const {
        provider,
        program,
        payer,
        configPda,
        mockUsdc,
    } = environment;

    /**
     * Dùng invoice ID khác để không trùng hóa đơn đã tạo.
     *
     * Trong test này, payer đóng vai freelancer để không phải
     * tạo Keypair mới rồi airdrop thêm SOL.
     */
    const invoiceId = new anchor.BN(2);
    const invalidAmount = new anchor.BN(0);

    const [invoicePda] = PublicKey.findProgramAddressSync(
        [
        Buffer.from("invoice"),
        payer.publicKey.toBuffer(),
        invoiceId.toArrayLike(Buffer, "le", 8),
        ],
        program.programId,
    );

    let creationFailed = false;

    try {
        await program.methods
        .createInvoice(
            invoiceId,
            mockUsdc.client.publicKey,
            invalidAmount,
            environment.defaultRateExpiresAt.subn(1),
        )
        .accountsStrict({
            freelancer: payer.publicKey,
            config: configPda,
            rateSnapshot: environment.defaultRateSnapshot,
            invoice: invoicePda,
            systemProgram: SystemProgram.programId,
        })
        /**
         * Không cần .signers([payer]).
         *
         * Payer là provider wallet nên Anchor Provider
         * tự động ký transaction.
         */
        .rpc();
    } catch (error) {
        creationFailed = true;

        console.log(
        "Invoice with zero amount was correctly rejected",
        );

        if (error instanceof Error) {
        console.log(
            "Expected error:",
            error.message.split("\n")[0],
        );
        }
    }

    // Program bắt buộc phải từ chối amount = 0.
    expect(creationFailed).to.equal(true);

    /**
     * Kiểm tra tính atomic:
     *
     * Transaction thất bại nên Invoice PDA không được để lại
     * trên blockchain, dù instruction có khai báo `init`.
     */
    const invoiceAccountInfo =
        await provider.connection.getAccountInfo(invoicePda);

    expect(invoiceAccountInfo).to.equal(null);
  });
  it("rejects an invoice whose client is the default public key", async () => {
  const {
    provider,
    program,
    payer,
    configPda,
  } = environment;

  // Dùng ID mới để tạo một Invoice PDA chưa tồn tại.
  const invoiceId = new anchor.BN(3);

  // Số tiền hợp lệ: 25 Mock USDC.
  const amount = new anchor.BN(25_000_000);

  /**
   * Đây tương ứng với Pubkey::default() bên Rust.
   *
   * Nó không đại diện cho một client thật.
   */
  const invalidClient = PublicKey.default;

  const [invoicePda] = PublicKey.findProgramAddressSync(
    [
      Buffer.from("invoice"),
      payer.publicKey.toBuffer(),
      invoiceId.toArrayLike(Buffer, "le", 8),
    ],
    program.programId,
  );

  let creationFailed = false;

  try {
    await program.methods
      .createInvoice(
        invoiceId,
        invalidClient,
        amount,
        environment.defaultRateExpiresAt.subn(1),
      )
      .accountsStrict({
        freelancer: payer.publicKey,
        config: configPda,
        rateSnapshot: environment.defaultRateSnapshot,
        invoice: invoicePda,
        systemProgram: SystemProgram.programId,
      })
      .rpc();
  } catch (error) {
    creationFailed = true;

    console.log(
      "Invoice with default client was correctly rejected",
    );

    if (error instanceof Error) {
      console.log(
        "Expected error:",
        error.message.split("\n")[0],
      );
    }
  }

  expect(creationFailed).to.equal(true);

  /**
   * Transaction thất bại hoàn toàn nên Invoice PDA
   * không được để lại trên blockchain.
   */
  const invoiceAccountInfo =
    await provider.connection.getAccountInfo(invoicePda);

  expect(invoiceAccountInfo).to.equal(null);
  });
  it("rejects duplicate invoice ID for the same freelancer", async () => {
    const {
        provider,
        program,
        configPda,
        mockUsdc,
    } = environment;

    // Tạo một freelancer riêng cho test này.
    const freelancer = Keypair.generate();

    const airdropSignature =
        await provider.connection.requestAirdrop(
        freelancer.publicKey,
        LAMPORTS_PER_SOL,
        );

    await provider.connection.confirmTransaction(
        airdropSignature,
        "confirmed",
    );

    const invoiceId = new anchor.BN(100);
    const originalAmount = new anchor.BN(10_000_000);

    const [invoicePda] = PublicKey.findProgramAddressSync(
        [
        Buffer.from("invoice"),
        freelancer.publicKey.toBuffer(),
        invoiceId.toArrayLike(Buffer, "le", 8),
        ],
        program.programId,
    );

    // ---------------------------------------------------------
    // LẦN 1: TẠO HÓA ĐƠN THÀNH CÔNG
    // ---------------------------------------------------------

    await program.methods
        .createInvoice(
        invoiceId,
        mockUsdc.client.publicKey,
        originalAmount,
        environment.defaultRateExpiresAt.subn(1),
        )
        .accountsStrict({
        freelancer: freelancer.publicKey,
        config: configPda,
        rateSnapshot: environment.defaultRateSnapshot,
        invoice: invoicePda,
        systemProgram: SystemProgram.programId,
        })
        .signers([freelancer])
        .rpc();

    const invoiceBefore =
        await program.account.invoice.fetch(invoicePda);

    expect(invoiceBefore.amount.eq(originalAmount)).to.equal(true);

    // ---------------------------------------------------------
    // LẦN 2: CỐ TẠO LẠI CÙNG PDA
    // ---------------------------------------------------------

    const differentClient = Keypair.generate();
    const differentAmount = new anchor.BN(99_000_000);

    let duplicateCreationFailed = false;

    try {
        await program.methods
        .createInvoice(
            invoiceId,
            differentClient.publicKey,
            differentAmount,
            environment.defaultRateExpiresAt.subn(1),
        )
        .accountsStrict({
            freelancer: freelancer.publicKey,
            config: configPda,
            rateSnapshot: environment.defaultRateSnapshot,

            /**
             * Cùng freelancer + cùng invoice ID
             * nên đây vẫn là cùng Invoice PDA.
             */
            invoice: invoicePda,

            systemProgram: SystemProgram.programId,
        })
        .signers([freelancer])
        .rpc();
    } catch (error) {
        duplicateCreationFailed = true;

        console.log(
        "Duplicate invoice was correctly rejected",
        );

        if (error instanceof Error) {
        console.log(
            "Expected error:",
            error.message.split("\n")[0],
        );
        }
    }

    expect(duplicateCreationFailed).to.equal(true);

    // ---------------------------------------------------------
    // KIỂM TRA HÓA ĐƠN CŨ KHÔNG BỊ THAY ĐỔI
    // ---------------------------------------------------------

    const invoiceAfter =
        await program.account.invoice.fetch(invoicePda);

    expect(
        invoiceAfter.client.equals(mockUsdc.client.publicKey),
    ).to.equal(true);

    expect(
        invoiceAfter.amount.eq(originalAmount),
    ).to.equal(true);

    expect(invoiceAfter.status).to.deep.equal({
        pending: {},
    });
  });
  it("rejects an Invoice expiry later than its locked RateSnapshot", async () => {
    const { program, payer, configPda, mockUsdc } = environment;
    const invoiceId = new anchor.BN(150);
    const [invoicePda] = PublicKey.findProgramAddressSync(
      [
        Buffer.from("invoice"),
        payer.publicKey.toBuffer(),
        invoiceId.toArrayLike(Buffer, "le", 8),
      ],
      program.programId,
    );

    let message = "";
    try {
      await program.methods
        .createInvoice(
          invoiceId,
          mockUsdc.client.publicKey,
          new anchor.BN(10_000_000),
          environment.defaultRateExpiresAt.addn(1),
        )
        .accountsStrict({
          freelancer: payer.publicKey,
          config: configPda,
          rateSnapshot: environment.defaultRateSnapshot,
          invoice: invoicePda,
          systemProgram: SystemProgram.programId,
        })
        .rpc();
    } catch (error) {
      message = String(error);
    }
    expect(message).to.include("InvalidInvoiceExpiration");
    expect(await environment.provider.connection.getAccountInfo(invoicePda)).to.equal(
      null,
    );
  });
  it("rejects invoice creation while the system is paused", async () => {
    const {
        provider,
        program,
        payer,
        configPda,
        mockUsdc,
        treasuryAuthority,
        rateAuthority,
        oracleAuthority,
        maxRateAgeSeconds,
    } = environment;

    const invoiceId = new anchor.BN(200);
    const amount = new anchor.BN(25_000_000);

    const [invoicePda] = PublicKey.findProgramAddressSync(
        [
        Buffer.from("invoice"),
        payer.publicKey.toBuffer(),
        invoiceId.toArrayLike(Buffer, "le", 8),
        ],
        program.programId,
    );

    // ---------------------------------------------------------
    // PHẦN 1: ADMIN TẠM DỪNG HỆ THỐNG
    // ---------------------------------------------------------

    await program.methods
        .updateConfig(
        treasuryAuthority.publicKey,
        rateAuthority.publicKey,
        oracleAuthority.publicKey,
        maxRateAgeSeconds,
        true,
        )
        .accountsStrict({
        admin: payer.publicKey,
        config: configPda,
        acceptedMint: mockUsdc.mint,
        })
        .rpc();

    const pausedConfig =
        await program.account.config.fetch(configPda);

    expect(pausedConfig.paused).to.equal(true);

    let creationFailed = false;

    try {
        // -------------------------------------------------------
        // PHẦN 2: THỬ TẠO INVOICE KHI PAUSED
        // -------------------------------------------------------

        try {
        await program.methods
            .createInvoice(
            invoiceId,
            mockUsdc.client.publicKey,
            amount,
            environment.defaultRateExpiresAt.subn(1),
            )
            .accountsStrict({
            freelancer: payer.publicKey,
            config: configPda,
            rateSnapshot: environment.defaultRateSnapshot,
            invoice: invoicePda,
            systemProgram: SystemProgram.programId,
            })
            .rpc();
        } catch (error) {
        creationFailed = true;

        console.log(
            "Invoice creation while paused was correctly rejected",
        );

        if (error instanceof Error) {
            console.log(
            "Expected error:",
            error.message.split("\n")[0],
            );
        }
        }

        expect(creationFailed).to.equal(true);

        /**
         * Config được kiểm tra trước khi Invoice PDA được tạo.
         * Vì vậy account Invoice không được xuất hiện.
         */
        const invoiceAccountInfo =
        await provider.connection.getAccountInfo(invoicePda);

        expect(invoiceAccountInfo).to.equal(null);
    } finally {
        // -------------------------------------------------------
        // PHẦN 3: LUÔN MỞ LẠI HỆ THỐNG
        // -------------------------------------------------------

        /**
         * finally luôn chạy, kể cả khi assertion phía trên thất bại.
         *
         * Điều này ngăn test làm Config mắc kẹt ở paused = true
         * và gây hỏng những test chạy sau.
         */
        await program.methods
        .updateConfig(
            treasuryAuthority.publicKey,
            rateAuthority.publicKey,
            oracleAuthority.publicKey,
            maxRateAgeSeconds,
            false,
        )
        .accountsStrict({
            admin: payer.publicKey,
            config: configPda,
            acceptedMint: mockUsdc.mint,
        })
        .rpc();
    }

    const restoredConfig =
        await program.account.config.fetch(configPda);

    expect(restoredConfig.paused).to.equal(false);
    });
});
