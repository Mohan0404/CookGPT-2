package com.example.cookgpt.data

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Call<T>.awaitResponse(): Response<T> =
    suspendCancellableCoroutine { cont ->
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) =
                cont.resume(response)
            override fun onFailure(call: Call<T>, t: Throwable) =
                cont.resumeWithException(t)
        })
        cont.invokeOnCancellation { cancel() }
    }
