package com.autobook.ui

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Import : Screen("import")
    object Player : Screen("player/{bookId}") {
        fun createRoute(bookId: String) = "player/$bookId"
    }
    object Chapters : Screen("chapters/{bookId}") {
        fun createRoute(bookId: String) = "chapters/$bookId"
    }
    object Settings : Screen("settings")
    object Search : Screen("search")
}
