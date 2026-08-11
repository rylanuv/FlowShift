package com.flow.shift.core

object AppSortingHelper {

    /**
     * Returns a numerical priority for sorting apps:
     * 0 -> Instagram
     * 1 -> TikTok
     * 2 -> YouTube
     * 3 -> X (Twitter)
     * 4 -> Facebook
     * 5 -> Snapchat
     * 6 -> Other Social Media & Media Apps
     * 100 -> All other apps
     */
    fun getAppPriority(packageName: String, appName: String): Int {
        val pkg = packageName.lowercase()
        val name = appName.lowercase().trim()

        // 1. Instagram
        if (pkg == "com.instagram.android" || pkg == "com.instagram.lite" || name == "instagram" || name == "instagram lite") {
            return 0
        }
        // 2. TikTok
        if (pkg.contains("zhiliaoapp.musically") || pkg.contains("ugc.trill") || name.contains("tiktok", ignoreCase = true)) {
            return 1
        }
        // 3. YouTube
        if (pkg == "com.google.android.youtube" || pkg == "com.google.android.apps.youtube.music" || pkg == "com.google.android.youtube.tv" || name == "youtube" || name == "youtube music" || name == "youtube studio" || name.startsWith("youtube ")) {
            return 2
        }
        // 4. X (Twitter)
        if (pkg.contains("com.twitter.android") || pkg == "com.twitter.android.lite" || pkg.contains("com.x.android") || name == "x" || name == "twitter" || name == "x (formerly twitter)" || name == "twitter lite" || name == "x lite") {
            return 3
        }
        // 5. Facebook
        if (pkg == "com.facebook.katana" || pkg == "com.facebook.lite" || pkg == "com.facebook.orca" || name == "facebook" || name == "facebook lite" || name == "messenger" || name == "meta") {
            return 4
        }
        // 6. Snapchat
        if (pkg == "com.snapchat.android" || name == "snapchat") {
            return 5
        }
        // 7. Other Social Media Apps
        if (isSocialMediaApp(pkg, name)) {
            return 6
        }
        // Everything else
        return 100
    }

    fun isSocialOrTopApp(packageName: String, appName: String): Boolean {
        return getAppPriority(packageName, appName) < 100
    }

    private fun isSocialMediaApp(pkg: String, name: String): Boolean {
        val socialPackages = setOf(
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.linkedin.android",
            "com.instagram.barcelona", // Threads
            "com.discord",
            "org.telegram.messenger",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.tumblr",
            "com.tencent.mm", // WeChat
            "com.sina.weibo",
            "tv.twitch.android.app",
            "com.bereal.ftw",
            "org.joinmastodon.android",
            "xyz.blueskyweb.app",
            "com.quora.android",
            "com.medium.reader",
            "com.tinder",
            "com.bumble.app",
            "com.coffeemeetsbagel",
            "com.grindr.onthego",
            "co.hinge.app",
            "com.viber.voip",
            "jp.naver.line.android",
            "com.skype.raider",
            "com.tencent.mobileqq",
            "com.vkontakte.android",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus",
            "com.hbo.hbonow",
            "com.spotify.music"
        )

        if (socialPackages.any { pkg.contains(it, ignoreCase = true) }) return true

        val socialNames = listOf(
            "twitter", "x", "reddit", "pinterest", "linkedin", "threads", "discord",
            "telegram", "whatsapp", "whatsapp business", "tumblr", "wechat", "weibo", 
            "twitch", "bereal", "mastodon", "bluesky", "quora", "medium", "tinder", 
            "bumble", "hinge", "grindr", "viber", "line", "skype", "qq", "vkontakte", 
            "netflix", "prime video", "disney+", "max", "hulu", "spotify", "kuaishou", "bilibili"
        )

        return socialNames.any { name == it || name.startsWith("$it ") || name.endsWith(" $it") || name.contains(" $it ") }
    }
}
