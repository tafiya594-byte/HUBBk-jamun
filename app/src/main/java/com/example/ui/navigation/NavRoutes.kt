package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data class ChatRoom(val chatId: String) : AppRoute

    @Serializable
    data class GroupInfo(val chatId: String) : AppRoute

    @Serializable
    data class StatusViewer(val ownerUid: String) : AppRoute

    @Serializable
    data class ActiveCall(val callId: String) : AppRoute
}
