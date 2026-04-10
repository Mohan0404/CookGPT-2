import re
import os

files = [
    'app/src/main/res/layout/activity_home.xml',
    'app/src/main/res/layout/activity_gemini_chat.xml',
    'app/src/main/res/layout/item_news_card.xml',
    'app/src/main/res/layout/item_message_user.xml',
    'app/src/main/res/layout/item_message_bot.xml'
]

def process_file(file_path):
    if not os.path.exists(file_path): return
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Add modified comment tracking
    if 'MODIFIED — Stitch MCP' not in content:
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

    # 3. Typography scaling (14sp body, 12sp captions, 18/20sp titles)
    if 'item_message' in file_path:
        content = re.sub(r'textSize="15sp"', 'textSize="14sp"', content)
        content = re.sub(r'textSize="11sp"', 'textSize="12sp"', content)
        # padding inside bubble container
        content = re.sub(r'padding="12dp"', 'padding="16dp"', content)
    
    if 'activity_home' in file_path:
        # Header size reduction
        content = re.sub(r'android:layout_height="120dp"', 'android:layout_height="100dp"', content)
        # Main title sizes
        content = re.sub(r'textSize="24sp"', 'textSize="20sp"', content)
        # Subtitle
        content = re.sub(r'textSize="16sp"', 'textSize="14sp"', content)
    
    if 'item_news_card' in file_path:
        # standardise title size
        content = re.sub(r'textSize="15sp"', 'textSize="18sp"', content)
        content = re.sub(r'textSize="12sp"', 'textSize="12sp"', content) # caption stays 12
    
    if 'activity_gemini_chat' in file_path:
        # stack from end for recyclerview chat
        if 'app:stackFromEnd' not in content:
            content = content.replace('android:clipToPadding="false"', 'android:clipToPadding="false"\n        app:stackFromEnd="true"\n        app:reverseLayout="false"\n        android:overScrollMode="never"')

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

for file in files:
    process_file(file)
