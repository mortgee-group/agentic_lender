// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {ERC721} from "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title LoanNote
 * @notice Represents approved mortgage loans as non-transferable ERC-721 tokens.
 * @dev Stores loan metadata and a SHA-256 audit hash on the blockchain.
 *      Only the contract owner (AL-Core's authorised wallet) can mint tokens.
 */
contract LoanNote is ERC721, Ownable {

    // Stores the information associated with an approved mortgage.
    struct Loan {
        uint256 principal;          // Approved loan amount in cents.
        uint256 aprBasisPoints;     // Annual interest rate (450 = 4.50%).
        uint256 termMonths;         // Mortgage duration in months.
        bytes32 borrowerPublicId;   // Pseudonymous borrower identifier.
        bytes32 auditHash;          // SHA-256 hash of the final lending evidence.
        uint256 issuedAt;           // Blockchain timestamp when token was minted.
    }

    // Token IDs start at 1 and increase for each approved mortgage.
    uint256 private nextTokenId = 1;

    // Associates each token ID with its mortgage information.
    mapping(uint256 => Loan) public loans;

    // Tracks which applications have already been tokenized.
    mapping(bytes32 => bool) public tokenizedApplications;

    /**
     * @notice Emitted when a new mortgage loan token is created.
     * @param tokenId Unique ERC-721 token identifier.
     * @param applicationHash Hash identifying the mortgage application.
     * @param auditHash SHA-256 hash of the final lending evidence.
     */
    event LoanTokenized(
        uint256 indexed tokenId,
        bytes32 indexed applicationHash,
        bytes32 auditHash
    );

    /**
     * @notice Initializes the ERC-721 token and assigns contract ownership.
     * @dev The deploying wallet becomes the authorised token issuer.
     */
    constructor()
        ERC721("Agentic Lender Loan Note", "ALLN")
        Ownable(msg.sender)
    {}

    /**
     * @notice Creates a loan token for an approved mortgage.
     * @dev Only the contract owner can call this function.
     *
     * @param applicationHash Hash of the Firestore application ID.
     * @param tokenOwner Wallet that will hold the mortgage token.
     * @param principal Approved mortgage principal in cents.
     * @param aprBasisPoints Annual percentage rate in basis points.
     * @param termMonths Mortgage repayment term in months.
     * @param borrowerPublicId Pseudonymous identifier for the borrower.
     * @param auditHash SHA-256 hash of the final credit memo,
     *                  valuation data, and underwriter approval.
     *
     * @return tokenId The newly created ERC-721 token identifier.
     */
    function tokenizeLoan(
        bytes32 applicationHash,
        address tokenOwner,
        uint256 principal,
        uint256 aprBasisPoints,
        uint256 termMonths,
        bytes32 borrowerPublicId,
        bytes32 auditHash
    )
        external
        onlyOwner
        returns (uint256)
    {
        // Validate required identifiers and loan information.
        require(
            applicationHash != bytes32(0),
            "Invalid application hash"
        );

        require(
            tokenOwner != address(0),
            "Invalid token owner"
        );

        require(
            principal > 0,
            "Principal must be positive"
        );

        require(
            termMonths > 0,
            "Term must be positive"
        );

        require(
            borrowerPublicId != bytes32(0),
            "Invalid borrower identifier"
        );

        require(
            auditHash != bytes32(0),
            "Invalid audit hash"
        );

        // Prevent the same mortgage application from being tokenized twice.
        require(
            !tokenizedApplications[applicationHash],
            "Application already tokenized"
        );

        // Generate a unique token ID for this mortgage.
        uint256 tokenId = nextTokenId++;

        // Mark the application as tokenized before minting.
        tokenizedApplications[applicationHash] = true;

        // Store the approved mortgage information on the blockchain.
        loans[tokenId] = Loan({
            principal: principal,
            aprBasisPoints: aprBasisPoints,
            termMonths: termMonths,
            borrowerPublicId: borrowerPublicId,
            auditHash: auditHash,
            issuedAt: block.timestamp
        });

        // Mint the ERC-721 token to the authorised institutional wallet.
        _safeMint(tokenOwner, tokenId);

        // Emit an event for blockchain audit and transaction tracking.
        emit LoanTokenized(
            tokenId,
            applicationHash,
            auditHash
        );

        return tokenId;
    }

    /**
     * @notice Prevents transfers of existing mortgage loan tokens.
     * @dev Minting is permitted, but transfers between wallets are blocked.
     *      This keeps each token associated with its original holder.
     */
    function _update(
        address to,
        uint256 tokenId,
        address auth
    )
        internal
        override
        returns (address)
    {
        // Retrieve the current token owner.
        // address(0) indicates that the token has not been minted yet.
        address from = _ownerOf(tokenId);

        // Allow initial minting, but reject transfers of existing tokens.
        require(
            from == address(0),
            "Loan tokens are non-transferable"
        );

        return super._update(to, tokenId, auth);
    }
}