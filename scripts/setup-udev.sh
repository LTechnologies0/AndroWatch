#!/usr/bin/env bash
# ADB udev rules (optional, Linux only). Run: sudo ./scripts/setup-udev.sh
set -euo pipefail
RULES="/etc/udev/rules.d/51-android.rules"
cat >"${RULES}" <<'EOF'
SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0666", GROUP="plugdev"
SUBSYSTEM=="usb", ATTR{idVendor}=="04e8", MODE="0666", GROUP="plugdev"
SUBSYSTEM=="usb", ATTR{idVendor}=="2717", MODE="0666", GROUP="plugdev"
EOF
udevadm control --reload-rules
udevadm trigger
echo "udev rules installed at ${RULES}"
