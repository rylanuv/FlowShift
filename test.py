import re

target_str = "0m"

total_minutes = 0

hours_match = re.search(r"(\d+)\s*h", target_str, re.IGNORECASE)
if hours_match:
    total_minutes += int(hours_match.group(1)) * 60

minutes_match = re.search(r"(\d+)\s*m", target_str, re.IGNORECASE)
if minutes_match:
    total_minutes += int(minutes_match.group(1)) * 1

print(f"Hours match: {hours_match}")
print(f"Minutes match: {minutes_match}")
print(f"Total minutes: {total_minutes}")

if hours_match is None and minutes_match is None:
    raw = target_str.strip()
    try:
        raw_val = int(raw)
        if raw_val > 0:
            total_minutes = raw_val * 60 if raw_val <= 24 else raw_val
        else:
            total_minutes = 120
    except:
        total_minutes = 120

print(f"Final total minutes: {total_minutes}")
