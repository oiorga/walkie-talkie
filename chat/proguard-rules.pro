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

-keep class walkie.chat.ChatDiscussion
-keep class walkie.chat.ChatDiscussionMap
-keep class walkie.chat.ChatGroupId
-keep class walkie.chat.ChatGroupList
-keep class walkie.chat.ChatGroupMap
-keep class walkie.chat.ChatMessage
-keep class walkie.chat.ChatMessageItem$Builder
-keep class walkie.chat.ChatMessageItem
-keep class walkie.chat.ChatMessageItemList
-keep class walkie.chat.DiscussionItemList
-keep class walkie.chat.Receiver
-keep class walkie.chat.Sender
-keep class walkie.comm.WTComm
-keep class walkie.comm.WTCommPacketKt
-keep class walkie.comm.WTCommPeerInfo
-keep class walkie.comm.prm.WTPRMComm
-keep class walkie.util.generic.ChannelMux
-keep class walkie.util.generic.ChannelMuxInt
-keep class walkie.util.generic.WTChannelId
-keep class walkie.glue.wtchannel.WTChannelKt
-keep class walkie.util.generic.WTChannelMessageType
-keep class walkie.glue.wtchat.ChatGroupIdInt
-keep class walkie.glue.wtchat.ChatGroupListAbs
-keep class walkie.glue.wtchat.ChatGroupType$Local
-keep class walkie.glue.wtchat.ChatGroupType$LocalChatTesting
-keep class walkie.glue.wtchat.ChatGroupType$LocalDebug
-keep class walkie.glue.wtchat.ChatGroupType$RemoteChat
-keep class walkie.glue.wtchat.ChatGroupType$RemoteChatTesting
-keep class walkie.glue.wtchat.ChatGroupType
-keep class walkie.glue.wtchat.ChatMessageAbs
-keep class walkie.glue.wtchat.ChatMessageItemInt
-keep class walkie.glue.wtchat.DiscussionAbs
-keep class walkie.glue.wtchat.DiscussionMapAbs
-keep class walkie.glue.wtchat.ReceiverInt
-keep class walkie.glue.wtchat.SenderInt
-keep class walkie.glue.wtcomm.CommPacket
-keep class walkie.glue.wtdebug.WTDebugInt
-keep class walkie.util.generic.GenericList
-keep class walkie.util.generic.GenericListAbs
-keep class walkie.glue.wtgeneric.WTGenericIntKt
-keep class walkie.glue.wtmisc.InfoMap
-keep class walkie.glue.wtmisc.WTNavigation$Back
-keep class walkie.glue.wtmisc.WTNavigation$LocalChatTesting
-keep class walkie.glue.wtmisc.WTNavigation$LocalDebugItem
-keep class walkie.glue.wtmisc.WTNavigation$Main
-keep class walkie.glue.wtmisc.WTNavigation$MainDebug
-keep class walkie.glue.wtmisc.WTNavigation$MainPeers
-keep class walkie.glue.wtmisc.WTNavigation$None
-keep class walkie.glue.wtmisc.WTNavigation$RemoteChat
-keep class walkie.glue.wtmisc.WTNavigation$RemoteChatTesting
-keep class walkie.glue.wtmisc.WTNavigation$Root
-keep class walkie.glue.wtmisc.WTNavigation$TextInfo
-keep class walkie.glue.wtmisc.WTNavigation$WT
-keep class walkie.glue.wtmisc.WTNavigation
-keep class walkie.util.generic.WTRemoteCallMux
-keep class walkie.util.generic.WTRemoteCallMuxInt
-keep class walkie.glue.wtsystem.NodeIdInt
-keep class walkie.glue.wtsystem.WtSystemNodeIdIntKt
-keep class walkie.glue.wtutil.Logging$Companion
-keep class walkie.glue.wtutil.Logging
-keep class walkie.util.VMCollection
-keep class walkie.util.WTLifeCycleLogs
-keep class walkie.glue.wtutil.WTLifeCycleObserver
-keep class walkie.glue.wtutil.WTUtilBinaryGamesKt
-keep class walkie.glue.wtutil.WTUtilKt
# This is generated automatically by the Android Gradle plugin.
-keep class walkie.util.generic.WTCallBackInt
-keep class walkie.glue.wtcomm.WTCommChatMessageIn
-keep class walkie.glue.wtcomm.WTCommChatMessageOut
-keep class walkie.glue.wtcomm.WTCommPacketIn
-keep class walkie.glue.wtcomm.WTCommPacketOut
-keep class walkie.glue.wtcomm.WTMedium$Companion
-keep class walkie.glue.wtcomm.WTMedium$WifiIp
-keep class walkie.glue.wtcomm.WTMedium
-keep class walkie.glue.wtcomm.WTPeerInt
-keep class walkie.glue.wtchat.ChatGroupMapAbs
