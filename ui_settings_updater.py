import re

file_path = "app/src/main/res/layout/activity_settings.xml"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Update all button corner radius to 16dp
content = re.sub(r'app:cornerRadius="[0-9]+dp"', 'app:cornerRadius="16dp"', content)
# Update text styling to fit better
content = re.sub(r'android:textSize="13sp"', 'android:textSize="14sp"', content)
content = re.sub(r'android:textSize="12sp"([^>]*)text="Edit', 'android:textSize="14sp"\\1text="Edit', content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
