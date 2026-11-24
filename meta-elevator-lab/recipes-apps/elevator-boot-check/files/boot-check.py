#!/usr/bin/python3
import sqlite3

with open("/proc/sys/kernel/random/boot_id", encoding="ascii") as source:
    boot_id = source.read().strip()

with sqlite3.connect("/srv/elevator-lab/boot.db") as db:
    db.execute("CREATE TABLE IF NOT EXISTS boots (boot_id TEXT PRIMARY KEY)")
    db.execute("INSERT OR IGNORE INTO boots VALUES (?)", (boot_id,))
print("Elevator lab boot recorded:", boot_id)
