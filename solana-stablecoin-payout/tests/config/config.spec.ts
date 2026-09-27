import * as anchor from "@anchor-lang/core";
import { expect } from "chai";
import { Keypair } from "@solana/web3.js";
import { getAccount, getMint } from "@solana/spl-token";

import {
  getTestEnvironment,
  type TestEnvironment,
} from "../helpers/test-environment";
import { MOCK_USDC_DECIMALS } from "../helpers/mock-usdc";

describe("Config PDA", () => {
  let environment: TestEnvironment;

  /**
   * Lấy môi trường dùng chung.
   *
   * Config không còn được initialize trực tiếp trong file này.
   * test-environment.ts chịu trách nhiệm khởi tạo nó đúng một lần.
   */
  before(async () => {
    environment = await getTestEnvironment();
  });

  it("creates the Mock USDC fixture", async () => {
    const { provider, mockUsdc } = environment;

    const mintData = await getMint(
      provider.connection,
      mockUsdc.mint,
    );

    const clientTokenAccount = await getAccount(
      provider.connection,
      mockUsdc.clientAta,
    );

    expect(mintData.decimals).to.equal(MOCK_USDC_DECIMALS);

    expect(
      mintData.mintAuthority?.equals(
        mockUsdc.mintAuthority.publicKey,
      ),
    ).to.equal(true);

    expect(
      clientTokenAccount.owner.equals(mockUsdc.client.publicKey),
    ).to.equal(true);

    expect(
      clientTokenAccount.mint.equals(mockUsdc.mint),
    ).to.equal(true);

    expect(clientTokenAccount.amount).to.equal(mockUsdc.mintAmount);
  });

  it("initializes Config PDA with the correct data", async () => {
    const {
      program,
      payer,
      mockUsdc,
      configPda,
      configBump,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
      mockOnrampAuthority,
      maxMockOnrampAmount,
    } = environment;

    const config = await program.account.config.fetch(configPda);

    expect(config.admin.equals(payer.publicKey)).to.equal(true);

    expect(
      config.acceptedMint.equals(mockUsdc.mint),
    ).to.equal(true);

    expect(
      config.treasuryAuthority.equals(
        treasuryAuthority.publicKey,
      ),
    ).to.equal(true);

    expect(
      config.rateAuthority.equals(rateAuthority.publicKey),
    ).to.equal(true);

    expect(
      config.oracleAuthority.equals(
        oracleAuthority.publicKey,
      ),
    ).to.equal(true);

    expect(config.maxRateAgeSeconds.eq(maxRateAgeSeconds)).to.equal(true);

    expect(
      config.mockOnrampAuthority.equals(mockOnrampAuthority.publicKey),
    ).to.equal(true);
    expect(config.maxMockOnrampAmount.eq(maxMockOnrampAmount)).to.equal(true);
    expect(config.mockOnrampEnabled).to.equal(true);

    expect(config.paused).to.equal(false);
    expect(config.bump).to.equal(configBump);

    console.log("Config PDA:", configPda.toBase58());
  });

  it("rejects duplicate Config initialization", async () => {
    const {
      program,
      payer,
      mockUsdc,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
      configPda,
      programData,
    } = environment;

    let duplicateInitializationFailed = false;

    try {
      await program.methods
        .initializeConfig(
          treasuryAuthority.publicKey,
          rateAuthority.publicKey,
          oracleAuthority.publicKey,
          maxRateAgeSeconds,
        )
        .accountsStrict({
          admin: payer.publicKey,
          config: configPda,
          acceptedMint: mockUsdc.mint,
          program: program.programId,
          programData,
          systemProgram: anchor.web3.SystemProgram.programId,
        })
        .rpc();
    } catch {
      duplicateInitializationFailed = true;
    }

    expect(duplicateInitializationFailed).to.equal(true);
  });

  it("allows the configured admin to update Config", async () => {
    const {
      program,
      payer,
      mockUsdc,
      configPda,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
    } = environment;

    const newTreasuryAuthority = Keypair.generate();
    const newRateAuthority = Keypair.generate();
    const newOracleAuthority = Keypair.generate();
    const newMaxRateAgeSeconds = new anchor.BN(600);

    await program.methods
      .updateConfig(
        newTreasuryAuthority.publicKey,
        newRateAuthority.publicKey,
        newOracleAuthority.publicKey,
        newMaxRateAgeSeconds,
        true,
      )
      .accountsStrict({
        admin: payer.publicKey,
        config: configPda,
        acceptedMint: mockUsdc.mint,
      })
      .rpc();

    try {
      const updatedConfig =
        await program.account.config.fetch(configPda);

      expect(
        updatedConfig.admin.equals(payer.publicKey),
      ).to.equal(true);

      expect(
        updatedConfig.acceptedMint.equals(mockUsdc.mint),
      ).to.equal(true);

      expect(
        updatedConfig.treasuryAuthority.equals(
          newTreasuryAuthority.publicKey,
        ),
      ).to.equal(true);

      expect(
        updatedConfig.rateAuthority.equals(
          newRateAuthority.publicKey,
        ),
      ).to.equal(true);

      expect(
        updatedConfig.oracleAuthority.equals(
          newOracleAuthority.publicKey,
        ),
      ).to.equal(true);

      expect(
        updatedConfig.maxRateAgeSeconds.eq(newMaxRateAgeSeconds),
      ).to.equal(true);

      expect(updatedConfig.paused).to.equal(true);
    } finally {
      /**
       * Trả Config về trạng thái ban đầu.
       *
       * Invoice test chạy sau đó cần hệ thống không bị pause.
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
  });

  it("rejects invalid rate configuration without changing Config", async () => {
    const {
      program,
      payer,
      mockUsdc,
      configPda,
      treasuryAuthority,
      rateAuthority,
      oracleAuthority,
      maxRateAgeSeconds,
    } = environment;

    const configBefore = await program.account.config.fetch(configPda);
    const invalidUpdates = [
      {
        rateAuthority: anchor.web3.PublicKey.default,
        oracleAuthority: oracleAuthority.publicKey,
        maxRateAgeSeconds,
      },
      {
        rateAuthority: oracleAuthority.publicKey,
        oracleAuthority: oracleAuthority.publicKey,
        maxRateAgeSeconds,
      },
      {
        rateAuthority: rateAuthority.publicKey,
        oracleAuthority: oracleAuthority.publicKey,
        maxRateAgeSeconds: new anchor.BN(0),
      },
    ];

    for (const invalidUpdate of invalidUpdates) {
      let updateFailed = false;

      try {
        await program.methods
          .updateConfig(
            treasuryAuthority.publicKey,
            invalidUpdate.rateAuthority,
            invalidUpdate.oracleAuthority,
            invalidUpdate.maxRateAgeSeconds,
            false,
          )
          .accountsStrict({
            admin: payer.publicKey,
            config: configPda,
            acceptedMint: mockUsdc.mint,
          })
          .rpc();
      } catch {
        updateFailed = true;
      }

      expect(updateFailed).to.equal(true);
    }

    const configAfter = await program.account.config.fetch(configPda);

    expect(configAfter.admin.equals(configBefore.admin)).to.equal(true);
    expect(configAfter.acceptedMint.equals(configBefore.acceptedMint)).to.equal(
      true,
    );
    expect(
      configAfter.treasuryAuthority.equals(configBefore.treasuryAuthority),
    ).to.equal(true);
    expect(configAfter.rateAuthority.equals(configBefore.rateAuthority)).to.equal(
      true,
    );
    expect(
      configAfter.oracleAuthority.equals(configBefore.oracleAuthority),
    ).to.equal(true);
    expect(
      configAfter.maxRateAgeSeconds.eq(configBefore.maxRateAgeSeconds),
    ).to.equal(true);
    expect(configAfter.paused).to.equal(configBefore.paused);
    expect(configAfter.bump).to.equal(configBefore.bump);
  });

  it("rejects an update signed by a fake admin", async () => {
    const {
      program,
      mockUsdc,
      configPda,
    } = environment;

    const configBefore =
      await program.account.config.fetch(configPda);

    const fakeAdmin = Keypair.generate();
    const maliciousTreasury = Keypair.generate();
    const maliciousRateAuthority = Keypair.generate();
    const maliciousOracle = Keypair.generate();

    let unauthorizedUpdateFailed = false;

    try {
      await program.methods
        .updateConfig(
          maliciousTreasury.publicKey,
          maliciousRateAuthority.publicKey,
          maliciousOracle.publicKey,
          new anchor.BN(600),
          false,
        )
        .accountsStrict({
          // Đây mới thực sự là account giả mạo quyền admin.
          admin: fakeAdmin.publicKey,
          config: configPda,
          acceptedMint: mockUsdc.mint,
        })
        // Fake admin phải ký vì Rust yêu cầu Signer<'info>.
        .signers([fakeAdmin])
        .rpc();
    } catch {
      unauthorizedUpdateFailed = true;
    }

    expect(unauthorizedUpdateFailed).to.equal(true);

    // Transaction thất bại nên Config phải giữ nguyên.
    const configAfter =
      await program.account.config.fetch(configPda);

    expect(
      configAfter.admin.equals(configBefore.admin),
    ).to.equal(true);

    expect(
      configAfter.acceptedMint.equals(configBefore.acceptedMint),
    ).to.equal(true);

    expect(
      configAfter.treasuryAuthority.equals(
        configBefore.treasuryAuthority,
      ),
    ).to.equal(true);

    expect(
      configAfter.rateAuthority.equals(
        configBefore.rateAuthority,
      ),
    ).to.equal(true);

    expect(
      configAfter.oracleAuthority.equals(
        configBefore.oracleAuthority,
      ),
    ).to.equal(true);

    expect(
      configAfter.maxRateAgeSeconds.eq(
        configBefore.maxRateAgeSeconds,
      ),
    ).to.equal(true);

    expect(configAfter.paused).to.equal(configBefore.paused);
    expect(configAfter.bump).to.equal(configBefore.bump);
  });
});
