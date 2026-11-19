package com.pil97.ticketing.support.outbox;

import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe.Evidence;
import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe.LockRow;
import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe.Verdict;
import com.pil97.ticketing.support.outbox.PrimaryKeyLockProbe.WaitRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrimaryKeyLockProbe 판정 규칙 - 인위 구성 입력 검증 (DB 관찰 아님)
 */
class PrimaryKeyLockProbeTest {

  private static final String T = "outbox_event";
  private static final String TABLE = "`db`.`outbox_event`";
  private static final String HOLDER = "100";
  private static final String WAITER = "101";
  private static final String PROBE = "102";
  private static final String WAITER_LOCK = "101:19:3:154";
  private static final String PROBE_LOCK = "102:19:3:154";

  private static LockRow lock(String lockId, String trx, String index, String space, String page, String rec) {
    return new LockRow(lockId, trx, "RECORD", TABLE, index, space, page, rec);
  }

  private static LockRow waiterLock() {
    return lock(WAITER_LOCK, WAITER, "PRIMARY", "19", "3", "154");
  }

  private static LockRow probeLock() {
    return lock(PROBE_LOCK, PROBE, "PRIMARY", "19", "3", "154");
  }

  /** 모든 연결이 맞는 기준 입력 */
  private static Builder base() {
    return new Builder();
  }

  private static final class Builder {
    boolean probePkIsTarget = true;
    String probeTrx = PROBE;
    String probeState = "LOCK WAIT";
    String probeRequested = PROBE_LOCK;
    boolean probeQueryIsProbe = true;
    LockRow probeLock = probeLock();
    List<WaitRow> probeWaits = List.of(new WaitRow(PROBE, PROBE_LOCK, HOLDER), new WaitRow(PROBE, PROBE_LOCK, WAITER));
    String holderTrx = HOLDER;
    String initialWaiterTrx = WAITER;
    String initialWaiterLock = WAITER_LOCK;
    String waiterTrx = WAITER;
    String waiterState = "LOCK WAIT";
    String waiterRequested = WAITER_LOCK;
    LockRow waiterLock = waiterLock();
    List<WaitRow> waiterWaits = List.of(new WaitRow(WAITER, WAITER_LOCK, HOLDER));

    Evidence build() {
      return new Evidence(probePkIsTarget, probeTrx, probeState, probeRequested, probeQueryIsProbe, probeLock, probeWaits,
        holderTrx, initialWaiterTrx, initialWaiterLock, waiterTrx, waiterState, waiterRequested, waiterLock, waiterWaits);
    }

    Verdict verdict() {
      return PrimaryKeyLockProbe.evaluate(T, build());
    }
  }

  // ===== 위치·인덱스·바인딩·연결 판정 규칙 =====

  @Test
  @DisplayName("기준: 정확한 기본키 probe의 요청 잠금과 waiter 요청 잠금이 같은 PRIMARY RECORD 위치면 인정")
  void acceptsSamePosition() {
    assertThat(base().verdict()).isEqualTo(Verdict.ACCEPT);
  }

  @Test
  @DisplayName("다른 기본키 probe(바인딩 불일치)는 위치가 같아도 거부")
  void rejectsOtherPrimaryKeyBinding() {
    Builder b = base();
    b.probePkIsTarget = false;
    assertThat(b.verdict()).isEqualTo(Verdict.REJECT);
  }

  @Test
  @DisplayName("다른 위치(rec·page·space 각각)는 거부")
  void rejectsOtherPosition() {
    for (String[] pos : List.of(new String[]{"19", "3", "155"}, new String[]{"19", "4", "154"}, new String[]{"20", "3", "154"})) {
      Builder b = base();
      b.probeLock = lock(PROBE_LOCK, PROBE, "PRIMARY", pos[0], pos[1], pos[2]);
      assertThat(b.verdict()).as("position %s:%s:%s", pos[0], pos[1], pos[2]).isEqualTo(Verdict.REJECT);
    }
  }

  @Test
  @DisplayName("다른 인덱스(보조 인덱스)는 거부")
  void rejectsOtherIndex() {
    Builder both = base();
    both.probeLock = lock(PROBE_LOCK, PROBE, "idx_outbox_event_status_id", "19", "4", "2");
    both.waiterLock = lock(WAITER_LOCK, WAITER, "idx_outbox_event_status_id", "19", "4", "2");
    assertThat(both.verdict()).isNotEqualTo(Verdict.ACCEPT);
    Builder waiterOnly = base();
    waiterOnly.waiterLock = lock(WAITER_LOCK, WAITER, "idx_outbox_event_status_id", "19", "3", "154");
    assertThat(waiterOnly.verdict()).isNotEqualTo(Verdict.ACCEPT);
  }

  @Test
  @DisplayName("위치 NULL은 거부 (양쪽 NULL 포함)")
  void rejectsNullPosition() {
    Builder both = base();
    both.probeLock = lock(PROBE_LOCK, PROBE, "PRIMARY", null, null, null);
    both.waiterLock = lock(WAITER_LOCK, WAITER, "PRIMARY", null, null, null);
    assertThat(both.verdict()).isNotEqualTo(Verdict.ACCEPT);
    Builder rec = base();
    rec.probeLock = lock(PROBE_LOCK, PROBE, "PRIMARY", "19", "3", null);
    rec.waiterLock = lock(WAITER_LOCK, WAITER, "PRIMARY", "19", "3", null);
    assertThat(rec.verdict()).isNotEqualTo(Verdict.ACCEPT);
  }

  @Test
  @DisplayName("probe 연결·trx·LOCK WAIT·probe 문장·대기 행·요청 잠금 중 하나라도 없으면 인정하지 않음 (위치 일치만으로 인정 금지)")
  void rejectsWithoutProbeLinkage() {
    Builder noTrx = base();
    noTrx.probeTrx = null;
    Builder notWaiting = base();
    notWaiting.probeState = "RUNNING";
    Builder otherQuery = base();
    otherQuery.probeQueryIsProbe = false;
    Builder noWaitRow = base();
    noWaitRow.probeWaits = List.of();
    Builder noLock = base();
    noLock.probeLock = null;
    for (Builder b : List.of(noTrx, notWaiting, otherQuery, noWaitRow, noLock)) {
      assertThat(b.verdict()).isEqualTo(Verdict.INCONSISTENT);
    }
  }

  @Test
  @DisplayName("probe 차단자가 holder·waiter 외 trx이면 인정하지 않음, waiter가 holder에 막혀 있지 않거나 대기 중이 아니면 인정하지 않음, 차단자가 waiter뿐이면 인정")
  void rejectsUnexpectedRelations() {
    Builder other = base();
    other.probeWaits = List.of(new WaitRow(PROBE, PROBE_LOCK, "999"));
    assertThat(other.verdict()).isEqualTo(Verdict.INCONSISTENT);
    Builder notByHolder = base();
    notByHolder.waiterWaits = List.of(new WaitRow(WAITER, WAITER_LOCK, "999"));
    assertThat(notByHolder.verdict()).isEqualTo(Verdict.INCONSISTENT);
    Builder waiterRunning = base();
    waiterRunning.waiterState = "RUNNING";
    assertThat(waiterRunning.verdict()).isEqualTo(Verdict.INCONSISTENT);
    Builder behindWaiter = base();
    behindWaiter.probeWaits = List.of(new WaitRow(PROBE, PROBE_LOCK, WAITER));
    assertThat(behindWaiter.verdict()).isEqualTo(Verdict.ACCEPT);
  }

  @Test
  @DisplayName("TABLE 잠금이나 다른 테이블은 거부")
  void rejectsTableLockOrOtherTable() {
    Builder tableLock = base();
    tableLock.probeLock = new LockRow(PROBE_LOCK, PROBE, "TABLE", TABLE, null, null, null, null);
    assertThat(tableLock.verdict()).isNotEqualTo(Verdict.ACCEPT);
    Builder otherTable = base();
    otherTable.probeLock = new LockRow(PROBE_LOCK, PROBE, "RECORD", "`db`.`notification`", "PRIMARY", "19", "3", "154");
    assertThat(otherTable.verdict()).isNotEqualTo(Verdict.ACCEPT);
  }

  // ===== 같은 트랜잭션·요청 잠금 연결 =====

  @Test
  @DisplayName("waiter 트랜잭션이 처음 관찰한 트랜잭션과 다르면 인정하지 않음")
  void rejectsWaiterTransactionChanged() {
    Builder b = base();
    b.initialWaiterTrx = "200";
    assertThat(b.verdict()).isEqualTo(Verdict.INCONSISTENT);
  }

  @Test
  @DisplayName("waiter 요청 잠금이 처음 관찰한 요청 잠금과 다르면 인정하지 않음")
  void rejectsWaiterRequestedLockChanged() {
    Builder b = base();
    b.initialWaiterLock = "101:19:3:155";
    assertThat(b.verdict()).isEqualTo(Verdict.INCONSISTENT);
  }

  @Test
  @DisplayName("잠금 행의 lock_trx_id가 그 요청 트랜잭션이 아니면 인정하지 않음 (probe·waiter 각각)")
  void rejectsLockTrxMismatch() {
    Builder probe = base();
    probe.probeLock = lock(PROBE_LOCK, "555", "PRIMARY", "19", "3", "154");
    assertThat(probe.verdict()).isEqualTo(Verdict.INCONSISTENT);
    Builder waiter = base();
    waiter.waiterLock = lock(WAITER_LOCK, "555", "PRIMARY", "19", "3", "154");
    assertThat(waiter.verdict()).isEqualTo(Verdict.INCONSISTENT);
  }

  @Test
  @DisplayName("대기 행의 요청 잠금이 trx_requested_lock_id와 다르면 인정하지 않음 (probe·waiter 각각, 다른 조회 시점 혼합 방지)")
  void rejectsWaitRowLockMismatch() {
    Builder probe = base();
    probe.probeWaits = List.of(new WaitRow(PROBE, PROBE_LOCK, HOLDER), new WaitRow(PROBE, "102:19:3:155", WAITER));
    assertThat(probe.verdict()).isEqualTo(Verdict.INCONSISTENT);
    Builder waiter = base();
    waiter.waiterWaits = List.of(new WaitRow(WAITER, "101:19:3:999", HOLDER));
    assertThat(waiter.verdict()).isEqualTo(Verdict.INCONSISTENT);
    Builder probeLockId = base();
    probeLockId.probeRequested = "102:19:3:155";
    assertThat(probeLockId.verdict()).isEqualTo(Verdict.INCONSISTENT);
  }

  // ===== timeout 판정 =====

  @Test
  @DisplayName("잠금 대기 timeout은 SQLSTATE HY000과 vendor code 1205가 모두 맞을 때만")
  void lockWaitTimeoutBySqlStateAndVendorCode() {
    assertThat(PrimaryKeyLockProbe.isLockWaitTimeout(new SQLException("Lock wait timeout exceeded", "HY000", 1205))).isTrue();
    assertThat(PrimaryKeyLockProbe.isLockWaitTimeout(new SQLException("x", "42000", 1205))).isFalse();
    assertThat(PrimaryKeyLockProbe.isLockWaitTimeout(new SQLException("x", "40001", 1213))).isFalse();
    assertThat(PrimaryKeyLockProbe.isLockWaitTimeout(new SQLException("x", "HY000", 1064))).isFalse();
    assertThat(PrimaryKeyLockProbe.isLockWaitTimeout(null)).isFalse();
  }
}
