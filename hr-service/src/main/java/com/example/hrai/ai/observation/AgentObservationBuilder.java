package com.example.hrai.ai.observation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 单次请求级 Agent 观测构建器。
 *
 * <p>该类不由 Spring 管理，每个请求应新建一个实例。</p>
 */
public final class AgentObservationBuilder {

    private final String requestId = UUID.randomUUID().toString();
    private final long startedAtNanos = System.nanoTime();
    private final List<MutableAgentStep> orderedSteps = new ArrayList<>();
    private final Map<String, MutableAgentStep> stepsById = new LinkedHashMap<>();
    private AgentDecisionObservation decision;

    /**
     * 开始记录一个 Agent 步骤。
     */
    public AgentStepHandle startAgent(String agentName, String summary) {
        Objects.requireNonNull(agentName, "agentName must not be null");
        AgentStepHandle handle = new AgentStepHandle(UUID.randomUUID().toString());
        MutableAgentStep step = new MutableAgentStep(agentName, summary, System.nanoTime());
        orderedSteps.add(step);
        stepsById.put(handle.id(), step);
        return handle;
    }

    /**
     * 完成 Agent 步骤并记录最终状态和面向员工的摘要。
     */
    public void finishAgent(AgentStepHandle handle, AgentObservationStatus status, String summary) {
        Objects.requireNonNull(status, "status must not be null");
        MutableAgentStep step = requireStep(handle);
        if (step.finished) {
            throw new IllegalStateException("Agent 步骤已完成，不能重复完成");
        }
        step.status = status;
        step.summary = summary;
        step.durationMs = elapsedMillis(step.startedAtNanos);
        step.finished = true;
    }

    /**
     * 按调用顺序追加工具观测。
     */
    public void addToolCall(AgentStepHandle handle, AgentToolObservation tool) {
        Objects.requireNonNull(tool, "tool must not be null");
        MutableAgentStep step = requireStep(handle);
        if (step.finished) {
            throw new IllegalStateException("Agent 步骤已完成，不能继续追加工具调用");
        }
        step.toolCalls.add(tool);
    }

    /**
     * 记录最终决策。
     */
    public void decision(AgentDecisionObservation decision) {
        this.decision = decision;
    }

    /**
     * 构建与后续修改隔离的不可变快照。
     */
    public AgentObservationSnapshot build() {
        List<AgentObservationStep> steps = orderedSteps.stream()
                .map(this::snapshot)
                .toList();
        List<String> summarySteps = steps.stream()
                .map(AgentObservationStep::summary)
                .filter(this::hasText)
                .toList();
        return new AgentObservationSnapshot(
                requestId,
                deriveStatus(steps),
                elapsedMillis(startedAtNanos),
                summarySteps,
                steps,
                decision
        );
    }

    private AgentObservationStep snapshot(MutableAgentStep step) {
        long durationMs = step.durationMs == null
                ? elapsedMillis(step.startedAtNanos)
                : step.durationMs;
        AgentObservationStatus status = step.status == null
                ? AgentObservationStatus.PARTIAL
                : step.status;
        return new AgentObservationStep(
                step.agentName,
                status,
                durationMs,
                step.summary,
                step.toolCalls.stream()
                        .map(this::copyTool)
                        .toList()
        );
    }

    private AgentToolObservation copyTool(AgentToolObservation tool) {
        return new AgentToolObservation(
                tool.toolName(),
                tool.status(),
                tool.durationMs(),
                tool.traceId(),
                tool.inputSummary(),
                tool.resultSummary(),
                tool.evidenceSource(),
                tool.errorCode()
        );
    }

    private AgentObservationStatus deriveStatus(List<AgentObservationStep> steps) {
        if (steps.isEmpty()) {
            return AgentObservationStatus.SUCCESS;
        }
        boolean hasSuccess = false;
        boolean hasFailed = false;
        for (AgentObservationStep step : steps) {
            AgentObservationStatus stepStatus = step.status();
            if (stepStatus == AgentObservationStatus.PARTIAL) {
                return AgentObservationStatus.PARTIAL;
            }
            hasSuccess |= stepStatus == AgentObservationStatus.SUCCESS;
            hasFailed |= stepStatus == AgentObservationStatus.FAILED;
            for (AgentToolObservation tool : step.toolCalls()) {
                if (tool.status() == AgentObservationStatus.PARTIAL) {
                    return AgentObservationStatus.PARTIAL;
                }
                hasSuccess |= tool.status() == AgentObservationStatus.SUCCESS;
                hasFailed |= tool.status() == AgentObservationStatus.FAILED;
            }
        }
        if (hasSuccess && hasFailed) {
            return AgentObservationStatus.PARTIAL;
        }
        return hasFailed ? AgentObservationStatus.FAILED : AgentObservationStatus.SUCCESS;
    }

    private MutableAgentStep requireStep(AgentStepHandle handle) {
        if (handle == null || !stepsById.containsKey(handle.id())) {
            throw new IllegalArgumentException("未知的 AgentStepHandle");
        }
        return stepsById.get(handle.id());
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 对外暴露的步骤句柄，仅用于关联 Builder 内部步骤。
     *
     * @param id Builder 内部生成的步骤 ID，外部只应原样传回 Builder 使用
     */
    public record AgentStepHandle(String id) {
    }

    private static final class MutableAgentStep {

        private final String agentName;
        private final long startedAtNanos;
        private final List<AgentToolObservation> toolCalls = new ArrayList<>();
        private AgentObservationStatus status;
        private String summary;
        private Long durationMs;
        private boolean finished;

        private MutableAgentStep(String agentName, String summary, long startedAtNanos) {
            this.agentName = agentName;
            this.summary = summary;
            this.startedAtNanos = startedAtNanos;
        }
    }
}
