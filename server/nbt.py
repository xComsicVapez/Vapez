"""Minimal reader/writer for gzipped Minecraft NBT files (standard library only)."""

import gzip
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)

_SCALARS = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}
_ARRAYS = {BYTE_ARRAY: ">b", INT_ARRAY: ">i", LONG_ARRAY: ">q"}


class Tag:
    __slots__ = ("type", "value", "item_type")

    def __init__(self, tag_type, value, item_type=END):
        self.type = tag_type
        self.value = value
        self.item_type = item_type

    def __repr__(self):
        return f"Tag({self.type}, {self.value!r})"


class _Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, fmt):
        size = struct.calcsize(fmt)
        value = struct.unpack_from(fmt, self.data, self.pos)
        self.pos += size
        return value

    def string(self):
        (length,) = self.take(">H")
        raw = self.data[self.pos:self.pos + length]
        self.pos += length
        return raw.decode("utf-8", errors="surrogateescape")

    def payload(self, tag_type):
        if tag_type in _SCALARS:
            return Tag(tag_type, self.take(_SCALARS[tag_type])[0])
        if tag_type in _ARRAYS:
            (length,) = self.take(">i")
            return Tag(tag_type, list(self.take(f">{length}{_ARRAYS[tag_type][1]}")))
        if tag_type == STRING:
            return Tag(STRING, self.string())
        if tag_type == LIST:
            item_type, length = self.take(">bi")
            return Tag(LIST, [self.payload(item_type) for _ in range(length)], item_type)
        if tag_type == COMPOUND:
            entries = {}
            while True:
                (child_type,) = self.take(">b")
                if child_type == END:
                    return Tag(COMPOUND, entries)
                name = self.string()
                entries[name] = self.payload(child_type)
        raise ValueError(f"unknown NBT tag type {tag_type}")


def _write_string(out, text):
    raw = text.encode("utf-8", errors="surrogateescape")
    out += struct.pack(">H", len(raw)) + raw


def _write_payload(out, tag):
    if tag.type in _SCALARS:
        out += struct.pack(_SCALARS[tag.type], tag.value)
    elif tag.type in _ARRAYS:
        out += struct.pack(">i", len(tag.value))
        out += struct.pack(f">{len(tag.value)}{_ARRAYS[tag.type][1]}", *tag.value)
    elif tag.type == STRING:
        _write_string(out, tag.value)
    elif tag.type == LIST:
        out += struct.pack(">bi", tag.item_type, len(tag.value))
        for item in tag.value:
            _write_payload(out, item)
    elif tag.type == COMPOUND:
        for name, child in tag.value.items():
            out += struct.pack(">b", child.type)
            _write_string(out, name)
            _write_payload(out, child)
        out += b"\x00"


def load(path):
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    reader = _Reader(data)
    (root_type,) = reader.take(">b")
    root_name = reader.string()
    return root_name, reader.payload(root_type)


def save(path, root_name, root):
    out = bytearray(struct.pack(">b", root.type))
    _write_string(out, root_name)
    _write_payload(out, root)
    with open(path, "wb") as handle:
        handle.write(gzip.compress(bytes(out)))
