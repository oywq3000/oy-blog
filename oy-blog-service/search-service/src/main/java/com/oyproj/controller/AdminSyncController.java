package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.scheduler.IndexReconciler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 索引管理接口
 * 提供手动重建索引和查询同步状态的能力
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminSyncController {

    private final IndexReconciler indexReconciler;

    /**
     * 手动触发全量索引重建
     */
    @PostMapping("/admin/reindex")
    public Result<Map<String, Object>> reindex() {
        log.info("收到手动重建索引请求");
        new Thread(() -> indexReconciler.reconcile(), "manual-reindex").start();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "全量重建已触发，请稍后通过 /admin/sync-status 查看进度");
        return Result.ok(result);
    }

    /**
     * 查询最近一次对账状态
     */
    @GetMapping("/admin/sync-status")
    public Result<IndexReconciler.SyncStats> syncStatus() {
        IndexReconciler.SyncStats stats = indexReconciler.getLastSyncStats();
        return Result.ok(stats);
    }
}
