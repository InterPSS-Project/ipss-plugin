"""Stream complete benchmark observations without resampling or dropping event rows."""
import csv
import gzip
import json
import math
import struct
from pathlib import Path


def read_metadata(directory):
    metadata = json.loads((Path(directory) / "full-network-channels.json").read_text())
    if metadata.get("schema") != "texas2k-full-network-observations-v1":
        raise ValueError("Unsupported observation metadata schema")
    names = [channel["name"] for channel in metadata["channels"]]
    if len(names) != len(set(names)):
        raise ValueError("Duplicate channel names")
    return metadata


def iter_observations(directory):
    """Yield (time, event_side, tuple(values)) in manifest channel order.

    Exhaust the iterator to validate the final sample count and file termination.
    BINARY64 stores big-endian IEEE binary64 without rounding or compression.
    """
    directory = Path(directory)
    metadata = read_metadata(directory)
    names = [channel["name"] for channel in metadata["channels"]]
    count = 0
    first = last = None
    for time, side, values in _rows(directory, metadata, names):
        if len(values) != len(names) or not all(map(math.isfinite, (time, *values))):
            raise ValueError("Incomplete or nonfinite observation")
        if any(character in side for character in ",\r\n"):
            raise ValueError("Invalid event side")
        if count == 0:
            first = time
        last = time
        count += 1
        yield time, side, values
    if count != metadata["samples"]:
        raise ValueError("Observation count differs from manifest")
    if count and (first != metadata["first_time_s"] or last != metadata["last_time_s"]):
        raise ValueError("Observation time bounds differ from manifest")


def _exact(stream, size):
    value = stream.read(size)
    if len(value) != size:
        raise ValueError("Truncated binary observation")
    return value


def _rows(directory, metadata, names):
    format_name = metadata.get("trace_format", "csv-gzip")
    if format_name == "csv-gzip":
        with gzip.open(directory / "full-network.csv.gz", "rt", newline="") as stream:
            reader = csv.reader(stream)
            header = next(reader, None)
            if header is None and metadata["samples"] == 0:
                return
            if header != ["time_s", "sample_kind", *names]:
                raise ValueError("CSV header differs from manifest")
            for row in reader:
                if len(row) != len(names) + 2:
                    raise ValueError("Incomplete CSV observation")
                yield float(row[0]), row[1], tuple(map(float, row[2:]))
    elif format_name == "ipss-observations-binary64-v1":
        if metadata.get("trace_file") != "full-network.bin":
            raise ValueError("Unexpected binary trace path")
        with (directory / "full-network.bin").open("rb") as stream:
            header = stream.read(12)
            if not header and metadata["samples"] == 0:
                return
            if len(header) != 12 or header[:8] != b"IPSSOBS1":
                raise ValueError("Invalid binary observation header")
            if struct.unpack(">I", header[8:])[0] != len(names):
                raise ValueError("Binary channel count differs from manifest")
            values = struct.Struct(">" + "d" * len(names))
            for _ in range(metadata["samples"]):
                time, length = struct.unpack(">dI", _exact(stream, 12))
                if length > 1024:
                    raise ValueError("Oversized event side")
                side = _exact(stream, length).decode("utf-8")
                yield time, side, values.unpack(_exact(stream, values.size))
            if stream.read(1):
                raise ValueError("Unexpected trailing binary observations")
    else:
        raise ValueError("Unsupported observation format: " + format_name)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    rows = sum(1 for _ in iter_observations(args.directory))
    print(json.dumps(dict(samples=rows, channels=len(read_metadata(args.directory)["channels"]),
                         all_values_finite=True)))
