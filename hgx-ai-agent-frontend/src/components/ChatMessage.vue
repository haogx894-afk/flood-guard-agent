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
      <div class="bubble-content" :class="{ rich: shouldRenderRichText }">
        <template v-if="shouldRenderRichText">
          <template v-for="block in richBlocks" :key="block.id">
            <h4 v-if="block.type === 'heading'" class="rich-heading">
              <template v-for="segment in block.segments" :key="segment.id">
                <span :class="{ 'rich-strong': segment.strong }">{{ segment.text }}</span>
              </template>
            </h4>

            <ul v-else-if="block.type === 'list'" class="rich-list">
              <li v-for="item in block.items" :key="item.id" class="rich-list-item">
                <span class="rich-list-dot" aria-hidden="true"></span>
                <span v-if="item.label" class="rich-list-label">{{ item.label }}</span>
                <span class="rich-list-text">
                  <template v-for="segment in item.segments" :key="segment.id">
                    <span :class="{ 'rich-strong': segment.strong }">{{ segment.text }}</span>
                  </template>
                </span>
              </li>
            </ul>

            <p v-else class="rich-paragraph">
              <template v-for="segment in block.segments" :key="segment.id">
                <span :class="{ 'rich-strong': segment.strong }">{{ segment.text }}</span>
              </template>
            </p>
          </template>
          <span v-if="message.isTyping" class="typing-caret" aria-hidden="true"></span>
        </template>

        <template v-else>
          {{ normalizedContent }}<span v-if="message.isTyping" class="typing-caret" aria-hidden="true"></span>
        </template>
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
const normalizedContent = computed(() => String(props.message.content || '').replace(/\\n/g, '\n'));
const shouldRenderRichText = computed(() => props.message.role === 'assistant' && normalizedContent.value.length > 0);
const richBlocks = computed(() => parseAssistantContent(normalizedContent.value));
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

function parseAssistantContent(content) {
  const lines = content
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);

  const blocks = [];
  let index = 0;
  let blockId = 0;

  while (index < lines.length) {
    const line = lines[index];
    const headingText = getHeadingText(line);
    if (headingText) {
      blocks.push({
        id: `heading-${blockId++}`,
        type: 'heading',
        segments: parseInlineSegments(headingText, `heading-${blockId}`),
      });
      index++;
      continue;
    }

    if (isListLine(line)) {
      const items = [];
      while (index < lines.length && isListLine(lines[index])) {
        items.push(parseListItem(stripListMarker(lines[index]), `list-${blockId}-${items.length}`));
        index++;
      }
      blocks.push({
        id: `list-${blockId++}`,
        type: 'list',
        items,
      });
      continue;
    }

    blocks.push({
      id: `paragraph-${blockId++}`,
      type: 'paragraph',
      segments: parseInlineSegments(cleanInlineText(line), `paragraph-${blockId}`),
    });
    index++;
  }

  return blocks;
}

function getHeadingText(line) {
  const markdownHeading = line.match(/^#{1,6}\s+(.+)$/);
  if (markdownHeading) {
    return cleanInlineText(markdownHeading[1]);
  }

  const bracketHeading = line.match(/^【(.+?)】[:：]?\s*(.*)$/);
  if (bracketHeading) {
    return cleanInlineText(`${bracketHeading[1]}${bracketHeading[2] ? `：${bracketHeading[2]}` : ''}`);
  }

  return '';
}

function isListLine(line) {
  return /^[-*]\s+/.test(line) || /^\d+[.、]\s+/.test(line);
}

function stripListMarker(line) {
  return line.replace(/^[-*]\s+/, '').replace(/^\d+[.、]\s+/, '');
}

function parseListItem(rawText, idPrefix) {
  const text = cleanInlineText(rawText);
  const labelMatch = text.match(/^\*\*(.+?)\*\*[:：]?\s*(.*)$/);

  if (labelMatch) {
    return {
      id: idPrefix,
      label: cleanInlineText(labelMatch[1]),
      segments: parseInlineSegments(labelMatch[2] || '', idPrefix),
    };
  }

  const plainLabelMatch = text.match(/^([^：:]{2,12})[:：]\s*(.+)$/);
  if (plainLabelMatch) {
    return {
      id: idPrefix,
      label: cleanInlineText(plainLabelMatch[1]),
      segments: parseInlineSegments(plainLabelMatch[2], idPrefix),
    };
  }

  return {
    id: idPrefix,
    label: '',
    segments: parseInlineSegments(text, idPrefix),
  };
}

function parseInlineSegments(text, idPrefix) {
  const segments = [];
  const normalizedText = cleanInlineText(text);
  const pattern = /\*\*(.+?)\*\*/g;
  let cursor = 0;
  let match;
  let index = 0;

  while ((match = pattern.exec(normalizedText)) !== null) {
    if (match.index > cursor) {
      segments.push({
        id: `${idPrefix}-text-${index++}`,
        text: normalizedText.slice(cursor, match.index),
        strong: false,
      });
    }
    segments.push({
      id: `${idPrefix}-strong-${index++}`,
      text: match[1],
      strong: true,
    });
    cursor = match.index + match[0].length;
  }

  if (cursor < normalizedText.length) {
    segments.push({
      id: `${idPrefix}-text-${index++}`,
      text: normalizedText.slice(cursor),
      strong: false,
    });
  }

  return segments.length > 0
    ? segments
    : [
        {
          id: `${idPrefix}-empty`,
          text: '',
          strong: false,
        },
      ];
}

function cleanInlineText(text) {
  return String(text || '')
    .replace(/\r/g, '')
    .replace(/`([^`]+)`/g, '$1')
    .trim();
}
</script>
