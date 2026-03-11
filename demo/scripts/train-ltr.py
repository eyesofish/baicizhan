#!/usr/bin/env python3
"""
Lightweight LTR trainer for the v2-lite pipeline.

This script keeps artifact contracts stable:
1) demo/models/ltr_feature_schema.json
2) demo/models/ltr_xgb.json

If real samples are not provided, it emits a sane default linear ranking model.
"""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
from statistics import mean


FEATURE_NAMES = [f"f{i}" for i in range(1, 11)]
DEFAULT_WEIGHTS = [1.8, 0.6, 0.2, 1.5, -0.4, -0.3, -0.4, -0.0006, 0.2, 0.05]


def load_samples(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open("r", encoding="utf-8", newline="") as fp:
        reader = csv.DictReader(fp)
        for row in reader:
            if "label" not in row:
                continue
            sample = {"label": float(row["label"])}
            for feature in FEATURE_NAMES:
                sample[feature] = float(row.get(feature, 0.0) or 0.0)
            rows.append(sample)
    return rows


def fit_linear_weights(rows: list[dict]) -> list[float]:
    if not rows:
        return DEFAULT_WEIGHTS

    labels = [r["label"] for r in rows]
    label_mean = mean(labels)
    label_var = sum((v - label_mean) ** 2 for v in labels) or 1.0

    weights: list[float] = []
    for feature in FEATURE_NAMES:
        values = [r[feature] for r in rows]
        value_mean = mean(values)
        cov = sum((v - value_mean) * (y - label_mean) for v, y in zip(values, labels))
        weights.append(cov / label_var)
    return weights


def write_outputs(model_path: Path, schema_path: Path, weights: list[float]) -> None:
    schema_path.parent.mkdir(parents=True, exist_ok=True)
    model_path.parent.mkdir(parents=True, exist_ok=True)

    schema_path.write_text(json.dumps(FEATURE_NAMES, ensure_ascii=False, indent=2), encoding="utf-8")
    payload = {
        "modelType": "xgboost-lite-linear",
        "featureNames": FEATURE_NAMES,
        "weights": [round(w, 6) for w in weights],
        "bias": 0.0,
    }
    model_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples", type=Path, default=None, help="CSV with label and f1..f10 columns")
    parser.add_argument("--out-model", type=Path, default=Path("demo/models/ltr_xgb.json"))
    parser.add_argument("--out-schema", type=Path, default=Path("demo/models/ltr_feature_schema.json"))
    args = parser.parse_args()

    rows = []
    if args.samples is not None and args.samples.exists():
        rows = load_samples(args.samples)

    weights = fit_linear_weights(rows)
    write_outputs(args.out_model, args.out_schema, weights)
    print(f"Wrote model to: {args.out_model}")
    print(f"Wrote schema to: {args.out_schema}")


if __name__ == "__main__":
    main()
