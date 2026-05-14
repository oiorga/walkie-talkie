# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class walkie.util.generic.ChannelMux
-keep class walkie.util.generic.ChannelMuxInt
-keep class walkie.util.generic.WTChannelId
-keep class walkie.api.wtchannel.WTChannelKt
-keep class walkie.util.generic.WTChannelMessageType
-keep class walkie.talkie.api.wtchat.ChatGroupIdInt
-keep class walkie.talkie.api.wtchat.ChatGroupListAbs
-keep class walkie.talkie.api.wtchat.ChatGroupType$Local
-keep class walkie.talkie.api.wtchat.ChatGroupType$LocalChatTesting
-keep class walkie.talkie.api.wtchat.ChatGroupType$LocalDebug
-keep class walkie.talkie.api.wtchat.ChatGroupType$RemoteChat
-keep class walkie.talkie.api.wtchat.ChatGroupType$RemoteChatTesting
-keep class walkie.talkie.api.wtchat.ChatGroupType
-keep class walkie.talkie.api.wtchat.ChatMessageAbs
-keep class walkie.talkie.api.wtchat.ChatMessageItemInt
-keep class walkie.talkie.api.wtchat.DiscussionAbs
-keep class walkie.talkie.api.wtchat.DiscussionMapAbs
-keep class walkie.talkie.api.wtchat.ReceiverInt
-keep class walkie.talkie.api.wtchat.SenderInt
-keep class walkie.talkie.api.wtcomm.CommPacket
-keep class walkie.talkie.api.wtdebug.WTDebugInt
-keep class walkie.util.generic.GenericList
-keep class walkie.util.generic.GenericListAbs
-keep class walkie.api.wtgeneric.WTGenericIntKt
-keep class walkie.talkie.api.wtmisc.InfoMap
-keep class walkie.talkie.api.wtmisc.WTNavigation$Back
-keep class walkie.talkie.api.wtmisc.WTNavigation$LocalChatTesting
-keep class walkie.talkie.api.wtmisc.WTNavigation$LocalDebugItem
-keep class walkie.talkie.api.wtmisc.WTNavigation$Main
-keep class walkie.talkie.api.wtmisc.WTNavigation$MainDebug
-keep class walkie.talkie.api.wtmisc.WTNavigation$MainPeers
-keep class walkie.talkie.api.wtmisc.WTNavigation$None
-keep class walkie.talkie.api.wtmisc.WTNavigation$RemoteChat
-keep class walkie.talkie.api.wtmisc.WTNavigation$RemoteChatTesting
-keep class walkie.talkie.api.wtmisc.WTNavigation$Root
-keep class walkie.talkie.api.wtmisc.WTNavigation$TextInfo
-keep class walkie.talkie.api.wtmisc.WTNavigation$WT
-keep class walkie.talkie.api.wtmisc.WTNavigation
-keep class walkie.util.generic.WTRemoteCallMux
-keep class walkie.util.generic.WTRemoteCallMuxInt
-keep class walkie.talkie.api.wtsystem.NodeIdInt
-keep class walkie.api.wtsystem.WtSystemNodeIdIntKt
-keep class walkie.api.wtutil.Logging$Companion
-keep class walkie.api.wtutil.Logging
-keep class walkie.util.VMCollection
-keep class walkie.util.WTLifeCycleLogs
-keep class walkie.api.wtutil.WTLifeCycleObserver
-keep class walkie.api.wtutil.WTUtilBinaryGamesKt
-keep class walkie.api.wtutil.WTUtilKt
# This is generated automatically by the Android Gradle plugin.
-keep class walkie.util.generic.WTCallBackInt
-keep class walkie.talkie.api.wtcomm.WTCommChatMessageIn
-keep class walkie.talkie.api.wtcomm.WTCommChatMessageOut
-keep class walkie.talkie.api.wtcomm.WTCommPacketIn
-keep class walkie.talkie.api.wtcomm.WTCommPacketOut
-keep class walkie.talkie.api.wtcomm.WTMedium$Companion
-keep class walkie.talkie.api.wtcomm.WTMedium$WifiIp
-keep class walkie.talkie.api.wtcomm.WTMedium
-keep class walkie.talkie.api.wtcomm.WTPeerInt
-keep class walkie.talkie.api.wtchat.ChatGroupMapAbs
