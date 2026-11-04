package com.pil97.ticketing.support.consistency;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 테스트 전용 DataSource 래퍼
 * - 반환하는 커넥션과 PreparedStatement를 JDK Proxy로 감싸 SqlObserver에 SQL 이벤트를 알리고 CommitGate를 적용한다
 * - 각 이벤트에는 서버 connection ID(MariaDB thread id)를 함께 기록해 PROCESSLIST·INNODB_TRX 행과 연결한다
 * - 실행이 SQLException으로 끝나면 vendor errorCode·SQLState를 EXEC_END에 기록한다 (예외는 그대로 다시 던진다)
 * - StatementGate: gated thread의 실행 시작 직전 거부 여부를 확인하고, 정상 반환 직후(EXEC_END 기록 이후) pause를 적용한다
 * - 세션 진단: 물리 커넥션(connection ID)을 처음 받을 때 1회, 감싸지 않은 원래 커넥션으로
 *   SESSION 변수(innodb_snapshot_isolation, transaction_isolation)를 조회해 SqlObserver에 기록한다
 *   - 테이블을 읽지 않는 변수 조회이며, 트랜잭션 시작 전(커넥션 반환 시점)에 수행한다
 *   - 관찰 이벤트로 기록하지 않으므로 시나리오의 SQL 순서에 포함되지 않는다
 * - 그 외 모든 호출은 원래 객체에 그대로 위임한다
 */
public class ObservingDataSource extends DelegatingDataSource {

  public ObservingDataSource(DataSource target) {
    super(target);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return wrap(super.getConnection());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return wrap(super.getConnection(username, password));
  }

  private Connection wrap(Connection target) {
    long connectionId = serverConnectionId(target);
    if (connectionId >= 0 && !SqlObserver.hasSessionDiagnostic(connectionId)) {
      SqlObserver.recordSessionDiagnostic(diagnoseSession(target, connectionId));
    }
    return (Connection) Proxy.newProxyInstance(
      Connection.class.getClassLoader(),
      new Class<?>[]{Connection.class},
      new ConnectionHandler(target, connectionId));
  }

  /**
   * 실제 커넥션의 SESSION 변수 조회 - 실패하면 사유를 기록한다 (requireSessionDiagnostics가 PRECONDITION으로 처리)
   */
  private static SqlObserver.SessionDiagnostic diagnoseSession(Connection target, long connectionId) {
    String capturedBy = Thread.currentThread().getName();
    try (Statement statement = target.createStatement();
         ResultSet rs = statement.executeQuery(
           "select @@session.innodb_snapshot_isolation, @@session.transaction_isolation")) {
      if (!rs.next()) {
        return new SqlObserver.SessionDiagnostic(connectionId, capturedBy, null, null, "no row");
      }
      return new SqlObserver.SessionDiagnostic(connectionId, capturedBy, rs.getString(1), rs.getString(2), null);
    } catch (SQLException | RuntimeException e) {
      return new SqlObserver.SessionDiagnostic(connectionId, capturedBy, null, null, e.toString());
    }
  }

  /**
   * 서버 connection ID 조회 - 쿼리를 보내지 않고 드라이버가 보유한 thread id(org.mariadb.jdbc.Connection#getThreadId)를 읽는다
   * - 드라이버는 runtimeOnly 의존성이라 컴파일 의존 없이 reflection으로 접근한다 (build.gradle 변경 없음)
   * - 얻지 못하면 -1 (LockWaitProbe가 HARNESS 오류로 처리)
   */
  private static long serverConnectionId(Connection target) {
    try {
      Class<?> mariadbConnection = Class.forName("org.mariadb.jdbc.Connection");
      Object unwrapped = target.unwrap(mariadbConnection);
      return (long) mariadbConnection.getMethod("getThreadId").invoke(unwrapped);
    } catch (ReflectiveOperationException | SQLException | RuntimeException e) {
      return -1L;
    }
  }

  private static Object invokeTarget(Object target, Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }

  private static final class ConnectionHandler implements InvocationHandler {

    private final Connection target;
    private final long connectionId;
    // 현재 트랜잭션 동안 gate 대상 SQL을 실행했는지 여부
    private volatile boolean armed;

    private ConnectionHandler(Connection target, long connectionId) {
      this.target = target;
      this.connectionId = connectionId;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();

      if ((name.equals("prepareStatement") || name.equals("prepareCall"))
        && args != null && args.length > 0 && args[0] instanceof String sql) {
        SqlObserver.record(SqlObserver.Phase.PREPARED, connectionId, sql, 0L);
        Object statement = invokeTarget(target, method, args);
        Class<?> type = name.equals("prepareCall") ? CallableStatement.class : PreparedStatement.class;
        return Proxy.newProxyInstance(
          type.getClassLoader(),
          new Class<?>[]{type},
          new StatementHandler(statement, sql, this));
      }

      if (name.equals("commit")) {
        SqlObserver.CommitGate gate = SqlObserver.currentGate();
        if (gate != null && gate.matchesThread()) {
          // gated thread의 모든 commit은 gate 상태를 거친다:
          // abort·timeout·interrupt 이후면 거부, 대상 SQL 실행 후 첫 commit이면 pause 후 RELEASED일 때만 진행
          gate.beforeCommit(armed);
        }
      }

      if (name.equals("commit") || name.equals("rollback") || name.equals("close")) {
        armed = false;
      }

      return invokeTarget(target, method, args);
    }

    void onExecuteStart(String sql) {
      SqlObserver.CommitGate gate = SqlObserver.currentGate();
      if (gate != null && gate.matchesThread()) {
        // StatementGate가 abort·timeout·interrupt 상태이면 실행 자체를 거부한다
        gate.beforeStatement();
        if (gate.matchesSql(sql)) {
          armed = true;
        }
      }
    }

    void onExecuteReturned(String sql) {
      SqlObserver.CommitGate gate = SqlObserver.currentGate();
      if (gate != null && gate.matchesThread()) {
        gate.afterStatement(sql);
      }
    }
  }

  private static final class StatementHandler implements InvocationHandler {

    private final Object target;
    private final String sql;
    private final ConnectionHandler connection;

    private StatementHandler(Object target, String sql, ConnectionHandler connection) {
      this.target = target;
      this.sql = sql;
      this.connection = connection;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (!method.getName().startsWith("execute")) {
        return invokeTarget(target, method, args);
      }
      connection.onExecuteStart(sql);
      long execId = SqlObserver.record(SqlObserver.Phase.EXEC_START, connection.connectionId, sql, 0L);
      Object result;
      try {
        result = invokeTarget(target, method, args);
      } catch (SQLException e) {
        SqlObserver.recordEnd(connection.connectionId, sql, execId, e);
        throw e;
      } catch (Throwable t) {
        SqlObserver.recordEnd(connection.connectionId, sql, execId, null);
        throw t;
      }
      SqlObserver.recordEnd(connection.connectionId, sql, execId, null);
      connection.onExecuteReturned(sql);
      return result;
    }
  }
}
