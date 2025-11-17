package com.example.pushapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LogStore {
    private const val PREF = "logs"
    private const val KEY = "entries"
    private const val LIMIT = 200

    fun getAll(context: Context): List<String> {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) list.add(arr.getString(i))
        list.reverse()
        return list
    }

    fun append(context: Context, text: String) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        arr.put(text)
        val trimmed = JSONArray()
        val start = if (arr.length() > LIMIT) arr.length() - LIMIT else 0
        for (i in start until arr.length()) trimmed.put(arr.getString(i))
        sp.edit().putString(KEY, trimmed.toString()).apply()
    }

    fun clear(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit().putString(KEY, "[]").apply()
    }
}