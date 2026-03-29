package com.kidstune.parent.navigation

import kotlinx.serialization.Serializable

@Serializable
object Login

@Serializable
object Dashboard

@Serializable
data class ProfileDetail(val profileId: String)

@Serializable
data class SearchContent(val profileId: String)

@Serializable
data class ContentList(val profileId: String)

@Serializable
object ImportHistory

@Serializable
object DeviceManagement

@Serializable
object ApprovalQueue

@Serializable
object Settings