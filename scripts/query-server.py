#!/usr/bin/env python3
"""Minecraft Server List Ping — check MOTD / players without joining."""
from __future__ import annotations

import json
import socket
import struct
import sys
from typing import Any


def pack_varint(value: int) -> bytes:
    out = bytearray()
    remaining = value & 0xFFFFFFFF
    while True:
        byte = remaining & 0x7F
        remaining >>= 7
        if remaining:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            break
    return bytes(out)


def read_varint(sock: socket.socket) -> int:
    value = 0
    shift = 0
    while True:
        data = sock.recv(1)
        if not data:
            raise ConnectionError("connection closed while reading varint")
        byte = data[0]
        value |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            return value
        shift += 7
        if shift > 35:
            raise ValueError("varint too long")


def ping(host: str, port: int = 25565, timeout: float = 5.0) -> dict[str, Any]:
    with socket.create_connection((host, port), timeout=timeout) as sock:
        handshake = (
            pack_varint(0)
            + pack_varint(767)
            + pack_varint(len(host.encode()))
            + host.encode()
            + struct.pack(">H", port)
            + pack_varint(1)
        )
        sock.sendall(pack_varint(len(handshake)) + handshake)
        sock.sendall(pack_varint(1) + pack_varint(0))
        _length = read_varint(sock)
        packet_id = read_varint(sock)
        if packet_id != 0:
            raise ValueError(f"unexpected packet id {packet_id}")
        json_len = read_varint(sock)
        raw = bytearray()
        while len(raw) < json_len:
            chunk = sock.recv(json_len - len(raw))
            if not chunk:
                break
            raw.extend(chunk)
    return json.loads(raw.decode("utf-8"))


def motd_text(node: Any) -> str:
    if node is None:
        return ""
    if isinstance(node, str):
        return node
    if isinstance(node, list):
        return "".join(motd_text(part) for part in node)
    if isinstance(node, dict):
        return str(node.get("text") or "") + motd_text(node.get("extra"))
    return str(node)
    if len(sys.argv) < 2:
        print("usage: query-server.py <host> [port]", file=sys.stderr)
        return 2
    host = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 25565
    data = ping(host, port)
    motd = motd_text(data.get("description"))
    players = data.get("players") or {}
    version = (data.get("version") or {}).get("name")
    print(f"host     {host}:{port}")
    print(f"version  {version}")
    print(f"players  {players.get('online')}/{players.get('max')}")
    print(f"motd     {motd}")
    if data.get("favicon"):
        print("favicon  present")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001 — CLI
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1)
