import { expect } from "chai";
import { network } from "hardhat";
import { createHash } from "node:crypto";

// Create a local blockchain connection for running contract tests.
// Uses the current Hardhat 3 network API.
const { ethers } = await network.create();

describe("LoanNote", function () {

  // Deploy a fresh contract before each test.
  async function deployLoanNote() {
    const [owner, otherAccount] = await ethers.getSigners();

    const loanNote = await ethers.deployContract("LoanNote");
    await loanNote.waitForDeployment();

    // Example mortgage information used throughout the tests.
    const loan = {
      applicationHash: ethers.id("application-123"),
      tokenOwner: owner.address,
      principal: 25000000n, // €250,000 represented in cents.
      aprBasisPoints: 450n, // 4.50% APR.
      termMonths: 360n, // 30-year mortgage.
      borrowerPublicId: ethers.id("borrower-123"),
      auditHash: `0x${createHash("sha256")
        .update("example lending evidence")
        .digest("hex")}`,
    };

    return { loanNote, owner, otherAccount, loan };
  }

  it("should allow the contract owner to tokenize a mortgage", async function () {
    const { loanNote, loan } = await deployLoanNote();

    // Mint the mortgage token using the authorised account.
    await loanNote.tokenizeLoan(
      loan.applicationHash,
      loan.tokenOwner,
      loan.principal,
      loan.aprBasisPoints,
      loan.termMonths,
      loan.borrowerPublicId,
      loan.auditHash
    );

    // The first mortgage should receive token ID 1.
    expect(await loanNote.ownerOf(1n)).to.equal(loan.tokenOwner);
  });

  it("should store the correct mortgage information", async function () {
    const { loanNote, loan } = await deployLoanNote();

    await loanNote.tokenizeLoan(
      loan.applicationHash,
      loan.tokenOwner,
      loan.principal,
      loan.aprBasisPoints,
      loan.termMonths,
      loan.borrowerPublicId,
      loan.auditHash
    );

    // Retrieve the loan record stored on the blockchain.
    const storedLoan = await loanNote.loans(1n);

    expect(storedLoan.principal).to.equal(loan.principal);
    expect(storedLoan.aprBasisPoints).to.equal(loan.aprBasisPoints);
    expect(storedLoan.termMonths).to.equal(loan.termMonths);
    expect(storedLoan.borrowerPublicId).to.equal(loan.borrowerPublicId);
    expect(storedLoan.auditHash).to.equal(loan.auditHash);
    expect(storedLoan.issuedAt).to.be.greaterThan(0n);
  });

  it("should prevent duplicate mortgage tokenization", async function () {
    const { loanNote, loan } = await deployLoanNote();

    // Tokenize the application for the first time.
    await loanNote.tokenizeLoan(
      loan.applicationHash,
      loan.tokenOwner,
      loan.principal,
      loan.aprBasisPoints,
      loan.termMonths,
      loan.borrowerPublicId,
      loan.auditHash
    );

    // Attempting to tokenize the same application must fail.
    await expect(
      loanNote.tokenizeLoan(
        loan.applicationHash,
        loan.tokenOwner,
        loan.principal,
        loan.aprBasisPoints,
        loan.termMonths,
        loan.borrowerPublicId,
        loan.auditHash
      )
    ).to.be.revertedWith("Application already tokenized");
  });

  it("should reject tokenization by an unauthorized account", async function () {
    const { loanNote, otherAccount, loan } = await deployLoanNote();

    // A wallet other than the contract owner cannot mint loans.
    await expect(
      loanNote.connect(otherAccount).tokenizeLoan(
        loan.applicationHash,
        loan.tokenOwner,
        loan.principal,
        loan.aprBasisPoints,
        loan.termMonths,
        loan.borrowerPublicId,
        loan.auditHash
      )
    ).to.be.revertedWithCustomError(
      loanNote,
      "OwnableUnauthorizedAccount"
    );
  });

  it("should reject a mortgage with zero principal", async function () {
    const { loanNote, loan } = await deployLoanNote();

    // A mortgage must have a positive principal amount.
    await expect(
      loanNote.tokenizeLoan(
        loan.applicationHash,
        loan.tokenOwner,
        0n,
        loan.aprBasisPoints,
        loan.termMonths,
        loan.borrowerPublicId,
        loan.auditHash
      )
    ).to.be.revertedWith("Principal must be positive");
  });

  it("should reject a mortgage with zero term", async function () {
    const { loanNote, loan } = await deployLoanNote();

    // A mortgage must have a valid repayment duration.
    await expect(
      loanNote.tokenizeLoan(
        loan.applicationHash,
        loan.tokenOwner,
        loan.principal,
        loan.aprBasisPoints,
        0n,
        loan.borrowerPublicId,
        loan.auditHash
      )
    ).to.be.revertedWith("Term must be positive");
  });

  it("should prevent mortgage token transfers", async function () {
    const { loanNote, owner, otherAccount, loan } =
      await deployLoanNote();

    // Mint the mortgage token to the institutional wallet.
    await loanNote.tokenizeLoan(
      loan.applicationHash,
      loan.tokenOwner,
      loan.principal,
      loan.aprBasisPoints,
      loan.termMonths,
      loan.borrowerPublicId,
      loan.auditHash
    );

    // Mortgage tokens are permanent records and cannot be transferred.
    await expect(
      loanNote.transferFrom(owner.address, otherAccount.address, 1n)
    ).to.be.revertedWith("Loan tokens are non-transferable");
  });

});