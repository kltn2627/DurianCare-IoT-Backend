# AI Rescue Pipeline Report

Primary detector failure now falls back to adaptive rescue using the primary YOLO model first,
then optional fallback detector, preserving the existing API contract.
