package org.mozilla.kitfox

/**
 * Chat Message
 *
 * Represents a message in a chat.
 */
class ChatMessage
/**
 * Chat Message constructor
 *
 * @param direction Incoming (true) or outgoing (false)
 * @param messageText The text of the message sent/received
 */(
    val direction: Boolean, val messageText: String
)
