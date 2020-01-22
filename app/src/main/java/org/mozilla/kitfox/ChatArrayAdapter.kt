package org.mozilla.kitfox

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import java.util.*

/**
 * Chat array adapter
 *
 * Inserts chat messages into a scrollable list view.
 */
class ChatArrayAdapter(private val chatContext: Context, textViewResourceId: Int) :
    ArrayAdapter<ChatMessage>(chatContext, textViewResourceId) {
    private val chatMessageList: MutableList<ChatMessage> =
        ArrayList()

    override fun add(message: ChatMessage) {
        chatMessageList.add(message)
        super.add(message)
    }

    override fun getCount(): Int {
        return chatMessageList.size
    }

    override fun getItem(index: Int): ChatMessage {
        return chatMessageList[index]
    }

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val chatMessageObject = getItem(position)
        val inflater = chatContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val row = inflater.inflate(
            if (chatMessageObject.direction) R.layout.incoming_message else R.layout.outgoing_message,
            parent,
            false
        )
        row.findViewById<TextView>(R.id.messageText).text = chatMessageObject.messageText
        return row
    }
}