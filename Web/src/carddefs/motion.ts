import type { SectionDef } from "../render";

export const motionSection: SectionDef = {
  id: "motion",
  title: "Motion & Acceleration",
  desc: "DeviceMotion — accelerometer, gyroscope, activity",
  gateLabel: "iOS requires a tap to grant motion-sensor access.",
  toggleable: true,
  cards: [
    {
      id: "accel",
      icon: "\u{1F4E1}",
      title: "Accelerometer",
      sparkline: true,
      triaxis: true,
      fields: [
        { key: "x", label: "X", unit: "m/s²" },
        { key: "y", label: "Y", unit: "m/s²" },
        { key: "z", label: "Z", unit: "m/s²" },
        { key: "mag", label: "Magnitude", unit: "m/s²" },
      ],
      note: "Includes gravity, matching Android's raw TYPE_ACCELEROMETER.",
    },
    {
      id: "linaccel",
      icon: "➤",
      title: "Linear Acceleration",
      triaxis: true,
      fields: [
        { key: "x", label: "X", unit: "m/s²" },
        { key: "y", label: "Y", unit: "m/s²" },
        { key: "z", label: "Z", unit: "m/s²" },
      ],
      note: "Gravity removed by the OS, when the browser reports it.",
    },
    {
      id: "gravity",
      icon: "\u{1FA90}",
      title: "Gravity",
      triaxis: true,
      fields: [
        { key: "x", label: "X", unit: "m/s²" },
        { key: "y", label: "Y", unit: "m/s²" },
        { key: "z", label: "Z", unit: "m/s²" },
      ],
      note: "Estimated: exact acceleration minus linear acceleration, or a low-pass filter as fallback.",
    },
    {
      id: "gyro",
      icon: "\u{1F300}",
      title: "Gyroscope",
      sparkline: true,
      triaxis: true,
      fields: [
        { key: "x", label: "α (Z)", unit: "°/s" },
        { key: "y", label: "β (X)", unit: "°/s" },
        { key: "z", label: "γ (Y)", unit: "°/s" },
      ],
    },
    {
      id: "activity",
      icon: "\u{1F3C3}",
      title: "Activity Detector",
      stateViz: true,
      fields: [
        { key: "state", label: "State" },
        { key: "variance", label: "Motion energy" },
      ],
      note: "Derived from accelerometer variance — approximates Significant/Stationary/Motion Detect.",
    },
    {
      id: "steps",
      icon: "\u{1F45F}",
      title: "Step Counter",
      gauge: true,
      fields: [
        { key: "count", label: "Steps" },
        { key: "cadence", label: "Last minute" },
      ],
      note: "Estimated: peak-detection over the accelerometer signal — approximates Step Counter/Step Detector, not hardware-pedometer accuracy.",
    },
    {
      id: "motion-na",
      icon: "\u{1F6AB}",
      title: "Not exposed by browsers",
      note: "Accelerometer (Uncalibrated) and Gyroscope (Uncalibrated) have no Web API — they require raw OS sensor-fusion access browsers intentionally don't expose. Hinge Angle has no direct equivalent either, but see Device Posture in Device & System for the closest thing (fold state, not the angle).",
    },
  ],
};
