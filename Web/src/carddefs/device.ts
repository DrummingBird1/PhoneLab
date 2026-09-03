import type { SectionDef } from "../render";

export const deviceSection: SectionDef = {
  id: "device",
  title: "Device & System",
  desc: "No permission required",
  cards: [
    {
      id: "cpu",
      icon: "\u{1F5A5}️",
      title: "Processor & Memory",
      fields: [
        { key: "cores", label: "Logical cores" },
        { key: "mem", label: "Device memory", unit: "GB" },
      ],
    },
    {
      id: "battery",
      icon: "\u{1F50B}",
      title: "Battery",
      meter: true,
      sparkline: true,
      fields: [
        { key: "level", label: "Level" },
        { key: "charging", label: "Charging" },
        { key: "time", label: "Time remaining" },
      ],
      note: "The Battery Status API is deprecated and removed from most desktop browsers.",
    },
    {
      id: "cpupressure",
      icon: "\u{1F321}️",
      title: "CPU Pressure",
      fields: [{ key: "state", label: "State" }],
      note: "Compute Pressure API — load-derived pressure state (nominal/fair/serious/critical), the closest browser equivalent to the app's thermal-zone monitoring. Chrome/Edge only, experimental.",
    },
    {
      id: "network",
      icon: "\u{1F4F6}",
      title: "Network",
      fields: [
        { key: "status", label: "Status" },
        { key: "type", label: "Effective type" },
        { key: "downlink", label: "Downlink", unit: "Mb/s" },
        { key: "rtt", label: "Round-trip", unit: "ms" },
      ],
    },
    {
      id: "speedtest",
      icon: "\u{1F680}",
      title: "Internet Speed Test",
      gauge: true,
      fields: [
        { key: "ping", label: "Ping", unit: "ms" },
        { key: "download", label: "Download", unit: "Mb/s" },
        { key: "upload", label: "Upload", unit: "Mb/s" },
      ],
      note: "Runs a real download/upload test against Cloudflare's public speed-test endpoint — actual network traffic, not an estimate.",
    },
    {
      id: "wakelock",
      icon: "\u{1F4A1}",
      title: "Keep Screen Awake",
      toggle: { onLabel: "Screen will stay on — tap to allow sleep", offLabel: "Keep screen awake" },
      fields: [{ key: "state", label: "State" }],
      note: "Screen Wake Lock API — released automatically when you leave this tab.",
    },
    {
      id: "usb",
      icon: "\u{1F50C}",
      title: "USB Device",
      fields: [
        { key: "name", label: "Device" },
        { key: "vendor", label: "Vendor ID" },
        { key: "product", label: "Product ID" },
      ],
      note: "WebUSB — pick any paired device to see its identifiers. Chrome/Edge only, needs a user gesture.",
    },
    {
      id: "storage",
      icon: "\u{1F4BE}",
      title: "Storage",
      fields: [
        { key: "usage", label: "Used" },
        { key: "quota", label: "Quota" },
      ],
    },
    {
      id: "display",
      icon: "\u{1F5BC}️",
      title: "Display",
      fields: [
        { key: "res", label: "Resolution" },
        { key: "dpr", label: "Pixel ratio" },
        { key: "color", label: "Color depth", unit: "bit" },
      ],
    },
    {
      id: "platform",
      icon: "\u{1F310}",
      title: "Browser & Platform",
      fields: [
        { key: "browser", label: "Browser" },
        { key: "os", label: "Platform" },
        { key: "lang", label: "Language" },
        { key: "tz", label: "Time zone" },
      ],
    },
    {
      id: "input",
      icon: "✋",
      title: "Input",
      fields: [
        { key: "touch", label: "Touch points" },
        { key: "pointer", label: "Primary pointer" },
      ],
    },
    {
      id: "biometric",
      icon: "\u{1F446}",
      title: "Biometric Hardware",
      fields: [{ key: "available", label: "Platform authenticator" }],
      note: "Checks for Face ID / Touch ID / Windows Hello / fingerprint hardware via WebAuthn — matches the app's Hardware tab biometric check. Doesn't trigger authentication.",
    },
    {
      id: "posture",
      icon: "\u{1F4D2}",
      title: "Device Posture",
      fields: [{ key: "type", label: "Posture" }],
      note: "Device Posture API — reports folded/continuous state on foldable phones. Chrome/Android on supported foldables only.",
    },
  ],
};
