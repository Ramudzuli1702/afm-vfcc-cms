package com.afmvfcc.app.ui

sealed class Screen(val route: String) {
    object Login      : Screen("login")
    object Sessions   : Screen("sessions")
    object Attendance : Screen("attendance/{sessionId}/{sessionName}") {
        fun route(id: Int, name: String) = "attendance/$id/${name.replace("/","_")}"
    }
    object AddGuest   : Screen("add_guest/{sessionId}") {
        fun route(id: Int) = "add_guest/$id"
    }
    object Settings   : Screen("settings")
}
