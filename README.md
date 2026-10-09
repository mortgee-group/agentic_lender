# agentic_lender

## Install on Linux, macOS, and Windows

To get started, first clone the repository to your local machine:

```bash
git clone https://github.com/mortgee-group/agentic_lender.git
cd agentic_lender
```

Then begin working in the project folder.

### If Git prompts for a username and password
If you are using HTTPS and Git asks for your username and password, use the email address and password associated with your GitHub account.

Example:
- Username: your GitHub email address
- Password: your GitHub password

### Use SSH instead (recommended)
If you prefer SSH, generate an SSH key in your local terminal first:

```bash
ssh-keygen -t ed25519 -C "your_email@example.com"
```

Note

If you are using a legacy system that doesn't support the Ed25519 algorithm, use:

```bash
ssh-keygen -t rsa -b 4096 -C "your_email@example.com"
```

After generating the key, open the generated public key and copy it, then add it to your GitHub account under SSH keys.

Example to view the public key:

```bash
cat ~/.ssh/id_ed25519.pub
```

If you're using RSA instead, view:

```bash
cat ~/.ssh/id_rsa.pub
```

Then paste the public key into the SSH key section on GitHub.

After that, clone with SSH:

```bash
git clone git@github.com:mortgee-group/agentic_lender.git
cd agentic_lender
```

### Linux

```bash
git clone https://github.com/mortgee-group/agentic_lender.git
cd agentic_lender
```

Or with SSH:

```bash
git clone git@github.com:mortgee-group/agentic_lender.git
cd agentic_lender
```

### macOS

```bash
git clone https://github.com/mortgee-group/agentic_lender.git
cd agentic_lender
```

Or with SSH:

```bash
git clone git@github.com:mortgee-group/agentic_lender.git
cd agentic_lender
```

### Windows

```bash
git clone https://github.com/mortgee-group/agentic_lender.git
cd agentic_lender
```

Or with SSH:

```bash
git clone git@github.com:mortgee-group/agentic_lender.git
cd agentic_lender
```

Once inside the project folder, you can start working on the repository.
