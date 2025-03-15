package io.recursio.livekitandroid

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class TokenClient {
    private var url: String = "http://brick.recursio.io:3030/token";
    private lateinit var callback: (String?) -> Unit

    constructor(url: String, room: String, identity: String, callback: (String?) -> Unit) {
        this.url = "$url?room=$room&identity=$identity"
        this.callback = callback
    }

    fun fetchToken(): Thread {
        return Thread {
            try {
                val client = URL(this.url).openConnection() as HttpURLConnection
                if (client.responseCode != 200) {
                    callback(null)
                    return@Thread
                }
                val token = client.getInputStream().bufferedReader().use { it.readText() }
                callback(token)

            } catch (e: IOException) {
                Log.i("TokenClient", "Failed to fetch token: ${e.message}")
                callback(null)
            }
        }
    }
}