package com.campusai.core.agent

import java.security.MessageDigest

enum class CaesarNodeStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

data class CaesarDagNode(
    val id: String,
    val toolName: String,
    val argumentsHash: String,
    val dependsOn: Set<String>,
    var status: CaesarNodeStatus = CaesarNodeStatus.PENDING,
)

/** Runtime representation of the model's progressively revealed DAG. */
class CaesarDagState {
    private val mutableNodes = linkedMapOf<String, CaesarDagNode>()
    val nodes: List<CaesarDagNode> get() = mutableNodes.values.toList()
    var toolCalls: Int = 0
        private set
    var replans: Int = 0
        private set

    fun begin(toolName: String, argumentsJson: String): CaesarDagNode {
        toolCalls += 1
        require(toolCalls <= MAX_TOOL_CALLS) { "tool_limit_reached" }
        val fingerprint = sha256("$toolName\u0000$argumentsJson").take(16)
        val existing = mutableNodes[fingerprint]
        if (existing != null) {
            existing.status = CaesarNodeStatus.RUNNING
            return existing
        }
        require(mutableNodes.size < MAX_NODES) { "dag_node_limit_reached" }
        val previous = mutableNodes.values.lastOrNull()?.id
        val node = CaesarDagNode(fingerprint, toolName, sha256(argumentsJson), previous?.let(::setOf).orEmpty(), CaesarNodeStatus.RUNNING)
        mutableNodes[fingerprint] = node
        return node
    }

    fun complete(node: CaesarDagNode, success: Boolean) {
        node.status = if (success) CaesarNodeStatus.SUCCEEDED else CaesarNodeStatus.FAILED
        if (!success) {
            replans += 1
            require(replans <= MAX_REPLANS) { "replan_limit_reached" }
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_NODES = 8
        const val MAX_TOOL_CALLS = 12
        const val MAX_REPLANS = 2
    }
}
