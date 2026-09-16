#!/usr/bin/env python3
"""Update metadata for existing Alibaba Cloud OSS image objects.

The script is intentionally dry-run by default. It self-copies each matching
object so OSS replaces the response metadata without downloading the payload.
"""

from __future__ import annotations

import argparse
import mimetypes
import os
import sys
import time
from typing import Iterable


IMAGE_CACHE_CONTROL = "public,max-age=31536000,immutable"
IMAGE_CONTENT_DISPOSITION = "inline"
SAFE_INLINE_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp", "image/gif", "image/avif"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prefix", required=True, help="Only migrate objects under this OSS prefix")
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Apply metadata changes. Without this flag the script only previews changes",
    )
    parser.add_argument(
        "--confirm-bucket",
        help="Required with --execute; must exactly match ALIYUN_OSS_BUCKET",
    )
    parser.add_argument(
        "--public-cache",
        action="store_true",
        help="Required with --execute; confirms the target bucket is public",
    )
    parser.add_argument("--max-objects", type=int, default=0, help="Stop after N objects; 0 means no limit")
    parser.add_argument("--sleep-ms", type=int, default=50, help="Pause between writes to limit request rate")
    return parser.parse_args()


def required_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def header_value(headers: dict[str, str], name: str) -> str | None:
    name = name.lower()
    for key, value in headers.items():
        if key.lower() == name:
            return value
    return None


def image_content_type(key: str, headers: dict[str, str]) -> str | None:
    content_type = header_value(headers, "Content-Type")
    if content_type:
        normalized_type = content_type.split(";", 1)[0].strip().lower()
        if normalized_type in SAFE_INLINE_IMAGE_TYPES:
            return normalized_type
    guessed_type, _ = mimetypes.guess_type(key)
    if guessed_type and guessed_type in SAFE_INLINE_IMAGE_TYPES:
        return guessed_type
    return None


def copy_headers(key: str, source_headers: dict[str, str]) -> dict[str, str] | None:
    content_type = image_content_type(key, source_headers)
    if content_type is None:
        return None

    headers = {
        "Content-Type": content_type,
        "Cache-Control": IMAGE_CACHE_CONTROL,
        "Content-Disposition": IMAGE_CONTENT_DISPOSITION,
        "x-oss-metadata-directive": "REPLACE",
    }
    # Preserve application-specific metadata while replacing response headers.
    headers.update({key: value for key, value in source_headers.items() if key.lower().startswith("x-oss-meta-")})
    return headers


def iter_objects(bucket, prefix: str) -> Iterable:
    import oss2

    return oss2.ObjectIterator(bucket, prefix=prefix)


def main() -> int:
    args = parse_args()
    if not args.prefix.strip():
        raise SystemExit("--prefix cannot be empty; choose an explicit object directory")
    if args.max_objects < 0 or args.sleep_ms < 0:
        raise SystemExit("--max-objects and --sleep-ms cannot be negative")

    try:
        import oss2
    except ImportError:
        raise SystemExit("Install the OSS SDK first: python3 -m pip install oss2")

    endpoint = required_env("ALIYUN_OSS_ENDPOINT")
    bucket_name = required_env("ALIYUN_OSS_BUCKET")
    access_key_id = required_env("ALIYUN_ACCESS_KEY_ID")
    access_key_secret = required_env("ALIYUN_ACCESS_KEY_SECRET")
    if args.execute and args.confirm_bucket != bucket_name:
        raise SystemExit("--confirm-bucket must exactly match ALIYUN_OSS_BUCKET when using --execute")
    if args.execute and not args.public_cache:
        raise SystemExit("--public-cache is required with --execute; private buckets must not use public caching")

    auth = oss2.Auth(access_key_id, access_key_secret)
    bucket = oss2.Bucket(auth, endpoint, bucket_name)
    scanned = changed = skipped = failed = 0

    for obj in iter_objects(bucket, args.prefix):
        if args.max_objects and scanned >= args.max_objects:
            break
        scanned += 1
        key = obj.key
        try:
            head = bucket.head_object(key)
            source_headers = dict(head.headers)
            headers = copy_headers(key, source_headers)
            if headers is None:
                skipped += 1
                print(f"SKIP non-image {key}")
                continue

            current_cache = header_value(source_headers, "Cache-Control")
            current_disposition = header_value(source_headers, "Content-Disposition")
            current_type = header_value(source_headers, "Content-Type")
            desired_type = headers["Content-Type"]
            already_migrated = (
                current_type == desired_type
                and current_cache == IMAGE_CACHE_CONTROL
                and current_disposition == IMAGE_CONTENT_DISPOSITION
            )
            if already_migrated:
                skipped += 1
                print(f"SKIP already migrated {key}")
                continue

            changed += 1
            print(f"{'UPDATE' if args.execute else 'DRY-RUN'} {key} ({desired_type})")
            if args.execute:
                # Copying an object onto itself is OSS's metadata replacement operation.
                bucket.copy_object(bucket_name, key, key, headers)
                if args.sleep_ms:
                    time.sleep(args.sleep_ms / 1000)
        except Exception as error:  # noqa: BLE001 - continue so one bad object does not stop the batch
            failed += 1
            print(f"ERROR {key}: {error}", file=sys.stderr)

    print(f"scanned={scanned} changed={changed} skipped={skipped} failed={failed} execute={args.execute}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
