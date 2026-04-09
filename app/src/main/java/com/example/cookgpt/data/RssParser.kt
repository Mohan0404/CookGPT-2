package com.example.cookgpt.data

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class RssParser {
    fun parse(xml: String, source: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val parser = Xml.newPullParser()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            
            var eventType = parser.eventType
            var currentItem: MutableRssItem? = null
            var tagText = "" // // FIX: Used as a buffer for text accumulation

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            currentItem = MutableRssItem()
                        } else if (currentItem != null) {
                            if (tagName.contains("content", ignoreCase = true) || tagName.equals("enclosure", ignoreCase = true)) {
                                val url = parser.getAttributeValue(null, "url")
                                if (!url.isNullOrEmpty()) currentItem.imageUrl = url
                            }
                        }
                        tagText = "" // // FIX: Clear buffer for new tag
                    }
                    XmlPullParser.TEXT -> {
                        tagText += parser.text // // FIX: Accumulate text nodes (handles CDATA and entities)
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            currentItem?.let { items.add(it.toRssItem(source)) }
                            currentItem = null
                        } else if (currentItem != null) {
                            when {
                                tagName.equals("title", ignoreCase = true) -> currentItem.title = tagText.trim()
                                tagName.equals("link", ignoreCase = true) -> currentItem.link = tagText.trim()
                                tagName.equals("description", ignoreCase = true) -> currentItem.description = tagText.trim()
                                tagName.equals("pubDate", ignoreCase = true) -> currentItem.pubDate = tagText.trim()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("RssParser", "Error parsing $source: ${e.message}")
        }
        return items
    }

    private class MutableRssItem {
        var title: String = ""
        var link: String = ""
        var description: String = ""
        var pubDate: String = ""
        var imageUrl: String? = null

        fun toRssItem(source: String): RssItem {
            return RssItem(
                title = title.trim(),
                link = link.trim(),
                description = cleanDescription(description),
                pubDate = formatDisplayDate(pubDate),
                imageUrl = imageUrl ?: extractImageUrl(description),
                source = source
            )
        }

        private fun cleanDescription(desc: String): String {
            // // FIX: Comprehensive HTML stripping and entity cleaning
            return desc.replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .trim()
        }

        private fun extractImageUrl(desc: String): String? {
            val match = Regex("src=\"([^\"]+)\"").find(desc)
            return match?.groupValues?.get(1)
        }

        private fun formatDisplayDate(dateStr: String): String {
            return try { dateStr.split(" ").take(4).joinToString(" ") } catch (e: Exception) { dateStr }
        }
    }
}
