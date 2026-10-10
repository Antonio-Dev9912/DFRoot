# DFRoot [DirtyFrag (CVE-2026-43284)]

DFRoot is an Android rooting tool that exploits CVE-2026-43284 (DirtyFrag) — a kernel page cache write primitive — to load a custom kernel module without requiring an unlocked bootloader. It is intentionally designed to be SU Manager agnostic and works with any KernelSU-compatible SU manager.

> [!IMPORTANT]
> Before hitting that fork button, consider making a pull request instead :)

---

## Features

- **Start on Boot** support.
- **Automatic soft reboot** (if `late-load --soft-reboot` is supported by your SU Manager).
- **RO Partition Protection**.
- **Disable all KernelSU modules** to recover from broken or incompatible modules.
- **Independent operation:** Shizuku not needed — regain root without Wi-Fi!

---

## Usage

> [!WARNING]
> Use at your own risk. I am not responsible for any damage to your device.

1. Install [DFRoot](https://github.com/diabl0w/DFRoot/releases/latest)
2. Install a compatible SU Manager:
   - **Samsung Devices:**
     - [diabl0w's KernelSU](https://github.com/diabl0w/KernelSU/releases/latest)
     - [zandatsu07's KernelSU-Next](https://github.com/zandatsu07/KernelSU-Next/releases/latest)
   - **Other Devices:**
     - [KernelSU](https://github.com/tiann/KernelSU/releases/latest)
     - [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next/releases/latest)
     - [KowSU](https://github.com/KOWX712/KernelSU/releases/latest)

> If you have a custom KernelSU fork for a specific manufacturer, please make a pull request to add it here.

---

## FAQ

<details>
<summary><b>Q: Log says patching failed and that my device isn't vulnerable</b></summary>

A: Sorry, there is no fix. Either:
- Your device's kernel is too new.
- Your device manufacturer backported the official mitigation.
- [Accidental mitigation](https://github.com/V4bel/dirtyfrag/issues/23#issuecomment-4405314290) (typically seen on kernel 6.1).
</details>

<details>
<summary><b>Q: Log says "SUCCESS" or "ksud exited with error", but I don't have root</b></summary>

A: First, try disabling **"Auto Soft Reboot"** in DFRoot settings if you have it enabled; otherwise, refer to [Discussions #69](https://github.com/diabl0w/DFRoot/discussions/69).
</details>

<details>
<summary><b>Q: I installed a module and now I can't launch root without crashing</b></summary>

A: Toggle **"Disable KSU Modules"** in DFRoot settings.
</details>

<details>
<summary><b>Q: I installed a module and have "Start at Boot" enabled and now I am bootlooping</b></summary>

A: Typically Android will detect bootloops and boot into Safe Mode. Otherwise, hold the Volume Down button during boot to enter safe mode. From there, disable **"Start on Boot"** and enable **"Disable KSU Modules"** in DFRoot settings, or uninstall DFRoot until stabilized.
</details>

---

## How it works

The Android kernel decrypts AES-CBC ESP packets directly into the page cache of files open for `splice()`. By crafting `IV = AES_ECB_DEC(key, current_content) ⊕ desired_content`, any 16-byte-aligned block in a mapped shared library can be overwritten without write permission and without copy-on-write.

The exploit uses this primitive to patch shellcode into `libc++.so` in the kernel's page cache. The next privileged call to the hooked function runs the shellcode, which loads our custom kernel module via `insmod`.

### Exploit chain

1. **IpSec transform** — App allocates a `UdpEncapsulationSocket` + SPI and builds an AES-CBC/HMAC-SHA256 ESP transform via `IpSecManager`.
2. **splicehelper → crash_dump64** — The splicehelper binary is spliced into `crash_dump64` via the CBC primitive. `crash_dump64` runs in the `crash_dump` SELinux domain (via exec label transition), which can open `vendor_file` labeled files. The splicehelper serves two modes: splice mode (pipe a 16-byte page chunk out to parent for write) and read mode (`argv[3]="r"`, write 16 bytes of file content to pipe fd for IV computation).
3. **dfroot.ko → vendor_file** — The kernel module is written via the crash_dump bridge into a `vendor_file`-labeled file.
4. **libc++ hook** (fires in init, `uid=0`, `tid=1`) — Shellcode is patched into `libc++.so` at `std::ostream::sentry::sentry()` (`_ZNSt3__113basic_ostreamIcNS_11char_traitsIcEEE6sentryC1ERS3_`). Triggered when init reaps an orphaned child process:
   - Validates `getuid() == 0` and `gettid() == 1`.
   - Creates `/dev/df` as a one-shot mutex (`O_CREAT|O_EXCL`) to prevent re-entry.
   - Clones a worker child process.
   - Grandchild sets `u:r:vendor_modprobe:s0` to `/proc/self/attr/exec` and calls `/vendor/bin/insmod <ko_target>`.
5. **dfroot.ko init** (runs as `vendor_modprobe`, `uid=0`) — The KO is loaded via `insmod`:
   - Writes `false` to `selinux_state` (global permissive).
   - Bypasses DEFEX via kprobes (if applicable).
   - Calls `call_usermodehelper` to run bootstrap code.
6. **bootstrap.c** — Performs initialization tasks and launches the `su` daemon from your installed SU Manager.

---

## Building

```sh
make
