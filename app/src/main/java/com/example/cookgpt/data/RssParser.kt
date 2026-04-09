package com.example.cookgpt.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class RssParser {
    fun parse(xml: String, source: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var currentItem: MutableRssItem? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "item") {
                        currentItem = MutableRssItem()
                    } else if (currentItem != null) {
                        when (name) {
                            "title" -> currentItem.title = parser.nextText()
                            "link" -> currentItem.link = parser.nextText()
                            "description" -> currentItem.description = parser.nextText()
                            "pubDate" -> currentItem.pubDate = parser.nextText()
                            "media:content", "enclosure" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (url != null) currentItem.imageUrl = url
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "item" && currentItem != null) {
                        items.add(currentItem.toRssItem(source))
                        currentItem = null
                    }
                }
            }
            eventType = parser.next()
        }
        return items
    }

    private class MutableRssItem {
        var title: String = ""
        var link: String = ""
        var description: String = ""
        var pubDate: String = ""
        var imageUrl: String? = null

        fun toRssItem(source: String) = RssItem(
            title = title,
            link = link,
            description = cleanDescription(description),
            pubDate = pubDate,
            imageUrl = imageUrl ?: extractImageUrl(description),
            source = source
        )

        private fun cleanDescription(desc: String): String {
            // Remove HTML tags from description
            return desc.replace(Regex("<[^>]*>"), "").trim()
        }

        private fun extractImageUrl(desc: String): String? {
            // Try to find an image source in the description if it's HTML
            val match = Regex("src=\"([^\"]+)\"").find(desc)
            return match?.groupValues?.get(1)
        }
    }
}
