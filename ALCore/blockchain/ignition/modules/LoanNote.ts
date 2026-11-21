import { buildModule } from "@nomicfoundation/hardhat-ignition/modules";

/**
 * Deployment module for the Agentic Lender LoanNote contract.
 *
 * Hardhat Ignition uses this module to deploy the ERC-721 contract
 * and track its deployment information.
 */
export default buildModule("LoanNoteModule", (m) => {

  // Deploy LoanNote without constructor arguments.
  // The deploying account becomes the contract owner.
  const loanNote = m.contract("LoanNote");

  // Expose the deployed contract for later use.
  return { loanNote };
});