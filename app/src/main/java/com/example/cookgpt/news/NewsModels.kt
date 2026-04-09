package com.example.cookgpt.news

data class NewsResponse( val status: String = "", val totalResults: Int = 0, val articles: List<NewsArticle> = emptyList() ) 
data class NewsArticle( val title: String = "", val description: String = "", val url: String = "", val urlToImage: String? = null, val publishedAt: String = "", val source: NewsSource = NewsSource() ) 
data class NewsSource( val id: String? = null, val name: String = "" )
