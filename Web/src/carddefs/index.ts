import type { SectionDef } from "../render";
import { motionSection } from "./motion";
import { orientationSection } from "./orientation";
import { environmentSection } from "./environment";
import { deviceSection } from "./device";

export const SECTIONS: SectionDef[] = [motionSection, orientationSection, environmentSection, deviceSection];
