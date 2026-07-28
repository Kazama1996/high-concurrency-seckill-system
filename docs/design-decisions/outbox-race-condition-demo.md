# Outbox Polling 的 TOCTOU 問題與驗證

## 問題

多個 instance/thread 併發跑 outbox polling 時,如果用「SELECT 找待處理事件 → 逐筆 UPDATE 標記 SENDING」這種兩段式寫法,SELECT 跟 UPDATE 之間存在時間差(TOCTOU), 會導致同一筆 event 被多個 worker 同時撈到、重複發送。

## 驗證用的 Test Case

> 對應正式測試檔案: `src/test/java/com/kazama/redis_cache_demo/order/repository/OrderCreatedOutboxClaimPendingBatchIntegrationTest.java` 方法:`concurrentClaims_produceDisjointClaims_andClaimEveryPendingRowExactlyOnce`
>
> 正式版本的斷言為 `assertThat(Collections.disjoint(set1, set2)).isTrue()`,固定 呼叫 `repository.claimPendingBatch(total)`,也就是下方「After」的版本。 下方展示的 naive 版本呼叫方式僅用於本地驗證,未進版控,正式檔案裡的斷言與呼叫 對象皆維持不變。

用兩個 thread 透過 `CountDownLatch` 同步起跑,對同一批 200 筆 seed 好的 PENDING row 併發呼叫 claim 方法,檢查兩個 thread 撈到的 id 集合是否有重疊。

下面這份 test 裡,`claimTask` 呼叫的 claim 方法可以互換替代——把 `naiveClaim(total)` 換成 `repository.claimPendingBatch(total)`,就能分別驗證 naive(SELECT-then-UPDATE)版本跟 CTE + `FOR UPDATE SKIP LOCKED` 版本的行為差異。

```java
@Test
void concurrentClaims_produceDisjointClaims_andClaimEveryPendingRowExactlyOnce() throws Exception {
    // ...
    Callable<List<Long>> claimTask = () -> {
        ready.countDown();
        start.await();
        // 下面這行可替換:
        //   naiveClaim(total)                      → 驗證 before(naive SELECT-then-UPDATE)
        //   repository.claimPendingBatch(total)    → 驗證 after(CTE + FOR UPDATE SKIP LOCKED)
        return naiveClaim(total).stream()
                .map(OrderCreatedOutbox::getId)
                .toList();
    };
    // ... (斷言邏輯,兩邊共用:Collections.disjoint(set1, set2) 應為 true)
}
```

## Before:naive SELECT-then-UPDATE

```java
// 僅為展示問題而暫時寫的,未進版控
@Transactional
@Query(value = "SELECT * FROM order_created_outbox WHERE status IN ('PENDING','FAILED') ORDER BY created_at ASC LIMIT :limit", nativeQuery = true)
List<OrderCreatedOutbox> selectPendingNaive(@Param("limit") int limit);

@Modifying
@Transactional
@Query(value = "UPDATE order_created_outbox SET status = 'SENDING', updated_at = now() WHERE id = :id", nativeQuery = true)
void markSendingNaive(@Param("id") Long id);

List<OrderCreatedOutbox> naiveClaim(int limit) {
    List<OrderCreatedOutbox> candidates = repository.selectPendingNaive(limit);
    try {  
	    Thread.sleep(50);  // 放大競爭視窗,讓 race 穩定重現
	} catch (InterruptedException e) {  
	    Thread.currentThread().interrupt();  
	}
    candidates.forEach(o -> repository.markSendingNaive(o.getId()));
    return candidates;
}
```

**結果**:

```
[naive-race-demo] thread1 claimed=200, thread2 claimed=200, overlap=200
```

兩個 thread 各自撈到完整的 200 筆,完全重疊,`Collections.disjoint(set1, set2)` 為 `false`,斷言失敗——證實 SELECT 與 UPDATE 分離時,race 是必然發生,不是偶發機率 問題。

## After:CTE + FOR UPDATE SKIP LOCKED

```java
@Transactional
@Query(value = """
    WITH claimed AS (
        SELECT id FROM order_created_outbox
        WHERE status IN ('PENDING', 'FAILED')
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    )
    UPDATE order_created_outbox o
    SET status = 'SENDING', updated_at = now()
    FROM claimed c
    WHERE o.id = c.id
    RETURNING o.*
    """, nativeQuery = true)
List<OrderCreatedOutbox> claimPendingBatch(@Param("limit") int limit);
```

**結果**:

```
[cte-skip-locked-demo] thread1 claimed=200, thread2 claimed=0, overlap=0
```

兩個 thread 的 claim 結果互斥(`Collections.disjoint(set1, set2)` 為 `true`), 聯集等於全部 200 筆 seed 資料,且都正確標記為 SENDING。thread2 拿到 0 筆並非 異常——在 `SKIP LOCKED` 語義下,一個 thread 搶到全部、另一個完全落空是合法結果, 因為 `SKIP LOCKED` 是跳過已鎖定的 row 而非排隊等待,不保證均分。`FOR UPDATE SKIP LOCKED` 讓「挑選候選」與「標記狀態」合併成單一原子語句,消除了 SELECT/UPDATE 之間的時間差。

## 結論

同一份併發測試,重複 claim 筆數從 naive 版本的 200/200(100%)降至 CTE + SKIP LOCKED 版本的 0。