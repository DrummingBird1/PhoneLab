import type { SectionDef } from "../render";

export const orientationSection: SectionDef = {
  id: "orientation",
  title: "Orientation & Compass",
  desc: "DeviceOrientation & Generic Sensor API",
  gateLabel: "iOS requires a tap to grant orientation-sensor access.",
  toggleable: true,
  cards: [
    {
      id: "compass",
      icon: "\u{1F9ED}",
      title: "Compass Heading",
      compass: true,
      fields: [
        { key: "heading", label: "Heading", unit: "°" },
        { key: "ref", label: "Reference" },
      ],
    },
    {
      id: "attitude",
      icon: "⚖️",
      title: "Attitude & Tilt",
      level: true,
      fields: [
        { key: "beta", label: "Pitch (β)", unit: "°" },
        { key: "gamma", label: "Roll (γ)", unit: "°" },
      ],
      note: "Approximates ROTATION_VECTOR / TILT_DETECTOR from orientation angles.",
    },
    {
      id: "magneto",
      icon: "\u{1F9F2}",
      title: "Magnetic Field",
      triaxis: true,
      fields: [
        { key: "x", label: "X", unit: "µT" },
        { key: "y", label: "Y", unit: "µT" },
        { key: "z", label: "Z", unit: "µT" },
      ],
      note: "Generic Sensor API — supported only on Chrome/Edge for Android, over HTTPS.",
    },
    {
      id: "screenorient",
      icon: "\u{1F4F1}",
      title: "Screen Orientation",
      rotateIcon: true,
      fields: [
        { key: "angle", label: "Angle", unit: "°" },
        { key: "type", label: "Type" },
      ],
    },
    {
      id: "orient-na",
      icon: "\u{1F6AB}",
      title: "Not exposed by browsers",
      note: "Magnetic Field (Uncalibrated), Geomagnetic Rotation Vector and Game Rotation Vector aren't separately available — the browser only ever exposes one fused orientation reading.",
    },
  ],
};
