package com.lihtdev.lingxicode.git

import com.intellij.openapi.vcs.changes.Change

/**
 * 变更列表 → diff 文本组装（平台胶水层）。
 * 单文件截断见 [DiffFormatter]；整体超过 maxTotalChars 时停止并注明。
 * 单个文件内容读取失败时降级为文件名清单条目，不中断整体。
 */
object DiffTextBuilder {

    /**
     * 组装变更列表的 diff 文本。
     *
     * @param changes 变更列表
     * @param maxTotalChars diff 总字符上限
     */
    fun build(changes: List<Change>, maxTotalChars: Int): String {
        val sb = StringBuilder()
        for (change in changes) {
            val path = filePathOf(change)
            val kind = kindOf(change)
            val section = try {
                val before = change.beforeRevision?.content
                val after = change.afterRevision?.content
                DiffFormatter.formatFileSection(path, kind, before, after)
            } catch (e: Exception) {
                // 内容读取失败：降级为仅记录文件（不中断整体）
                "$path（${kind.label}，读取内容失败：${e.message ?: e.javaClass.simpleName}）\n\n"
            }
            if (sb.length + section.length > maxTotalChars) {
                sb.appendLine("…（diff 总长度超出 $maxTotalChars 字符上限，剩余文件已截断）")
                break
            }
            sb.append(section)
        }
        return sb.toString().ifBlank { "（无可读变更内容）" }
    }

    /** 变更文件路径（优先取变更后 revision，删除文件取变更前） */
    fun filePathOf(change: Change): String =
        (change.afterRevision ?: change.beforeRevision)?.file?.path ?: change.toString()

    /** 平台 Change.Type → 本插件 ChangeKind */
    fun kindOf(change: Change): ChangeKind = when (change.type) {
        Change.Type.NEW -> ChangeKind.NEW
        Change.Type.DELETED -> ChangeKind.DELETED
        Change.Type.MOVED -> ChangeKind.RENAMED
        Change.Type.MODIFICATION -> ChangeKind.MODIFIED
        else -> ChangeKind.UNKNOWN
    }
}
