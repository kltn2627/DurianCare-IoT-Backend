"use strict";

// Static knowledge base — mirrors V5 SQL seed for in-memory calculation
// (no DB round-trip needed for scoring; DB stores final assessment results)

const CHEMICALS = {
  paclobutrazol: { name: "Paclobutrazol (kích hoa)",   phiDays: 60, halfLifeDays: 60  },
  hexaconazole:  { name: "Hexaconazole (trị nấm)",     phiDays: 14, halfLifeDays: 30  },
  metalaxyl:     { name: "Metalaxyl (xì mủ)",           phiDays:  7, halfLifeDays:  7  },
  fosetyl_al:    { name: "Fosetyl-Al (thối rễ)",        phiDays:  7, halfLifeDays: 14  },
  chlorpyrifos:  { name: "Chlorpyrifos (trừ sâu)",      phiDays: 21, halfLifeDays: 30  },
  thiamethoxam:  { name: "Thiamethoxam (rệp, bọ trĩ)", phiDays: 14, halfLifeDays: 10  },
  imidacloprid:  { name: "Imidacloprid (rầy, rệp)",    phiDays: 21, halfLifeDays: 20  },
  mancozeb:      { name: "Mancozeb (nấm tổng hợp)",    phiDays:  7, halfLifeDays:  3  },
  carbendazim:   { name: "Carbendazim (thán thư)",      phiDays: 14, halfLifeDays: 20  },
  dimethoate:    { name: "Dimethoate (ruồi trái)",      phiDays: 21, halfLifeDays:  5  },
};

// MRL in ppm (mg/kg) per market. null = banned
const MRL = {
  CHINA:    { paclobutrazol: 0.10, hexaconazole: 0.10, metalaxyl: 0.50, fosetyl_al: 75.00, chlorpyrifos: 0.50,  thiamethoxam: 0.50, imidacloprid: 0.50, mancozeb: 7.00,  carbendazim: 0.50, dimethoate: 0.20 },
  EU:       { paclobutrazol: 0.02, hexaconazole: 0.01, metalaxyl: 0.05, fosetyl_al: 75.00, chlorpyrifos: null,  thiamethoxam: 0.02, imidacloprid: 0.01, mancozeb: 0.05,  carbendazim: 0.10, dimethoate: 0.02 },
  US:       { paclobutrazol: 0.50, hexaconazole: 0.10, metalaxyl: 0.50, fosetyl_al: 75.00, chlorpyrifos: 0.10,  thiamethoxam: 0.50, imidacloprid: 0.50, mancozeb: 7.00,  carbendazim: null,  dimethoate: 0.50 },
  JAPAN:    { paclobutrazol: 0.05, hexaconazole: 0.05, metalaxyl: 0.05, fosetyl_al: 10.00, chlorpyrifos: 0.30,  thiamethoxam: 0.30, imidacloprid: 0.20, mancozeb: 2.00,  carbendazim: 0.50, dimethoate: 0.10 },
  DOMESTIC: { paclobutrazol: 0.50, hexaconazole: 0.50, metalaxyl: 1.00, fosetyl_al: 100.0, chlorpyrifos: 0.50,  thiamethoxam: 1.00, imidacloprid: 1.00, mancozeb: 10.00, carbendazim: 1.00, dimethoate: 0.50 },
};

const MARKET_NAMES = {
  CHINA:    "Trung Quốc (GACC)",
  EU:       "EU (GlobalGAP / EC 396)",
  US:       "Hoa Kỳ (FDA / EPA)",
  JAPAN:    "Nhật Bản (Food Sanitation Law)",
  DOMESTIC: "Nội địa (VietGAP / QCVN)",
};

const VALID_MARKETS = Object.keys(MRL);

module.exports = { CHEMICALS, MRL, MARKET_NAMES, VALID_MARKETS };
