package io.github.jonathanxoxox.tvkanaele.core

/** Liest eine M3U/M3U8-Playlist im IPTV-Format (#EXTINF mit tvg-id, tvg-logo, group-title …). */
object M3uParser {
    private val ATTR = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(lines: Sequence<String>): Playlist {
        val channels = mutableListOf<IptvChannel>()
        val epgUrls = mutableListOf<String>()
        val seenKeys = mutableMapOf<String, Int>()

        var attrs: Map<String, String> = emptyMap()
        var name: String? = null
        var group: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        for (raw in lines) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val header = attributes(line)
                    listOfNotNull(header["url-tvg"], header["x-tvg-url"]).forEach { epgUrls += Text.splitList(it) }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    attrs = attributes(line)
                    name = displayName(line)
                    group = attrs["group-title"]
                    userAgent = attrs["user-agent"] ?: attrs["http-user-agent"]
                    referrer = attrs["referrer"] ?: attrs["http-referrer"]
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim()
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    when (opt.substringBefore('=').trim().lowercase()) {
                        "http-user-agent" -> userAgent = opt.substringAfter('=').trim()
                        "http-referrer", "http-referer" -> referrer = opt.substringAfter('=').trim()
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val channelName = name?.takeIf { it.isNotBlank() } ?: attrs["tvg-name"] ?: line
                    val channelGroup = group.orEmpty()
                    val baseKey = "m:${Text.fold(channelGroup)}|${Text.fold(channelName)}"
                    val n = (seenKeys[baseKey] ?: 0) + 1
                    seenKeys[baseKey] = n
                    channels += IptvChannel(
                        key = if (n == 1) baseKey else "$baseKey#$n",
                        name = channelName,
                        streamUrl = line,
                        group = channelGroup,
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() },
                        userAgent = userAgent,
                        referrer = referrer,
                    )
                    attrs = emptyMap(); name = null; group = null; userAgent = null; referrer = null
                }
            }
        }
        return Playlist(channels, epgUrls.distinct())
    }

    private fun attributes(line: String): Map<String, String> =
        ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

    /** Der Name steht hinter dem ersten Komma, das nicht in Anführungszeichen liegt. */
    private fun displayName(line: String): String {
        var inQuotes = false
        line.forEachIndexed { i, c ->
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return line.substring(i + 1).trim()
        }
        return ""
    }
}
