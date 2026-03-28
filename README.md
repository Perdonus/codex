# Codex Android Client

Native Android client for a remote Linux-hosted `codex-cli` workflow.

## What it does

- Connects to `91.233.168.233:22` over SSH.
- Bootstraps SSH key auth from a one-time password login.
- Starts and talks to remote `codex app-server` over an SSH-forwarded WebSocket.
- Shows Codex dialogs, streams responses, supports interrupt, and keeps an SFTP file tree beside chat.
- Supports ChatGPT account sign-in through the remote `codex app-server` OAuth flow, with auth status shown in-app.
- Supports GitHub device login and pinning a repository to a dialog.
- Writes or updates a managed `AGENTS.md` block inside the remote working tree so Codex knows it is being used from Android and must build through GitHub.

## Build policy

This repo is intentionally set up for **GitHub-only builds**.

- Do not build Android artifacts on the Linux server where Codex runs.
- Use GitHub Actions for CI and release artifacts.
- The expected remote is `git@github.com:Perdonus/codex.git`.

## Required secret

Set these GitHub Actions secrets:

- `GITHUB_DEVICE_CLIENT_ID` for the in-app GitHub OAuth device flow.
- `SERVER_SSH_KEY` so GitHub Actions can upload the built APK back to the Linux server after CI succeeds.
- Optional overrides: `SERVER_HOST`, `SERVER_PORT`, `SERVER_USER`, `SERVER_UPLOAD_PATH`. By default the workflow uploads to `root@91.233.168.233:22` into `/root/codex-app/builds`.

## Notes

- `gradlew` in this repo is a lightweight bootstrap script that downloads Gradle 9.3.1 on demand. It is designed for CI and avoids local Gradle installation requirements.
- The Android app itself is still configured to never build on the remote Linux host in normal workflow.
