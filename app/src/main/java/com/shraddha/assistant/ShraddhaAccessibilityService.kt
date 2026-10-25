package com.shraddha.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Opt-in accessibility bridge. Nothing is read or changed until the user enables the service
 * in Android Settings and explicitly invokes a corresponding Shraddha action.
 */
class ShraddhaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately do not store events or scrape content in the background.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    private fun root(): AccessibilityNodeInfo? = try { rootInActiveWindow } catch (_: Exception) { null }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int = 0) {
        if (node == null || depth > 35 || out.size > 400) return
        try {
            if (!node.isPassword) {
                val text = node.text?.toString()?.trim().orEmpty()
                val description = node.contentDescription?.toString()?.trim().orEmpty()
                if (text.isNotBlank()) out.add(text)
                if (description.isNotBlank() && description != text) out.add(description)
            }
            for (i in 0 until node.childCount) collectText(node.getChild(i), out, depth + 1)
        } catch (_: Exception) { }
    }

    private fun findNode(node: AccessibilityNodeInfo?, target: String, editableOnly: Boolean, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 35) return null
        try {
            val value = listOfNotNull(node.text?.toString(), node.contentDescription?.toString(), node.hintText?.toString())
                .joinToString(" ").lowercase(Locale.ROOT)
            val matches = target.isBlank() || value.contains(target.lowercase(Locale.ROOT))
            if (matches && (!editableOnly || node.isEditable)) return node
            for (i in 0 until node.childCount) {
                val result = findNode(node.getChild(i), target, editableOnly, depth + 1)
                if (result != null) return result
            }
        } catch (_: Exception) { }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        return try {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            var parent = node.parent
            var hops = 0
            while (parent != null && hops++ < 8) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                parent = parent.parent
            }
            false
        } catch (_: Exception) { false }
    }

    companion object {
        @Volatile private var activeService: ShraddhaAccessibilityService? = null

        fun isConnected(): Boolean = activeService != null

        fun readVisibleScreen(): String? {
            val service = activeService ?: return null
            val values = mutableListOf<String>()
            service.collectText(service.root(), values)
            return values.asSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                .joinToString("\n").take(14000).ifBlank { null }
        }

        fun clickVisibleLabel(label: String): Boolean {
            val service = activeService ?: return false
            val root = service.root() ?: return false
            val node = service.findNode(root, label.trim(), editableOnly = false)
            return service.clickNode(node)
        }

        fun fillEditableField(match: String, value: String): Boolean {
            val service = activeService ?: return false
            val root = service.root() ?: return false
            val node = service.findNode(root, match.trim(), editableOnly = true) ?: return false
            return try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } catch (_: Exception) { false }
        }

        fun goBack(): Boolean = activeService?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        fun goHome(): Boolean = activeService?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
    }
}
