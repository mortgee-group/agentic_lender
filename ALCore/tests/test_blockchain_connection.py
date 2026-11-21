from web3 import Web3

# Local JSON-RPC endpoint provided by the running Hardhat node.
RPC_URL = "http://127.0.0.1:8545"

# Address returned by Hardhat Ignition after deploying LoanNote.sol.
CONTRACT_ADDRESS = "0x5FbDB2315678afecb367f032d93F642f64180aa3"


def main():
    """Verify that AL-Core can connect to the local blockchain."""

    # Establish a connection to the local Hardhat JSON-RPC server.
    web3 = Web3(Web3.HTTPProvider(RPC_URL))

    if not web3.is_connected():
        print("ERROR: Could not connect to the Hardhat blockchain.")
        return

    print("Connected to Hardhat blockchain!")

    # Retrieve the blockchain's current block number.
    block_number = web3.eth.block_number
    print(f"Current block number: {block_number}")

    # Check whether smart contract bytecode exists at the deployed address.
    contract_address = Web3.to_checksum_address(CONTRACT_ADDRESS)
    contract_code = web3.eth.get_code(contract_address)

    if len(contract_code) == 0:
        print("ERROR: No contract found at the specified address.")
        return

    print(f"LoanNote contract found at: {contract_address}")
    print("Blockchain connection test passed!")


if __name__ == "__main__":
    main()