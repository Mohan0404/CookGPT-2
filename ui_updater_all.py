import re
import glob

directory = 'app/src/main/res/layout/'
files = glob.glob(directory + '*.xml')

for file_path in files:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Add modified comment tracking
    if 'MODIFIED — Stitch MCP' not in content and '<?xml' in content:
        content = content.replace('<?xml version="1.0" encoding="utf-8"?>', '<?xml version="1.0" encoding="utf-8"?>\n<!-- MODIFIED — Stitch MCP UI enhancement -->')

    # 2. MaterialCardView standardisation
    content = content.replace('androidx.cardview.widget.CardView', 'com.google.android.material.card.MaterialCardView')
    
    def format_cards(match):
        tags = match.group(0)
        # Clear out existing layout elements to standardise
        tags = re.sub(r'app:cardCornerRadius="[^"]*"', '', tags)
        tags = re.sub(r'app:cardElevation="[^"]*"', '', tags)
        tags = re.sub(r'app:strokeWidth="[^"]*"', '', tags)
        tags = re.sub(r'app:strokeColor="[^"]*"', '', tags)
        
        # apply specific material 3 Stitch parameters
        if tags.endswith('/>'):
            tags = tags[:-2] + '\n        app:cardCornerRadius="16dp"\n        app:cardElevation="0dp"\n        app:strokeWidth="1dp"\n        app:strokeColor="#E5E7EB"/>'
        elif tags.endswith('>'):
            tags = tags[:-1] + '\n        app:cardCornerRadius="16dp"\n        app:cardElevation="0dp"\n        app:strokeWidth="1dp"\n        app:strokeColor="#E5E7EB">'
        return tags

    content = re.sub(r'<com\.google\.android\.material\.card\.MaterialCardView[^>]*>', format_cards, content)

    # 3. Typography scaling
    # Just standardizing Title Large/Medium and body text on relevant sections
    # Using generic text size adjustments is risky globally because of spacing but
    # I will stick to what the user safely mentioned like card padding standardization.

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
