<template>
    <div class="attempt-header">
        <div class="task-icon d-none d-md-inline-block me-1">
            <task-icon
                :cls="taskType(currentTaskRun)"
                v-if="taskType(currentTaskRun)"
                only-icon
                :icons="icons"
            />
        </div>
        <div
            class="task-id flex-grow-1"
            :id="`attempt-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${currentTaskRun.id}`"
        >
            <el-tooltip :persistent="false" transition="" :hide-after="0">
                <template #content>
                    {{ $t("from") }} :
                    {{ $filters.date(selectedAttempt(currentTaskRun).state.startDate) }}
                    <br>
                    {{ $t("to") }} :
                    {{ $filters.date(selectedAttempt(currentTaskRun).state.endDate) }}
                    <br>
                    <clock/>
                    <strong>{{ $t("duration") }}:</strong>
                    {{ $filters.humanizeDuration(selectedAttempt(currentTaskRun).state.duration) }}
                </template>
                <span>
                    <span class="me-1 fw-bold">{{ currentTaskRun.taskId }}</span>
                    <small v-if="currentTaskRun.value">
                        {{ currentTaskRun.value }}
                    </small>
                </span>
            </el-tooltip>
        </div>

        <div class="task-duration d-none d-md-inline-block">
            <small class="me-1">
                <duration :histories="selectedAttempt(currentTaskRun).state.histories"/>
            </small>
        </div>

        <div class="task-status">
            <status size="small" :status="selectedAttempt(currentTaskRun).state.current"/>
        </div>

        <el-select
            class="d-none d-md-inline-block"
            :model-value="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
            @change="forwardEvent('swapDisplayedAttempt',(currentTaskRun.id, $event))"
            :disabled="!currentTaskRun.attempts || currentTaskRun.attempts?.length <= 1"
        >
            <el-option
                v-for="(_, index) in attempts(currentTaskRun)"
                :key="`attempt-${index}-${currentTaskRun.id}`"
                :value="index"
                :label="`${$t('attempt')} ${index + 1}`"
            />
        </el-select>

        <el-dropdown trigger="click">
            <el-button type="default" class="more-dropdown-button">
                <DotsHorizontal title=""/>
            </el-button>
            <template #dropdown>
                <el-dropdown-menu>
                    <sub-flow-link
                        v-if="isSubflow(currentTaskRun)"
                        component="el-dropdown-item"
                        tab-execution="logs"
                        :execution-id="currentTaskRun.outputs.executionId"
                    />

                    <metrics :task-run="currentTaskRun" :execution="followedExecution"/>

                    <outputs
                        :outputs="currentTaskRun.outputs"
                        :execution="followedExecution"
                    />

                    <restart
                        component="el-dropdown-item"
                        :key="`restart-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        is-replay
                        tooltip-position="left"
                        :execution="followedExecution"
                        :task-run="currentTaskRun"
                        :attempt-index="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="forwardEvent('follow', $event)"
                    />

                    <change-status
                        component="el-dropdown-item"
                        :key="`change-status-${selectedAttemptNumberByTaskRunId[currentTaskRun.id]}-${selectedAttempt(currentTaskRun).state.startDate}`"
                        :execution="followedExecution"
                        :task-run="currentTaskRun"
                        :attempt-index="selectedAttemptNumberByTaskRunId[currentTaskRun.id]"
                        @follow="forwardEvent('follow', $event)"
                    />
                    <task-edit
                        :read-only="true"
                        component="el-dropdown-item"
                        :task-id="currentTaskRun.taskId"
                        :section="SECTIONS.TASKS"
                        :flow-id="followedExecution.flowId"
                        :namespace="followedExecution.namespace"
                        :revision="followedExecution.flowRevision"
                        :flow-source="flow?.source"
                    />
                    <el-dropdown-item
                        :icon="Download"
                        @click="downloadContent(currentTaskRun.id)"
                    >
                        {{ $t("download logs") }}
                    </el-dropdown-item>
                </el-dropdown-menu>
            </template>
        </el-dropdown>

        <el-button
            v-if="!taskRunId && shouldDisplayChevron(currentTaskRun)"
            class="border-0 expand-collapse"
            type="default"
            text
            @click.stop="() => forwardEvent('toggleShowAttempt',(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id])))"
        >
            <ChevronUp
                v-if="shownAttemptsUid.includes(attemptUid(currentTaskRun.id, selectedAttemptNumberByTaskRunId[currentTaskRun.id]))"
            />
            <ChevronDown v-else/>
        </el-button>
    </div>
</template>
<script>
    import Restart from "./Restart.vue";
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
    import Metrics from "./Metrics.vue";
    import Status from "../Status.vue";
    import ChangeStatus from "./ChangeStatus.vue";
    import TaskEdit from "../flows/TaskEdit.vue";
    import SubFlowLink from "../flows/SubFlowLink.vue";
    import DotsHorizontal from "vue-material-design-icons/DotsHorizontal.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";
    import Clock from "vue-material-design-icons/Clock.vue";
    import Outputs from "./Outputs.vue";
    import State from "../../utils/state";
    import FlowUtils from "../../utils/flowUtils";
    import {mapState} from "vuex";
    import {SECTIONS} from "../../utils/constants";
    import Download from "vue-material-design-icons/Download.vue";
    import _groupBy from "lodash/groupBy";
    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";
    import Duration from "../layout/Duration.vue";

    export default {
        components: {
            TaskIcon,
            Outputs,
            Clock,
            ChevronDown,
            DotsHorizontal,
            SubFlowLink,
            TaskEdit,
            ChangeStatus,
            Status,
            Metrics,
            ChevronUp,
            Restart,
            Duration
        },
        mounted() {
            if (this.targetExecutionId) {
                this.followExecution(this.targetExecutionId);
            }
        },
        props: {
            currentTaskRun: {
                type: Object,
                required: true
            },
            followedExecution: {
                type: Object,
                required: true
            },
            flow: {
                type: Object,
                default: null
            },
            forcedAttemptNumber: {
                type: Number,
                default: undefined
            },
            taskRunId: {
                type: String,
                default: undefined,
            },
            selectedAttemptNumberByTaskRunId: {
                type: Object,
                default: () => ({}),
            },
            shownAttemptsUid: {
                type: Array,
                default: () => [],
            },
            logs: {
                type: Array,
                default: () => [],
            },
            filter: {
                type: String,
                default: ""
            }
        },
        computed: {
            Download() {
                return Download
            },
            ...mapState("plugin", ["icons"]),
            SECTIONS() {
                return SECTIONS
            },
            currentTaskRuns() {
                return this.followedExecution?.taskRunList?.filter(tr => this.taskRunId ? tr.id === this.taskRunId : true) ?? [];
            },
            taskRunById() {
                return Object.fromEntries(this.currentTaskRuns.map(taskRun => [taskRun.id, taskRun]));
            },
            logsWithIndexByAttemptUid() {
                const indexedLogs = this.logs
                    .filter(logLine => logLine.message.toLowerCase().includes(this.filter) || this.isSubflow(this.taskRunById[logLine.taskRunId]))
                    .map((logLine, index) => ({...logLine, index}));

                return _groupBy(indexedLogs, indexedLog => this.attemptUid(indexedLog.taskRunId, indexedLog.attemptNumber));
            }
        },
        methods: {
            attempts(taskRun) {
                if (this.followedExecution.state.current === State.RUNNING || this.forcedAttemptNumber === undefined) {
                    return taskRun.attempts ?? [{state: taskRun.state}];
                }

                return taskRun.attempts ? [taskRun.attempts[this.forcedAttemptNumber]] : [];
            },
            isSubflow(taskRun) {
                return taskRun.outputs?.executionId;
            },
            selectedAttempt(taskRun) {
                return this.attempts(taskRun)[this.selectedAttemptNumberByTaskRunId[taskRun.id] ?? 0];
            },
            taskType(taskRun) {
                const task = FlowUtils.findTaskById(this.flow, taskRun.taskId);
                const parentTaskRunId = taskRun.parentTaskRunId;
                if (task === undefined && parentTaskRunId) {
                    return this.taskType(this.taskRunById[parentTaskRunId])
                }
                return task ? task.type : undefined;
            },
            downloadContent(currentTaskRunId) {
                const params = this.params
                this.$store.dispatch("execution/downloadLogs", {
                    executionId: this.followedExecution.id,
                    params: {...params, taskRunId: currentTaskRunId}
                }).then((response) => {
                    const url = window.URL.createObjectURL(new Blob([response]));
                    const link = document.createElement("a");
                    link.href = url;
                    link.setAttribute("download", this.downloadName(currentTaskRunId));
                    document.body.appendChild(link);
                    link.click();
                });
            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            attemptUid(taskRunId, attemptNumber) {
                return `${taskRunId}-${attemptNumber}`
            },
            shouldDisplayChevron(taskRun) {
                return this.shouldDisplayProgressBar(taskRun) || this.shouldDisplayLogs(taskRun.id)
            },
            shouldDisplayProgressBar(taskRun) {
                return this.taskType(taskRun) === "io.kestra.core.tasks.flows.ForEachItem"
            },
            shouldDisplayLogs(taskRunId) {
                return this.logsWithIndexByAttemptUid[this.attemptUid(taskRunId, this.selectedAttemptNumberByTaskRunId[taskRunId])]
            }
        }
    }
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .attempt-header {
        display: flex;
        gap: calc(var(--spacer) / 2);

        > * {
            display: flex;
            align-items: center;
        }

        .el-select {
            width: 8rem;
        }

        .attempt-number {
            background: var(--bs-gray-400);
            padding: .375rem .75rem;
            white-space: nowrap;

            html.dark & {
                color: var(--bs-gray-600);
            }
        }

        .task-id, .task-duration {
            padding: .375rem 0;
        }

        .task-id {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;

            span span {
                color: var(--bs-tertiary-color);

                html:not(.dark) & {
                    color: $black;
                }
            }
        }

        .task-icon {
            width: 36px;
            padding: 6px;
            border-radius: $border-radius-lg;
        }

        small {
            color: var(--bs-gray-600);
            font-family: var(--bs-font-monospace);
            font-size: var(--font-size-xs)
        }

        .task-duration small {
            white-space: nowrap;

            color: var(--bs-gray-800);
        }

        .more-dropdown-button {
            padding: .5rem;
            margin-bottom: .5rem;
            border: 1px solid rgba($white, .05);

            &:not(:hover) {
                background: rgba($white, .10);
            }
        }

        .expand-collapse {
            background-color: transparent !important;
        }
    }
</style>