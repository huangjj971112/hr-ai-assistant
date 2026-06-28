package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.security.CurrentUserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 MCP Tool 的考勤子 Agent 实现。
 *
 * <p>只调用 query_attendance，不直接访问 AttendanceService。这样 Multi-Agent
 * 与普通 MCP Agent 共享同一套权限、审计和错误码。</p>
 */
@Service
public class McpAttendanceAgent extends AbstractMcpSubAgent implements AttendanceAgent {

    private static final Set<String> SCOPES = Set.of("attendance:records:read");

    private final HrMcpCaller hrMcpCaller;

    public McpAttendanceAgent(
            HrMcpCaller hrMcpCaller,
            CurrentUserService currentUserService,
            ToolTokenService toolTokenService
    ) {
        super(currentUserService, toolTokenService);
        this.hrMcpCaller = hrMcpCaller;
    }

    @Override
    public AttendanceAgentResult query(AgentInvocationContext context, DateRange dateRange) {
        // MCP Tool 参数仍由后端注入 toolToken，模型和前端不能伪造调用身份。
        ToolResult<Object> result = hrMcpCaller.call("query_attendance", Map.of(
                "toolToken", toolToken(SCOPES),
                // MCP 参数按 JSON 传输，日期统一转成 yyyy-MM-dd，避免 LocalDate 在协议层绑定失败。
                "startDate", dateRange.startDate().toString(),
                "endDate", dateRange.endDate().toString()
        ));
        return new AttendanceAgentResult(result.success(), records(result.data()), lateCount(result.data()), result.traceId());
    }

    private List<AttendanceRecordResponse> records(Object data) {
        if (data instanceof List<?> list && list.stream().allMatch(AttendanceRecordResponse.class::isInstance)) {
            return list.stream().map(AttendanceRecordResponse.class::cast).toList();
        }
        return List.of();
    }

    private int lateCount(Object data) {
        if (!(data instanceof List<?> list)) {
            return 0;
        }
        int count = 0;
        for (Object item : list) {
            if (isLate(item)) {
                count++;
            }
        }
        return count;
    }

    private boolean isLate(Object item) {
        // 兼容单测中的 Map 结果和真实业务中的 AttendanceRecordResponse 结果。
        if (item instanceof AttendanceRecordResponse record) {
            return containsLate(record.status()) || containsLate(record.remark());
        }
        if (item instanceof Map<?, ?> map) {
            return containsLate(map.get("status")) || containsLate(map.get("remark"));
        }
        return false;
    }

    private boolean containsLate(Object value) {
        if (value == null) {
            return false;
        }
        String text = value.toString();
        return text.contains("LATE") || text.contains("迟到");
    }
}
