<template>
  <div class="message-row" :class="rowClasses">
    <div class="avatar" aria-hidden="true">
      {{ message.role === 'assistant' ? 'AI' : '我' }}
    </div>
    <article class="bubble">
      <div class="bubble-header">
        <span>{{ title }}</span>
        <span v-if="isStepMessage" class="step-badge">执行步骤</span>
      </div>
      <div class="bubble-content">
        {{ message.content }}<span v-if="message.isTyping" class="typing-caret" aria-hidden="true"></span>
      </div>
    </article>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  message: {
    type: Object,
    required: true,
  },
});

const isStepMessage = computed(() => props.message.content?.startsWith('Step '));
const title = computed(() => {
  if (props.message.role === 'user') {
    return '用户';
  }
  return isStepMessage.value ? '智能体执行' : '防汛管家';
});
const rowClasses = computed(() => ({
  [props.message.role]: true,
  'is-step': isStepMessage.value,
  'is-typing': props.message.isTyping,
}));
</script>
