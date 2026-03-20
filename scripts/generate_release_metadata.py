#!/usr/bin/env python3
import argparse
import json
import pathlib
import re
from typing import Dict


DEFINE_RE = re.compile(r'^#define\s+(\w+)\s+(.+)$')


def parse_header(header_path: pathlib.Path) -> Dict[str, str]:
    values: Dict[str, str] = {}
    for line in header_path.read_text(encoding="utf-8").splitlines():
        match = DEFINE_RE.match(line.strip())
        if not match:
            continue
        key, value = match.groups()
        values[key] = value.strip().strip('"')
    return values


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--header", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    values = parse_header(pathlib.Path(args.header))
    metadata = {
        "tag": args.tag,
        "version": values["FW_SEMVER"],
        "version_code": int(values["FW_VERSION_CODE"]),
        "channel": values["FW_CHANNEL"],
        "dfu_package_format": "legacy-crc",
        "security_epoch": int(values["SECURITY_EPOCH"]),
        "hw_allow": [values["HW_REV"]],
        "min_bootloader": values["MIN_BOOTLOADER"],
        "stack_req": values["STACK_REQ"],
    }

    output_path = pathlib.Path(args.output)
    output_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
