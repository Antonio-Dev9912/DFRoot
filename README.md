# DFRoot

DFRoot is an Android rooting utility based on CVE-2026-43284 (DirtyFrag). It enables loading a custom kernel module without requiring an unlocked bootloader and is designed to work seamlessly with KernelSU-compatible SU managers.

> [!IMPORTANT]
> Please consider opening a Pull Request instead of creating individual forks.

---

## Features

- **Start on Boot** support.
- **Automatic Soft Reboot** (when supported by the SU Manager via `late-load --soft-reboot`).
- **Read-Only (RO) Partition Protection**.
- **Safe Recovery:** Option to disable all KernelSU modules if an issue occurs.
- **Independent:** Operates without requiring Shizuku or an active Wi-Fi connection.

---

## Usage

> [!WARNING]
> Use at your own risk. Modifying system software carries inherent risks.

1. Install the latest [DFRoot Release](https://github.com/diabl0w/DFRoot/releases/latest).
2. Install a compatible SU Manager:
   - **Samsung Devices:** [KernelSU (diabl0w)](https://github.com/diabl0w/KernelSU/releases/latest)
   - **Other Devices:**
     - [KernelSU](https://github.com/tiann/KernelSU/releases/latest)
     - [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next/releases/latest)
     - [KowSU](https://github.com/KOWX712/KernelSU/releases/latest)

---

## FAQ & Troubleshooting

<details>
<summary><b>Log says patching failed / Device not vulnerable</b></summary>

There is no workaround for this error. This usually indicates:
- The device kernel has been updated.
- The manufacturer has backported the patch.
- An accidental mitigation is present (common on kernel 6.1).
</details>

<details>
<summary><b>Log says "SUCCESS" or "ksud exited with error", but root is missing</b></summary>

- Try disabling **Auto Soft Reboot** in DFRoot settings.
- For more troubleshooting steps, visit the community discussions.
</details>

<details>
<summary><b>Device crashes or bootloops after installing a module</b></summary>

- If the app crashes on launch: Enable **Disable KSU Modules** in DFRoot settings.
- If the device is bootlooping: Boot into **Safe Mode** (usually by holding Volume Down during startup), then disable **Start on Boot** and enable **Disable KSU Modules**.
</details>

---

## Building

To compile the project:

```sh
make
