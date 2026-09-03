import { setStatus, setFields, addAction } from "./ui";

export function initUsb() {
  const usb = (navigator as any).usb;
  if (!usb) {
    setStatus("usb", "unavailable", "No WebUSB API");
    return;
  }

  setStatus("usb", "permission");

  async function showDevice(device: any) {
    setStatus("usb", "live");
    setFields("usb", {
      name: device.productName || "Unnamed device",
      vendor: `0x${device.vendorId.toString(16).padStart(4, "0")}`,
      product: `0x${device.productId.toString(16).padStart(4, "0")}`,
    });
  }

  usb
    .getDevices()
    .then((devices: any[]) => {
      if (devices.length) showDevice(devices[0]);
    })
    .catch(() => {});

  addAction(
    "usb",
    "Connect USB Device",
    async () => {
      try {
        const device = await usb.requestDevice({ filters: [] });
        showDevice(device);
      } catch (err: any) {
        setStatus(
          "usb",
          err?.name === "NotFoundError" ? "idle" : "error",
          err?.name === "NotFoundError" ? "No device selected" : err?.message
        );
      }
    },
    false
  );
}
