<template>
    <el-form label-position="top">
        <el-form-item :required="true">
            <template #label>
                <code>id</code>
            </template>
            <el-input :disabled="editing" v-model="newMetadata.id" />
        </el-form-item>
        <el-form-item :required="true">
            <template #label>
                <code>namespace</code>
            </template>
            <el-input :disabled="editing" v-model="newMetadata.namespace" />
        </el-form-item>
        <el-form-item>
            <template #label>
                <div class="d-flex">
                    <code class="flex-grow-1">description</code>
                    <el-button-group size="small">
                        <el-button type="primary" @click="preview = false">
                            Edit
                        </el-button>
                        <el-button type="primary" @click="preview = true">
                            Preview
                        </el-button>
                    </el-button-group>
                </div>
            </template>
            <editor
                v-if="!preview"
                v-model="newMetadata.description"
                :navbar="false"
                :full-height="false"
                :input="true"
                lang="text"
            />
            <markdown v-else :source="newMetadata.description" />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>labels</code>
            </template>
            <div class="d-flex w-100" v-for="(item, index) in newMetadata.labels" :key="index">
                <div class="flex-fill flex-grow-1 w-100 me-2">
                    <el-input
                        :model-value="item[0]"
                        @update:model-value="onKey(index, $event)"
                    />
                </div>
                <div class="flex-fill flex-grow-1 w-100 me-2">
                    <el-input
                        :model-value="item[1]"
                        @update:model-value="onValue(index, $event)"
                    />
                </div>
                <div class="flex-shrink-1">
                    <el-button-group class="d-flex flex-nowrap">
                        <el-button :icon="Plus" @click="addItem" />
                        <el-button
                            :icon="Minus"
                            @click="removeItem(index)"
                            :disabled="index === 0 && newMetadata.labels.length === 1"
                        />
                    </el-button-group>
                </div>
            </div>
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>inputs</code>
            </template>
            <metadata-inputs v-model="newMetadata.inputs" :inputs="newMetadata.inputs" />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>variables</code>
            </template>
            <metadata-variables v-model="newMetadata.variables" :variables="newMetadata.variables" />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>taskDefaults</code>
            </template>
            <editor
                v-if="!preview"
                :v-model-value="newMetadata.taskDefaults"
                :navbar="false"
                :full-height="false"
                :input="true"
                lang="yaml"
            />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>disabled</code>
            </template>
            <div>
                <el-switch active-color="green" v-model="newMetadata.disabled" />
            </div>
        </el-form-item>
    </el-form>
</template>
<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
</script>
<script>
    import {toRaw} from "vue";
    import markdown from "../layout/Markdown.vue";
    import MetadataInputs from "./MetadataInputs.vue";
    import MetadataVariables from "./MetadataVariables.vue";
    import yamlUtils from "../../utils/yamlUtils";
    import Editor from "../inputs/Editor.vue";

    export default {
        emits: ["update:modelValue"],
        created() {
            this.setup();
        },
        components: {
            markdown,
            Editor,
            MetadataInputs,
            MetadataVariables,
        },
        props: {
            metadata: {
                type: Object,
                required: true
            },
            editing: {
                type: Boolean,
                default: true
            }
        },
        data() {
            return {
                newMetadata: {
                    id: "",
                    namespace: "",
                    description: "",
                    labels: [["", undefined]],
                    inputs: [],
                    variables: [["", undefined]],
                    taskDefaults: [],
                    disabled: false
                },
                preview: false
            };
        },
        watch: {
            newMetadata: {
                handler() {
                    this.update();
                },
                deep: true
            }
        },
        methods: {
            setup() {
                this.newMetadata.id = this.metadata.id
                this.newMetadata.namespace = this.metadata.namespace
                this.newMetadata.description = this.metadata?.description || ""
                this.newMetadata.labels = this.metadata.labels ? Object.entries(toRaw(this.metadata.labels)) : [["", undefined]]
                this.newMetadata.inputs = this.metadata.inputs || []
                this.newMetadata.variables = this.metadata.variables ? Object.entries(toRaw(this.metadata.variables)) : [["", undefined]]
                this.newMetadata.taskDefaults = yamlUtils.stringify(this.metadata.taskDefaults) || []
                this.newMetadata.disabled = this.metadata.disabled || false
            },
            addItem() {
                const local = this.newMetadata.labels || [];
                local.push(["", undefined]);

                this.newMetadata.labels = local;
            },
            removeItem(x) {
                const local = this.newMetadata.labels || [];
                local.splice(x, 1);

                this.newMetadata.labels = local;
            },
            onValue(key, value) {
                const local = this.newMetadata.labels || [];
                local[key][1] = value;
                this.newMetadata.labels = local;

            },
            onKey(key, value) {
                const local = this.newMetadata.labels || [];
                local[key][0] = value;
                this.newMetadata.labels = local;
            },
            arrayToObject(array) {
                return array.reduce((obj, [key, value]) => {
                    if (key) {
                        obj[key] = value;
                    }
                    return obj;
                }, {});
            },
            update() {
                this.$emit("update:modelValue", this.cleanMetadata);
            }
        },
        computed: {
            cleanMetadata() {
                const taskDefaults = yamlUtils.parse(this.newMetadata.taskDefaults);
                const metadata = {
                    id: this.newMetadata.id,
                    namespace: this.newMetadata.namespace,
                    description: this.newMetadata.description,
                    labels: this.arrayToObject(this.newMetadata.labels),
                    inputs: this.newMetadata.inputs.filter(e => e.name && e.type),
                    variables: this.arrayToObject(this.newMetadata.variables),
                    taskDefaults: taskDefaults,
                    disabled: this.newMetadata.disabled
                }
                return metadata;
            }
        }
    };
</script>

<style lang="scss" scoped>
    :deep(label) {
        padding-right: 0;
    }
</style>