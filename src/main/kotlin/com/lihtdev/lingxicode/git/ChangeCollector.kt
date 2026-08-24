package com.lihtdev.lingxicode.git

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager

/**
 * 变更收集：优先取提交对话框上下文中勾选的变更，
 * 无勾选时回退到默认 changelist 的全部本地变更。
 */
object ChangeCollector {

    /**
     * 从动作事件收集将要提交的变更。
     *
     * @param e 提交对话框中的动作事件
     * @return 过滤后的变更列表（跳过 .lock 等无意义文件）
     */
    fun collect(e: AnActionEvent): List<Change> {
        val project = e.project ?: return emptyList()
        // 1. 提交上下文中勾选的变更（非模态 commit workflow / 模态对话框均提供该 key）
        val fromContext = e.getData(VcsDataKeys.CHANGES)?.filterNotNull()
        if (!fromContext.isNullOrEmpty()) {
            return filter(fromContext)
        }
        // 2. 回退：默认 changelist 的全部本地变更
        val defaultList = ChangeListManager.getInstance(project).defaultChangeList
        return filter(defaultList.changes.toList())
    }

    /** 过滤无意义变更（锁文件等） */
    private fun filter(changes: List<Change>): List<Change> =
        changes.filterNot { change ->
            val name = (change.afterRevision ?: change.beforeRevision)?.file?.name
            name?.endsWith(".lock") == true
        }
}
