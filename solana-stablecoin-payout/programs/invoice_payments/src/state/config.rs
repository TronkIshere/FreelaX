use anchor_lang::prelude::*;

#[account]
#[derive(InitSpace)]
pub struct Config {
    // nguoi co quyen cap nhat cau hinh he thong
    pub admin: Pubkey,

    // spl token mint duy nhat duoc chap nhat thanh toan
    pub accepted_mint: Pubkey,

    // vi nhan token khi freelancer yeu cau off-ramp
    pub treasury_authority: Pubkey,

    // vi ky va cong bo snapshot ty gia
    pub rate_authority: Pubkey,

    // vi duoc xac nhan ket qua off-ramp
    pub oracle_authority: Pubkey,

    // tuoi toi da cua snapshot ty gia duoc phep su dung
    pub max_rate_age_seconds: i64,

    // khi true => cac nghiep vu thanh toan bi tam dung
    pub paused: bool,

    // dung de xac minh lai config PDA
    pub bump: u8,
}
